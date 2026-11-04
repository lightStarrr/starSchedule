package com.star.schedule.feature.update.data

import com.star.schedule.feature.update.domain.CurrentInstallation
import com.star.schedule.feature.update.domain.UpdateCacheStore
import com.star.schedule.feature.update.domain.UpdateDownloadFailure
import com.star.schedule.feature.update.domain.UpdateDownloadRepository
import com.star.schedule.feature.update.domain.UpdateDownloadState
import com.star.schedule.feature.update.domain.UpdateDownloadStatus
import com.star.schedule.feature.update.domain.UpdateDownloader
import com.star.schedule.feature.update.domain.UpdateInstallPreparation
import com.star.schedule.feature.update.domain.UpdatePackageValidation
import com.star.schedule.feature.update.domain.UpdatePackageVerifier
import com.star.schedule.feature.update.domain.UpdateRelease
import com.star.schedule.feature.update.domain.VerifiedUpdatePackage
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class DefaultUpdateDownloadRepository(
    private val downloader: UpdateDownloader,
    private val verifier: UpdatePackageVerifier,
    private val recordStore: UpdateCacheStore,
    private val updatesDirectory: File?,
    private val installed: CurrentInstallation,
    private val expectedPackageName: String,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UpdateDownloadRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    private val _state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    private var generation = 0L
    private var observers = 0
    private var pollJob: Job? = null
    private var verifyJob: Job? = null
    private var restoreJob: Deferred<Unit>? = null
    private var prepareJob: Deferred<UpdateInstallPreparation>? = null
    private val mutationMutex = Mutex()

    override fun observe(): Flow<UpdateDownloadState> = flow {
        val first = synchronized(this@DefaultUpdateDownloadRepository) {
            observers += 1
            observers == 1
        }
        if (first) startPolling()
        try {
            _state.collect { emit(it) }
        } finally {
            val last = synchronized(this@DefaultUpdateDownloadRepository) {
                observers -= 1
                observers == 0
            }
            if (last) stopPolling()
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch(ioDispatcher) {
            ensureRestored()
            val verifying = _state.value as? UpdateDownloadState.Verifying
            if (verifying != null && verifyJob?.isActive != true) {
                val gen = lock.withLock { generation }
                beginVerify(verifying.target, gen)
            }
            while (isActive) {
                pollOnce()
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        verifyJob?.cancel()
        verifyJob = null
    }

    override suspend fun restore() = ensureRestored()

    private suspend fun ensureRestored() {
        val job = lock.withLock {
            restoreJob ?: scope.async(ioDispatcher) { restoreInternal() }
                .also { restoreJob = it }
        }
        job.await()
    }

    override suspend fun start(target: UpdateRelease) {
        ensureRestored()
        val op = lock.withLock {
            if (isOccupied(_state.value)) return
            generation += 1
            _state.value = UpdateDownloadState.Starting(target)
            val gen = generation
            scope.async(ioDispatcher) {
                mutationMutex.withLock {
                    val (next, createdId) = startInternal(target)
                    val applied = publish(gen, next)
                    if (!applied && createdId != null) {
                        rollbackCreated(target, createdId)
                    }
                }
            }
        }
        op.await()
    }

    private suspend fun startInternal(
        target: UpdateRelease,
    ): Pair<UpdateDownloadState, Long?> {
        val cleanupFailure = clearOwnedArtifacts()
        if (cleanupFailure != null) {
            return UpdateDownloadState.Failed(target, cleanupFailure) to null
        }
        return enqueue(target)
    }

    private suspend fun rollbackCreated(target: UpdateRelease, downloadId: Long) {
        val cancelled = try {
            downloader.cancel(downloadId)
            true
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
        if (!cancelled) return
        fileFor(target.identity)?.delete()
        try {
            recordStore.write(RECORD_KEY, "")
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
        }
    }

    override suspend fun cancel() {
        val gen = lock.withLock {
            generation += 1
            generation
        }
        verifyJob?.cancel()
        verifyJob = null
        val failure = mutationMutex.withLock {
            withContext(ioDispatcher) { clearOwnedArtifacts() }
        }
        publish(
            gen,
            failure?.let { UpdateDownloadState.Failed(currentTarget(), it) }
                ?: UpdateDownloadState.Idle,
        )
    }

    override suspend fun prepareInstallation(): UpdateInstallPreparation {
        val deferred = lock.withLock {
            val active = prepareJob
            if (active != null && active.isActive) return@withLock active
            scope.async(ioDispatcher) { prepareInternal() }.also { prepareJob = it }
        }
        return deferred.await()
    }

    private suspend fun prepareInternal(): UpdateInstallPreparation {
        val gen = lock.withLock {
            generation += 1
            generation
        }
        val ready = _state.value as? UpdateDownloadState.Ready
            ?: return UpdateInstallPreparation.Failure(UpdateDownloadFailure.Unknown)
        publish(gen, UpdateDownloadState.Verifying(ready.target))
        val failure = try {
            verify(ready.target)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            UpdateDownloadFailure.Unknown
        }
        val stillCurrent = lock.withLock { gen == generation }
        if (!stillCurrent) {
            return UpdateInstallPreparation.Failure(UpdateDownloadFailure.Unknown)
        }
        return if (failure == null) {
            publish(gen, UpdateDownloadState.Ready(ready.target))
            UpdateInstallPreparation.Ready(
                VerifiedUpdatePackage(
                    identity = ready.target.identity,
                    localPath = fileFor(ready.target.identity)?.canonicalPath.orEmpty(),
                ),
            )
        } else {
            publish(gen, UpdateDownloadState.Failed(ready.target, failure))
            UpdateInstallPreparation.Failure(failure)
        }
    }

    suspend fun cleanupAfterInstall() {
        ensureRestored()
        mutationMutex.withLock {
            val result = withContext(ioDispatcher) { readRecord() }
            if (result is RecordResult.Valid) {
                val target = result.record.toRelease()
                val installedDone = installed.versionCode > target.versionCode ||
                    (installed.versionName == target.versionName &&
                        installed.commitSha == target.commitSha)
                if (installedDone) {
                    val gen = lock.withLock {
                        generation += 1
                        generation
                    }
                    val failure = withContext(ioDispatcher) { clearOwnedArtifacts() }
                    publish(
                        gen,
                        failure?.let { UpdateDownloadState.Failed(target, it) }
                            ?: UpdateDownloadState.Idle,
                    )
                }
            }
        }
    }

    private suspend fun restoreInternal() {
        val gen = lock.withLock { generation }
        when (val result = readRecord()) {
            RecordResult.Absent -> {
                publish(gen, UpdateDownloadState.Idle)
                return
            }

            RecordResult.Failure -> {
                publish(
                    gen,
                    UpdateDownloadState.Failed(null, UpdateDownloadFailure.Storage),
                )
                return
            }

            RecordResult.Invalid -> {
                try {
                    recordStore.write(RECORD_KEY, "")
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                }
                publish(
                    gen,
                    UpdateDownloadState.Failed(null, UpdateDownloadFailure.Storage),
                )
                return
            }

            is RecordResult.Valid -> restoreRecord(gen, result.record)
        }
    }

    private suspend fun restoreRecord(gen: Long, record: DownloadRecord) {
        val target = record.toRelease()
        val query = try {
            downloader.query(record.downloadId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            publish(
                gen,
                UpdateDownloadState.Failed(target, UpdateDownloadFailure.Unknown),
            )
            return
        }
        val next = when {
            query == null || !queryOwns(query, record) ->
                UpdateDownloadState.Failed(target, UpdateDownloadFailure.MissingTask)

            query.status == UpdateDownloadStatus.Successful ->
                UpdateDownloadState.Verifying(target)

            query.status == UpdateDownloadStatus.Failed ->
                UpdateDownloadState.Failed(
                    target,
                    query.failure ?: UpdateDownloadFailure.Network,
                )

            else -> UpdateDownloadState.Downloading(
                target,
                record.downloadId,
                query.bytesDownloaded,
                query.totalBytes.takeIf { it > 0 },
                query.waiting,
            )
        }
        publish(gen, next)
        if (next is UpdateDownloadState.Verifying) {
            beginVerify(target, gen)
        }
    }

    private suspend fun pollOnce() {
        val state = _state.value
        if (state !is UpdateDownloadState.Downloading) return
        val gen = lock.withLock { generation }
        val query = try {
            downloader.query(state.downloadId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return
        }
        when {
            query == null -> publish(
                gen,
                UpdateDownloadState.Failed(state.target, UpdateDownloadFailure.MissingTask),
            )

            !queryOwns(query, state.target) -> publish(
                gen,
                UpdateDownloadState.Failed(state.target, UpdateDownloadFailure.MissingTask),
            )

            query.status == UpdateDownloadStatus.Successful ->
                beginVerify(state.target, gen)

            query.status == UpdateDownloadStatus.Failed -> publish(
                gen,
                UpdateDownloadState.Failed(
                    state.target,
                    query.failure ?: UpdateDownloadFailure.Network,
                ),
            )

            else -> publish(
                gen,
                state.copy(
                    bytesDownloaded = query.bytesDownloaded,
                    totalBytes = query.totalBytes.takeIf { it > 0 },
                    waiting = query.waiting,
                ),
            )
        }
    }

    private suspend fun beginVerify(target: UpdateRelease, expectedGen: Long) {
        val gen = lock.withLock {
            if (expectedGen != generation) return@withLock null
            if (verifyJob?.isActive == true) return@withLock null
            _state.value = UpdateDownloadState.Verifying(target)
            generation
        } ?: return
        verifyJob = scope.async(ioDispatcher) {
            val failure = try {
                verify(target)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                UpdateDownloadFailure.Unknown
            }
            publish(
                gen,
                if (failure == null) {
                    UpdateDownloadState.Ready(target)
                } else {
                    UpdateDownloadState.Failed(target, failure)
                },
            )
        }
    }

    private suspend fun verify(target: UpdateRelease): UpdateDownloadFailure? {
        val file = fileFor(target.identity) ?: return UpdateDownloadFailure.MissingFile
        if (!file.isFile) return UpdateDownloadFailure.MissingFile
        val sizeBefore = file.length()
        val modifiedBefore = file.lastModified()
        if (sizeBefore != target.apkSize) return UpdateDownloadFailure.SizeMismatch
        val sha256 = try {
            streamSha256(file)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return UpdateDownloadFailure.Storage
        }
        if (file.length() != sizeBefore || file.lastModified() != modifiedBefore) {
            return UpdateDownloadFailure.HashMismatch
        }
        val facts = try {
            verifier.packageFacts(file.absolutePath)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return UpdateDownloadFailure.PackageMismatch
        val installedFacts = try {
            verifier.installedPackageFacts()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        } ?: return UpdateDownloadFailure.SignatureMismatch
        if (file.length() != sizeBefore || file.lastModified() != modifiedBefore) {
            return UpdateDownloadFailure.HashMismatch
        }
        return UpdatePackageValidation.validate(
            fileSize = sizeBefore,
            fileSha256 = sha256,
            facts = facts,
            target = target,
            installedVersionCode = installedFacts.versionCode,
            expectedPackageName = expectedPackageName,
            expectedSigners = installedFacts.signerSha256,
        )
    }

    private suspend fun streamSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                if (!currentCoroutineContext().isActive) throw CancellationException()
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun enqueue(
        target: UpdateRelease,
    ): Pair<UpdateDownloadState, Long?> {
        if (!DownloadRecord.of(target, 1).isValid()) {
            return UpdateDownloadState.Failed(
                target,
                UpdateDownloadFailure.Unavailable,
            ) to null
        }
        if (!downloader.isAvailable()) {
            return UpdateDownloadState.Failed(
                target,
                UpdateDownloadFailure.Unavailable,
            ) to null
        }
        val dir = updatesDirectory
            ?: return UpdateDownloadState.Failed(
                target,
                UpdateDownloadFailure.Unavailable,
            ) to null
        if (!dir.exists() && !dir.mkdirs()) {
            return UpdateDownloadState.Failed(
                target,
                UpdateDownloadFailure.Storage,
            ) to null
        }
        if (dir.usableSpace < target.apkSize) {
            return UpdateDownloadState.Failed(
                target,
                UpdateDownloadFailure.InsufficientSpace,
            ) to null
        }
        val destination = fileFor(target.identity)
            ?: return UpdateDownloadState.Failed(
                target,
                UpdateDownloadFailure.Storage,
            ) to null
        if (destination.exists() && !destination.delete()) {
            return UpdateDownloadState.Failed(
                target,
                UpdateDownloadFailure.Storage,
            ) to null
        }
        val downloadId = try {
            downloader.enqueue(target, destination.absolutePath)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return UpdateDownloadState.Failed(
                target,
                UpdateDownloadFailure.Network,
            ) to null
        }
        val state = try {
            recordStore.write(
                RECORD_KEY,
                json.encodeToString(DownloadRecord.of(target, downloadId)),
            )
            UpdateDownloadState.Downloading(target, downloadId, 0, null, false)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            runCatching { downloader.cancel(downloadId) }
            return UpdateDownloadState.Failed(
                target,
                UpdateDownloadFailure.Storage,
            ) to null
        }
        return state to downloadId
    }

    private suspend fun clearOwnedArtifacts(): UpdateDownloadFailure? {
        val result = try {
            readRecord()
        } catch (error: CancellationException) {
            throw error
        }
        val record = when (result) {
            RecordResult.Absent -> return null
            RecordResult.Failure -> return UpdateDownloadFailure.Storage
            RecordResult.Invalid -> {
                return try {
                    recordStore.write(RECORD_KEY, "")
                    null
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    UpdateDownloadFailure.Storage
                }
            }

            is RecordResult.Valid -> result.record
        }
        val queryError = try {
            if (claimTask(record)) {
                try {
                    downloader.cancel(record.downloadId)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    return UpdateDownloadFailure.Storage
                }
            }
            false
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            true
        }
        if (queryError) return UpdateDownloadFailure.Storage
        val file = fileFor(record.toRelease().identity)
        if (file != null && file.exists() && !file.delete()) {
            return UpdateDownloadFailure.Storage
        }
        return try {
            recordStore.write(RECORD_KEY, "")
            null
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            UpdateDownloadFailure.Storage
        }
    }

    private suspend fun claimTask(record: DownloadRecord): Boolean {
        val query = downloader.query(record.downloadId) ?: return false
        return queryOwns(query, record)
    }

    private fun queryOwns(
        query: com.star.schedule.feature.update.domain.UpdateDownloadQuery,
        record: DownloadRecord,
    ): Boolean = queryOwns(query, record.toRelease())

    private fun queryOwns(
        query: com.star.schedule.feature.update.domain.UpdateDownloadQuery,
        target: UpdateRelease,
    ): Boolean {
        val localPath = query.localPath ?: return false
        val claimed = runCatching { File(localPath).canonicalFile }.getOrNull() ?: return false
        val expected = fileFor(target.identity) ?: return false
        return claimed == expected
    }

    private sealed interface RecordResult {
        data object Absent : RecordResult

        data object Invalid : RecordResult

        data object Failure : RecordResult

        data class Valid(val record: DownloadRecord) : RecordResult
    }

    private suspend fun readRecord(): RecordResult {
        val raw = try {
            recordStore.read(RECORD_KEY)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return RecordResult.Failure
        }
        if (raw.isNullOrBlank()) return RecordResult.Absent
        val record = runCatching {
            json.decodeFromString<DownloadRecord>(raw)
        }.getOrNull()?.takeIf { it.isValid() }
        return record?.let { RecordResult.Valid(it) } ?: RecordResult.Invalid
    }

    private fun currentTarget(): UpdateRelease? = when (val state = _state.value) {
        is UpdateDownloadState.Starting -> state.target
        is UpdateDownloadState.Downloading -> state.target
        is UpdateDownloadState.Verifying -> state.target
        is UpdateDownloadState.Ready -> state.target
        is UpdateDownloadState.Failed -> state.target
        UpdateDownloadState.Idle -> null
    }

    private fun isOccupied(state: UpdateDownloadState): Boolean =
        state is UpdateDownloadState.Starting ||
            state is UpdateDownloadState.Downloading ||
            state is UpdateDownloadState.Verifying ||
            state is UpdateDownloadState.Ready

    private suspend fun publish(gen: Long, next: UpdateDownloadState): Boolean =
        lock.withLock {
            if (gen == generation) {
                _state.value = next
                true
            } else {
                false
            }
        }

    private fun fileFor(identity: String): File? {
        val dir = updatesDirectory ?: return null
        val canonicalDir = runCatching { dir.canonicalFile }.getOrNull() ?: return null
        val file = runCatching {
            File(canonicalDir, "${sha256Hex(identity)}.apk").canonicalFile
        }.getOrNull() ?: return null
        if (file.parentFile != canonicalDir) return null
        return file
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    @Serializable
    private data class DownloadRecord(
        val schemaVersion: Int = 1,
        val releaseId: Long,
        val metadataAssetId: Long,
        val apkAssetId: Long,
        val tag: String,
        val channel: String,
        val versionName: String,
        val versionCode: Long,
        val commitSha: String,
        val apkName: String,
        val apkSize: Long,
        val apkSha256: String,
        val apkDownloadUrl: String,
        val htmlUrl: String,
        val changelogAdded: List<String> = emptyList(),
        val changelogChanged: List<String> = emptyList(),
        val changelogFixed: List<String> = emptyList(),
        val downloadId: Long,
    ) {
        fun isValid(): Boolean =
            schemaVersion == 1 &&
                releaseId > 0 && metadataAssetId > 0 && apkAssetId > 0 && downloadId > 0 &&
                (channel == "stable" || channel == "snapshot") &&
                versionCode in 1..MAX_VERSION_CODE &&
                FULL_SHA.matches(commitSha) &&
                SHA256.matches(apkSha256) &&
                apkSize > 0 &&
                apkName.endsWith(".apk") && apkName != ".apk" &&
                apkName.none { it == '/' || it == '\\' || it.code < 0x20 || it.code == 0x7f } &&
                when (channel) {
                    "stable" -> STABLE_VERSION.matches(versionName) && tag == "v$versionName"
                    "snapshot" -> tag == "snapshot" &&
                        SNAPSHOT_VERSION.matchEntire(versionName)?.groupValues?.get(1)
                            ?.let { commitSha.startsWith(it) } == true
                    else -> false
                } &&
                isReleaseHtmlUrl(htmlUrl, tag) &&
                isReleaseDownloadUrl(apkDownloadUrl, tag, apkName)

        fun toRelease() = UpdateRelease(
            releaseId = releaseId,
            metadataAssetId = metadataAssetId,
            apkAssetId = apkAssetId,
            tag = tag,
            channel = if (channel == "snapshot") {
                com.star.schedule.feature.update.domain.UpdateChannel.Snapshot
            } else {
                com.star.schedule.feature.update.domain.UpdateChannel.Stable
            },
            versionName = versionName,
            versionCode = versionCode,
            commitSha = commitSha,
            apkName = apkName,
            apkSize = apkSize,
            apkSha256 = apkSha256,
            apkDownloadUrl = apkDownloadUrl,
            htmlUrl = htmlUrl,
            changelog = com.star.schedule.feature.update.domain.UpdateChangelog(
                added = changelogAdded,
                changed = changelogChanged,
                fixed = changelogFixed,
            ),
        )

        companion object {
            fun of(target: UpdateRelease, downloadId: Long) = DownloadRecord(
                releaseId = target.releaseId,
                metadataAssetId = target.metadataAssetId,
                apkAssetId = target.apkAssetId,
                tag = target.tag,
                channel = if (target.channel ==
                    com.star.schedule.feature.update.domain.UpdateChannel.Snapshot
                ) {
                    "snapshot"
                } else {
                    "stable"
                },
                versionName = target.versionName,
                versionCode = target.versionCode,
                commitSha = target.commitSha,
                apkName = target.apkName,
                apkSize = target.apkSize,
                apkSha256 = target.apkSha256,
                apkDownloadUrl = target.apkDownloadUrl,
                htmlUrl = target.htmlUrl,
                changelogAdded = target.changelog.added,
                changelogChanged = target.changelog.changed,
                changelogFixed = target.changelog.fixed,
                downloadId = downloadId,
            )
        }
    }

    private companion object {
        const val RECORD_KEY = "download.record"
        const val POLL_INTERVAL_MILLIS = 500L
        const val MAX_VERSION_CODE = 2_100_000_000L
        val FULL_SHA = Regex("[0-9a-f]{40}")
        val SHA256 = Regex("[0-9a-f]{64}")
        val STABLE_VERSION = Regex("\\d+\\.\\d+\\.\\d+")
        val SNAPSHOT_VERSION = Regex("\\d+\\.\\d+\\.\\d+-snapshot\\.([0-9a-f]+)")

        fun isReleaseHtmlUrl(raw: String, tag: String): Boolean {
            val url = raw.toHttpUrlOrNull() ?: return false
            return url.isHttps && url.host == "github.com" && url.port == 443 &&
                url.encodedUsername.isEmpty() && url.encodedPassword.isEmpty() &&
                url.encodedQuery == null && url.encodedFragment == null &&
                url.pathSegments == listOf("lightStarrr", "starSchedule", "releases", "tag", tag)
        }

        fun isReleaseDownloadUrl(raw: String, tag: String, expectedName: String): Boolean {
            val url = raw.toHttpUrlOrNull() ?: return false
            if (!url.isHttps || url.host != "github.com" || url.port != 443) return false
            if (url.encodedUsername.isNotEmpty() || url.encodedPassword.isNotEmpty()) {
                return false
            }
            if (url.encodedQuery != null || url.encodedFragment != null) return false
            val segments = url.pathSegments
            return segments.size == 6 &&
                segments[0] == "lightStarrr" &&
                segments[1] == "starSchedule" &&
                segments[2] == "releases" &&
                segments[3] == "download" &&
                segments[4] == tag &&
                segments[5] == expectedName
        }
    }
}
