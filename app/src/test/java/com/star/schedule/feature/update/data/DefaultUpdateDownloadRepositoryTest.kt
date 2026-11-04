package com.star.schedule.feature.update.data

import com.star.schedule.feature.update.domain.CurrentInstallation
import com.star.schedule.feature.update.domain.UpdateCacheStore
import com.star.schedule.feature.update.domain.UpdateChannel
import com.star.schedule.feature.update.domain.UpdateChangelog
import com.star.schedule.feature.update.domain.UpdateDownloadFailure
import com.star.schedule.feature.update.domain.UpdateDownloadQuery
import com.star.schedule.feature.update.domain.UpdateDownloadState
import com.star.schedule.feature.update.domain.UpdateDownloadStatus
import com.star.schedule.feature.update.domain.UpdateDownloader
import com.star.schedule.feature.update.domain.UpdateInstallPreparation
import com.star.schedule.feature.update.domain.UpdatePackageFacts
import com.star.schedule.feature.update.domain.UpdatePackageVerifier
import com.star.schedule.feature.update.domain.UpdateRelease
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultUpdateDownloadRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val signers = setOf("f".repeat(64))

    private class FakeCacheStore : UpdateCacheStore {
        val data = mutableMapOf<String, String>()
        var failReads = false
        var failWrites = false

        override suspend fun read(key: String): String? {
            if (failReads) error("read")
            return data[key]
        }

        override suspend fun write(key: String, value: String) {
            if (failWrites) error("write")
            data[key] = value
        }
    }

    private class FakeDownloader : UpdateDownloader {
        var available = true
        var nextId = 100L
        var queryException: Exception? = null
        var queryGate: CompletableDeferred<Unit>? = null
        var queryCalls = 0
        var slowOnce: CompletableDeferred<UpdateDownloadQuery?>? = null
        var enqueueGate: CompletableDeferred<Unit>? = null
        val enqueued = mutableListOf<Long>()
        val cancelled = mutableListOf<Long>()
        val queries = mutableMapOf<Long, UpdateDownloadQuery?>()

        override fun isAvailable() = available

        override suspend fun enqueue(
            target: UpdateRelease,
            destinationPath: String,
        ): Long {
            enqueueGate?.await()
            val id = nextId++
            enqueued += id
            queries[id] = UpdateDownloadQuery(
                UpdateDownloadStatus.Running,
                0,
                target.apkSize,
                false,
                localPath = destinationPath,
            )
            return id
        }

        override suspend fun query(downloadId: Long): UpdateDownloadQuery? {
            queryCalls++
            slowOnce?.let {
                slowOnce = null
                return it.await()
            }
            queryGate?.await()
            queryException?.let { throw it }
            return queries[downloadId]
        }

        override suspend fun cancel(downloadId: Long) {
            cancelled += downloadId
        }
    }

    private class FakeVerifier : UpdatePackageVerifier {
        var facts: UpdatePackageFacts? = null
        var installedFacts: UpdatePackageFacts? = null
        var gate: CompletableDeferred<Unit>? = null
        var exception: Exception? = null
        var calls = 0

        override suspend fun packageFacts(filePath: String): UpdatePackageFacts? {
            calls++
            gate?.await()
            exception?.let { throw it }
            return facts
        }

        override suspend fun installedPackageFacts() = installedFacts
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun fileName(identity: String) = "${sha256(identity.toByteArray())}.apk"

    private fun target(
        versionCode: Long = 130,
        apkSize: Long = 16,
        sha: String = "a".repeat(64),
        channel: UpdateChannel = UpdateChannel.Stable,
    ) = UpdateRelease(
        releaseId = 1,
        metadataAssetId = 2,
        apkAssetId = 3,
        tag = "v1.3.0",
        channel = channel,
        versionName = "1.3.0",
        versionCode = versionCode,
        commitSha = "b".repeat(40),
        apkName = "app.apk",
        apkSize = apkSize,
        apkSha256 = sha,
        apkDownloadUrl = "https://github.com/lightStarrr/starSchedule/releases/download/v1.3.0/app.apk",
        htmlUrl = "https://github.com/lightStarrr/starSchedule/releases/tag/v1.3.0",
        changelog = UpdateChangelog(added = listOf("x")),
    )

    private fun goodFacts(target: UpdateRelease) = UpdatePackageFacts(
        "com.star.schedule",
        target.versionName,
        target.versionCode,
        signers,
    )

    private fun installedFacts(code: Long = 120) =
        UpdatePackageFacts("com.star.schedule", "1.2.0", code, signers)

    private fun TestScope.harness(
        dir: File,
        installedCode: Long = 120,
    ): Triple<DefaultUpdateDownloadRepository, FakeDownloader, FakeVerifier> {
        val downloader = FakeDownloader()
        val verifier = FakeVerifier()
        verifier.installedFacts = installedFacts(installedCode)
        val repo = DefaultUpdateDownloadRepository(
            downloader = downloader,
            verifier = verifier,
            recordStore = store,
            updatesDirectory = dir,
            installed = CurrentInstallation(
                "1.2.0",
                installedCode,
                "a".repeat(40),
                UpdateChannel.Stable,
            ),
            expectedPackageName = "com.star.schedule",
            scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        return Triple(repo, downloader, verifier)
    }

    private val store = FakeCacheStore()

    private fun writeApk(dir: File, target: UpdateRelease, bytes: ByteArray): File {
        dir.mkdirs()
        val file = File(dir, fileName(target.identity))
        file.writeBytes(bytes)
        return file
    }

    private fun seedCompleted(
        dir: File,
        downloader: FakeDownloader,
        recordTarget: UpdateRelease,
        downloadId: Long = 55L,
        localMatch: Boolean = true,
    ) {
        val repo = DefaultUpdateDownloadRepository::class.java
        val record = """
            {"schemaVersion":1,"releaseId":${recordTarget.releaseId},
            "metadataAssetId":${recordTarget.metadataAssetId},"apkAssetId":${recordTarget.apkAssetId},
            "tag":"${recordTarget.tag}","channel":"${if (recordTarget.channel == UpdateChannel.Snapshot) "snapshot" else "stable"}",
            "versionName":"${recordTarget.versionName}","versionCode":${recordTarget.versionCode},
            "commitSha":"${recordTarget.commitSha}","apkName":"${recordTarget.apkName}",
            "apkSize":${recordTarget.apkSize},"apkSha256":"${recordTarget.apkSha256}",
            "apkDownloadUrl":"${recordTarget.apkDownloadUrl}","htmlUrl":"${recordTarget.htmlUrl}",
            "changelogAdded":[],"changelogChanged":[],"changelogFixed":[],"downloadId":$downloadId}
        """.trimIndent().replace("\n", "")
        store.data["download.record"] = record
        val file = File(dir, fileName(recordTarget.identity))
        downloader.queries[downloadId] = UpdateDownloadQuery(
            status = UpdateDownloadStatus.Successful,
            bytesDownloaded = recordTarget.apkSize,
            totalBytes = recordTarget.apkSize,
            waiting = false,
            localPath = if (localMatch) file.absolutePath else "/data/other/app.apk",
        )
    }

    @Test
    fun `first start completes without deadlock and enqueues once`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, _) = harness(dir)
        withTimeout(10_000) { repo.start(target()) }
        advanceUntilIdle()
        assertEquals(1, downloader.enqueued.size)
        val state = repo.observe().take(1).toList().single()
        advanceUntilIdle()
        assertTrue(state is UpdateDownloadState.Downloading)
    }

    @Test
    fun `start restores the old record first and an occupied start does not replace`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, verifier) = harness(dir)
        val bytes = "real-apk-bytes".toByteArray()
        val t = target(apkSize = bytes.size.toLong(), sha = sha256(bytes))
        verifier.facts = goodFacts(t)
        verifier.gate = CompletableDeferred()
        writeApk(dir, t, bytes)
        seedCompleted(dir, downloader, t)
        repo.restore()
        advanceUntilIdle()
        val queued = async { repo.start(target(versionCode = 140)) }
        val restored = repo.observe().take(1).toList().single()
        assertTrue("$restored", restored is UpdateDownloadState.Verifying)
        advanceUntilIdle()
        assertTrue(downloader.enqueued.isEmpty())
        queued.await()
    }

    @Test
    fun `failed retry clears only its own task`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, _) = harness(dir)
        val t = target()
        store.data["download.record"] = recordJson(t, 55)
        downloader.queries[55] = UpdateDownloadQuery(
            UpdateDownloadStatus.Failed, 0, 0, false,
            localPath = File(dir, fileName(t.identity)).absolutePath,
        )
        repo.restore()
        advanceUntilIdle()
        val failed = repo.observe().take(1).toList().single()
        assertTrue(failed is UpdateDownloadState.Failed)
        repo.start(t)
        advanceUntilIdle()
        assertEquals(listOf(55L), downloader.cancelled)
        assertEquals(1, downloader.enqueued.size)
    }

    @Test
    fun `multiple observers share one poll and polling stops on unsubscribe`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, _) = harness(dir)
        val t = target()
        store.data["download.record"] = recordJson(t, 55)
        downloader.queries[55] = UpdateDownloadQuery(
            UpdateDownloadStatus.Running, 5, 100, false,
            localPath = File(dir, fileName(t.identity)).absolutePath,
        )
        val states = mutableListOf<UpdateDownloadState>()
        val job1 = launch { repo.observe().collect { states += it } }
        val job2 = launch { repo.observe().collect {} }
        runCurrent()
        advanceTimeBy(600)
        runCurrent()
        val callsWhileTwo = downloader.queryCalls
        assertTrue(callsWhileTwo > 0)
        job2.cancel()
        runCurrent()
        advanceTimeBy(600)
        runCurrent()
        val callsAfterOne = downloader.queryCalls
        assertTrue(callsAfterOne > callsWhileTwo)
        job1.cancel()
        runCurrent()
        val callsAfterAll = downloader.queryCalls
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(callsAfterAll, downloader.queryCalls)
        assertTrue(states.isNotEmpty())
    }

    @Test
    fun `completed download verifies real bytes and becomes ready`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, verifier) = harness(dir)
        val bytes = "real-apk-bytes".toByteArray()
        val t = target(apkSize = bytes.size.toLong(), sha = sha256(bytes))
        verifier.facts = goodFacts(t)
        writeApk(dir, t, bytes)
        seedCompleted(dir, downloader, t)
        repo.restore()
        advanceUntilIdle()
        val state = repo.observe().take(1).toList().single()
        assertTrue(state is UpdateDownloadState.Ready)
        assertEquals(1, verifier.calls)
    }

    @Test
    fun `observer sees verifying before the verifier gate releases`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, verifier) = harness(dir)
        val bytes = "real-apk-bytes".toByteArray()
        val t = target(apkSize = bytes.size.toLong(), sha = sha256(bytes))
        verifier.facts = goodFacts(t)
        verifier.gate = CompletableDeferred()
        writeApk(dir, t, bytes)
        seedCompleted(dir, downloader, t)
        repo.restore()
        advanceUntilIdle()
        val seen = repo.observe().take(1).toList().single()
        assertTrue("$seen", seen is UpdateDownloadState.Verifying)
        verifier.gate!!.complete(Unit)
        assertTrue(
            repo.observe().first { it is UpdateDownloadState.Ready }
                is UpdateDownloadState.Ready,
        )
    }

    @Test
    fun `late poll result after cancel does not overwrite idle`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, _) = harness(dir)
        val t = target()
        store.data["download.record"] = recordJson(t, 55)
        downloader.queries[55] = UpdateDownloadQuery(
            UpdateDownloadStatus.Running, 1, 100, false,
            localPath = File(dir, fileName(t.identity)).absolutePath,
        )
        downloader.slowOnce = CompletableDeferred()
        val job = launch { repo.observe().collect {} }
        runCurrent()
        repo.cancel()
        val held = downloader.slowOnce
        downloader.slowOnce = null
        held?.complete(
            UpdateDownloadQuery(
                UpdateDownloadStatus.Successful, 100, 100, false,
                localPath = File(dir, fileName(t.identity)).absolutePath,
            ),
        )
        advanceUntilIdle()
        assertTrue(repo.observe().take(1).toList().single() is UpdateDownloadState.Idle)
        job.cancel()
    }

    @Test
    fun `resubscribing re-verifies a verifying download`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, verifier) = harness(dir)
        val bytes = "real-apk-bytes".toByteArray()
        val t = target(apkSize = bytes.size.toLong(), sha = sha256(bytes))
        verifier.facts = goodFacts(t)
        verifier.gate = CompletableDeferred()
        writeApk(dir, t, bytes)
        seedCompleted(dir, downloader, t)
        val job = launch { repo.observe().collect {} }
        runCurrent()
        job.cancel()
        runCurrent()
        verifier.gate!!.complete(Unit)
        runCurrent()
        val job2 = launch { repo.observe().collect {} }
        runCurrent()
        assertTrue(repo.observe().take(1).toList().single() is UpdateDownloadState.Ready)
        job2.cancel()
    }

    @Test
    fun `prepare re-verifies and is single-flight`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, verifier) = harness(dir)
        val bytes = "real-apk-bytes".toByteArray()
        val t = target(apkSize = bytes.size.toLong(), sha = sha256(bytes))
        verifier.facts = goodFacts(t)
        writeApk(dir, t, bytes)
        seedCompleted(dir, downloader, t)
        repo.restore()
        advanceUntilIdle()
        verifier.gate = CompletableDeferred()
        val first = async { repo.prepareInstallation() }
        val second = async { repo.prepareInstallation() }
        advanceUntilIdle()
        verifier.gate!!.complete(Unit)
        advanceUntilIdle()
        val a = first.await()
        val b = second.await()
        assertTrue(a is UpdateInstallPreparation.Ready)
        assertTrue(b is UpdateInstallPreparation.Ready)
        assertEquals(2, verifier.calls)
    }

    @Test
    fun `a prepare invalidated by cancel returns failure not an artifact`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, verifier) = harness(dir)
        val bytes = "real-apk-bytes".toByteArray()
        val t = target(apkSize = bytes.size.toLong(), sha = sha256(bytes))
        verifier.facts = goodFacts(t)
        writeApk(dir, t, bytes)
        seedCompleted(dir, downloader, t)
        repo.restore()
        advanceUntilIdle()
        assertTrue(repo.observe().take(1).toList().single() is UpdateDownloadState.Ready)

        verifier.gate = CompletableDeferred()
        val prepare = async { repo.prepareInstallation() }
        runCurrent()
        repo.cancel()
        verifier.gate!!.complete(Unit)
        advanceUntilIdle()
        assertTrue(prepare.await() is UpdateInstallPreparation.Failure)
    }

    @Test
    fun `unknown total size and waiting surface in state`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, _) = harness(dir)
        val t = target()
        store.data["download.record"] = recordJson(t, 55)
        downloader.queries[55] = UpdateDownloadQuery(
            UpdateDownloadStatus.Paused, 10, -1, true,
            localPath = File(dir, fileName(t.identity)).absolutePath,
        )
        repo.restore()
        runCurrent()
        val downloading = repo.observe().take(1).toList().single()
        assertTrue(downloading is UpdateDownloadState.Downloading)
        assertNull((downloading as UpdateDownloadState.Downloading).totalBytes)
        assertTrue(downloading.waiting)
    }

    @Test
    fun `query exception keeps the task record instead of dropping it`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, _) = harness(dir)
        val t = target()
        store.data["download.record"] = recordJson(t, 55)
        downloader.queryException = RuntimeException("dm down")
        repo.restore()
        advanceUntilIdle()
        val state = repo.observe().take(1).toList().single()
        assertTrue(state !is UpdateDownloadState.Idle)
        assertTrue(store.data["download.record"]!!.isNotBlank())
    }

    @Test
    fun `corrupt record fails explicitly`() = runTest {
        val dir = tmp.newFolder()
        val (repo, _, _) = harness(dir)
        store.data["download.record"] = """{"schemaVersion":0,"downloadId":77}"""
        repo.restore()
        advanceUntilIdle()
        val state = repo.observe().take(1).toList().single()
        assertTrue(state is UpdateDownloadState.Failed)
        assertEquals(
            UpdateDownloadFailure.Storage,
            (state as UpdateDownloadState.Failed).reason,
        )
    }

    @Test
    fun `foreign download id is never cancelled`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, _) = harness(dir)
        val t = target()
        store.data["download.record"] = recordJson(t, 55)
        downloader.queries[55] = UpdateDownloadQuery(
            UpdateDownloadStatus.Running, 1, 100, false,
            localPath = "/data/data/com.star.schedule/files/other.apk",
        )
        repo.restore()
        advanceUntilIdle()
        repo.cancel()
        advanceUntilIdle()
        assertTrue(downloader.cancelled.isEmpty())
    }

    @Test
    fun `record write failure does not leave an orphan task`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, _) = harness(dir)
        store.failWrites = true
        repo.start(target())
        advanceUntilIdle()
        assertEquals(1, downloader.enqueued.size)
        assertEquals(downloader.enqueued, downloader.cancelled)
        val state = repo.observe().take(1).toList().single()
        assertTrue(state is UpdateDownloadState.Failed)
        assertEquals(
            UpdateDownloadFailure.Storage,
            (state as UpdateDownloadState.Failed).reason,
        )
    }

    @Test
    fun `missing task and missing file report different reasons`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, verifier) = harness(dir)
        val bytes = "real-apk-bytes".toByteArray()
        val t = target(apkSize = bytes.size.toLong(), sha = sha256(bytes))
        verifier.facts = goodFacts(t)
        store.data["download.record"] = recordJson(t, 55)
        downloader.queries.remove(55)
        repo.restore()
        advanceUntilIdle()
        val missing = repo.observe().take(1).toList().single()
        assertEquals(
            UpdateDownloadFailure.MissingTask,
            (missing as UpdateDownloadState.Failed).reason,
        )

        val dir2 = tmp.newFolder()
        store.data.clear()
        val (repo2, downloader2, verifier2) = harness(dir2)
        verifier2.facts = goodFacts(t)
        seedCompleted(dir2, downloader2, t)
        repo2.restore()
        advanceUntilIdle()
        val missingFile = repo2.observe().take(1).toList().single()
        assertEquals(
            UpdateDownloadFailure.MissingFile,
            (missingFile as UpdateDownloadState.Failed).reason,
        )
    }

    @Test
    fun `cleanup only runs after a matching install`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, _) = harness(dir, installedCode = 120)
        val bytes = "real-apk-bytes".toByteArray()
        val t = target(apkSize = bytes.size.toLong(), sha = sha256(bytes))
        val file = writeApk(dir, t, bytes)
        seedCompleted(dir, downloader, t)
        repo.cleanupAfterInstall()
        advanceUntilIdle()
        assertTrue(store.data["download.record"]!!.isNotBlank())

        val (repo2, downloader2, _) = harness(dir, installedCode = 140)
        seedCompleted(dir, downloader2, t)
        repo2.cleanupAfterInstall()
        advanceUntilIdle()
        assertEquals("", store.data["download.record"])
        assertEquals(listOf(55L), downloader2.cancelled)
    }

    @Test
    fun `a query without local path is never owned nor cancelled`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, _) = harness(dir)
        val t = target()
        store.data["download.record"] = recordJson(t, 55)
        downloader.queries[55] = UpdateDownloadQuery(
            UpdateDownloadStatus.Running, 1, 100, false,
            localPath = null,
        )
        repo.restore()
        advanceUntilIdle()
        val state = repo.observe().take(1).toList().single()
        assertEquals(
            UpdateDownloadFailure.MissingTask,
            (state as UpdateDownloadState.Failed).reason,
        )
        repo.cancel()
        advanceUntilIdle()
        assertTrue(downloader.cancelled.isEmpty())
    }

    @Test
    fun `persisted releases reject unsupported schemas channels and external links`() = runTest {
        val valid = recordJson(target(), 55)
        val corruptions = listOf(
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            valid.replace("\"channel\":\"stable\"", "\"channel\":\"other\""),
            valid.replace("\"versionName\":\"1.3.0\"", "\"versionName\":\"other\""),
            valid.replace(
                "https://github.com/lightStarrr/starSchedule/releases/tag/v1.3.0",
                "https://example.invalid/release",
            ),
        )
        for (corrupt in corruptions) {
            store.data["download.record"] = corrupt
            val (repo, downloader, _) = harness(tmp.newFolder())
            repo.restore()
            runCurrent()
            val state = repo.observe().take(1).toList().single()
            assertTrue(state is UpdateDownloadState.Failed)
            assertEquals(
                UpdateDownloadFailure.Storage,
                (state as UpdateDownloadState.Failed).reason,
            )
            assertTrue(downloader.cancelled.isEmpty())
        }
    }

    @Test
    fun `a start superseded by cancel leaves no orphan task or record`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, _) = harness(dir)
        downloader.enqueueGate = CompletableDeferred()
        val started = async { repo.start(target()) }
        runCurrent()
        val cancelled = async { repo.cancel() }
        runCurrent()
        downloader.enqueueGate!!.complete(Unit)
        started.await()
        cancelled.await()
        advanceUntilIdle()
        assertTrue(repo.observe().take(1).toList().single() is UpdateDownloadState.Idle)
        assertTrue(store.data["download.record"].isNullOrBlank())
        assertEquals(downloader.enqueued, downloader.cancelled)
    }

    @Test
    fun `cancel keeps the record when the store cannot be read`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, _) = harness(dir)
        store.data["download.record"] = recordJson(target(), 55)
        downloader.queries[55] = UpdateDownloadQuery(
            UpdateDownloadStatus.Running, 1, 100, false,
            localPath = File(dir, fileName(target().identity)).absolutePath,
        )
        repo.restore()
        advanceUntilIdle()
        store.failReads = true
        store.failWrites = true
        repo.cancel()
        advanceUntilIdle()
        val state = repo.observe().take(1).toList().single()
        assertTrue(state is UpdateDownloadState.Failed)
        store.failReads = false
        store.failWrites = false
        assertTrue(store.data["download.record"]!!.isNotBlank())
        assertTrue(downloader.cancelled.isEmpty())
    }

    @Test
    fun `a file swapped during verification is rejected`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, verifier) = harness(dir)
        val bytes = "real-apk-bytes".toByteArray()
        val t = target(apkSize = bytes.size.toLong(), sha = sha256(bytes))
        verifier.facts = goodFacts(t)
        verifier.gate = CompletableDeferred()
        writeApk(dir, t, bytes)
        seedCompleted(dir, downloader, t)
        repo.restore()
        advanceUntilIdle()
        File(dir, fileName(t.identity)).writeBytes("tampered-apk!!".toByteArray())
        verifier.gate!!.complete(Unit)
        advanceUntilIdle()
        val state = repo.observe().take(1).toList().single()
        assertTrue(state is UpdateDownloadState.Failed)
    }

    @Test
    fun `cleanup for a not-installed target does not invalidate verification`() = runTest {
        val dir = tmp.newFolder()
        val (repo, downloader, verifier) = harness(dir, installedCode = 120)
        val bytes = "real-apk-bytes".toByteArray()
        val t = target(apkSize = bytes.size.toLong(), sha = sha256(bytes))
        verifier.facts = goodFacts(t)
        verifier.gate = CompletableDeferred()
        writeApk(dir, t, bytes)
        seedCompleted(dir, downloader, t)
        repo.restore()
        advanceUntilIdle()
        repo.cleanupAfterInstall()
        verifier.gate!!.complete(Unit)
        advanceUntilIdle()
        val state = repo.observe().take(1).toList().single()
        assertTrue(state is UpdateDownloadState.Ready)
    }

    private fun recordJson(t: UpdateRelease, id: Long): String = """
        {"schemaVersion":1,"releaseId":${t.releaseId},"metadataAssetId":${t.metadataAssetId},
        "apkAssetId":${t.apkAssetId},"tag":"${t.tag}",
        "channel":"${if (t.channel == UpdateChannel.Snapshot) "snapshot" else "stable"}",
        "versionName":"${t.versionName}","versionCode":${t.versionCode},
        "commitSha":"${t.commitSha}","apkName":"${t.apkName}","apkSize":${t.apkSize},
        "apkSha256":"${t.apkSha256}","apkDownloadUrl":"${t.apkDownloadUrl}",
        "htmlUrl":"${t.htmlUrl}","changelogAdded":[],"changelogChanged":[],
        "changelogFixed":[],"downloadId":$id}
    """.trimIndent().replace("\n", "")
}
