package com.star.schedule.feature.update.data

import com.star.schedule.feature.update.domain.UpdateChannel
import com.star.schedule.feature.update.domain.UpdateChangelog
import com.star.schedule.feature.update.domain.UpdateFetchOutcome
import com.star.schedule.feature.update.domain.UpdateIssue
import com.star.schedule.feature.update.domain.UpdateIssueReason
import com.star.schedule.feature.update.domain.UpdateRelease
import com.star.schedule.feature.update.domain.UpdateReleaseSource
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

class GitHubReleaseSource(
    private val httpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val snapshotRetryDelayMillis: Long = 1_000,
) : UpdateReleaseSource {

    private val json = Json { ignoreUnknownKeys = true }
    private val rateLimitMutex = Mutex()

    private var rateLimitedUntilMillis = 0L

    override suspend fun fetchRelease(channel: UpdateChannel): UpdateFetchOutcome =
        withContext(ioDispatcher) {
            val retryAt = currentRateLimit()
            if (clock() < retryAt) {
                return@withContext UpdateFetchOutcome.Failure(
                    issue = UpdateIssue(channel, UpdateIssueReason.RateLimited, retryAt),
                )
            }
            try {
                UpdateFetchOutcome.Success(
                    release = buildRelease(channel, requestRelease(channel)),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: SourceFailure) {
                UpdateFetchOutcome.Failure(
                    issue = UpdateIssue(channel, error.reason, error.retryAtEpochMillis),
                )
            } catch (_: IOException) {
                UpdateFetchOutcome.Failure(
                    issue = UpdateIssue(channel, UpdateIssueReason.Network),
                )
            } catch (_: Exception) {
                UpdateFetchOutcome.Failure(
                    issue = UpdateIssue(channel, UpdateIssueReason.Network),
                )
            }
        }

    private suspend fun requestRelease(channel: UpdateChannel): String {
        val url = when (channel) {
            UpdateChannel.Stable -> "$API_ROOT/releases/latest"
            UpdateChannel.Snapshot -> "$API_ROOT/releases/tags/snapshot"
        }
        var attempt = 0
        while (true) {
            attempt++
            val decision = execute(apiRequest(url)).use { response ->
                when {
                    response.code == 200 -> ReleaseResult.Payload(
                        readBodyCapped(response, API_BODY_LIMIT),
                    )

                    response.code == 404 || response.code == 410 ->
                        if (channel == UpdateChannel.Snapshot && attempt == 1) {
                            ReleaseResult.Retry
                        } else {
                            throw SourceFailure(UpdateIssueReason.ReleaseUnavailable)
                        }

                    response.code == 403 || response.code == 429 ->
                        throw rateLimitedOrUnavailable(response)

                    else -> throw SourceFailure(UpdateIssueReason.ReleaseUnavailable)
                }
            }
            when (decision) {
                is ReleaseResult.Payload -> return decision.body
                ReleaseResult.Retry -> delay(snapshotRetryDelayMillis)
            }
        }
    }

    private suspend fun requestMetadata(url: String): String =
        execute(apiRequest(url)).use { response ->
        when {
            response.code == 200 -> readBodyCapped(response, METADATA_BODY_LIMIT)

            response.code == 403 || response.code == 429 ->
                throw rateLimitedOrUnavailable(response)

            else -> throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        }
    }

    private suspend fun buildRelease(
        channel: UpdateChannel,
        releaseBody: String,
    ): UpdateRelease {
        val root = parseObject(releaseBody)
        val metadataUrl = metadataDownloadUrl(root, findMetadataAssetId(root))

        val retryAt = currentRateLimit()
        if (clock() < retryAt) {
            throw SourceFailure(UpdateIssueReason.RateLimited, retryAt)
        }
        return assembleRelease(channel, root, requestMetadata(metadataUrl))
    }

    private fun assembleRelease(
        channel: UpdateChannel,
        root: JsonObject,
        metadataBody: String,
    ): UpdateRelease {
        val releaseId = requiredId(root, "id")
        val tag = requiredString(root, "tag_name")
        val draft = requiredBool(root, "draft")
        val prerelease = requiredBool(root, "prerelease")
        val htmlUrl = requiredString(root, "html_url")
        if (!isReleaseHtmlUrl(htmlUrl, tag)) {
            throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        }
        when (channel) {
            UpdateChannel.Stable ->
                if (draft || prerelease) {
                    throw SourceFailure(UpdateIssueReason.ReleaseUnavailable)
                }

            UpdateChannel.Snapshot ->
                if (draft || !prerelease || tag != SNAPSHOT_TAG) {
                    throw SourceFailure(UpdateIssueReason.ReleaseUnavailable)
                }
        }
        val assets = (root["assets"] as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?: throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        val metadata = validateMetadata(channel, parseObject(metadataBody))
        if (tag != expectedTag(channel, metadata)) {
            throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        }
        val metadataAssetId = findMetadataAssetId(root)
        if (!isReleaseDownloadUrl(
                requiredString(
                    assets.singleOrNull { requiredId(it, "id") == metadataAssetId }
                        ?: throw SourceFailure(UpdateIssueReason.InvalidMetadata),
                    "browser_download_url",
                ),
                tag,
                METADATA_ASSET_NAME,
            )
        ) {
            throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        }
        val apkAssets = assets.filter { string(it, "name") == metadata.apkName }
        if (apkAssets.size != 1) {
            throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        }
        val apkAsset = apkAssets.single()
        val apkAssetId = requiredId(apkAsset, "id")
        if (number(apkAsset, "size") != metadata.apkSize) {
            throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        }
        val apkUrl = requiredString(apkAsset, "browser_download_url")
        if (!isReleaseDownloadUrl(apkUrl, tag, metadata.apkName)) {
            throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        }
        return UpdateRelease(
            releaseId = releaseId,
            metadataAssetId = metadataAssetId,
            apkAssetId = apkAssetId,
            tag = tag,
            channel = channel,
            versionName = metadata.versionName,
            versionCode = metadata.versionCode,
            commitSha = metadata.commitSha,
            apkName = metadata.apkName,
            apkSize = metadata.apkSize,
            apkSha256 = metadata.apkSha256,
            apkDownloadUrl = apkUrl,
            htmlUrl = htmlUrl,
            changelog = metadata.changelog,
        )
    }

    private fun findMetadataAssetId(root: JsonObject): Long {
        val assets = (root["assets"] as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?: throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        val matches = assets.filter { string(it, "name") == METADATA_ASSET_NAME }
        if (matches.size != 1) {
            throw SourceFailure(UpdateIssueReason.MetadataUnavailable)
        }
        return requiredId(matches.single(), "id")
    }

    private fun metadataDownloadUrl(root: JsonObject, metadataAssetId: Long): String {
        val assets = (root["assets"] as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?: throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        val asset = assets.singleOrNull { requiredId(it, "id") == metadataAssetId }
            ?: throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        val url = requiredString(asset, "browser_download_url")
        if (!isReleaseDownloadUrl(url, requiredString(root, "tag_name"), METADATA_ASSET_NAME)) {
            throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        }
        return url
    }

    private fun validateMetadata(channel: UpdateChannel, root: JsonObject): MetadataContent {
        if (number(root, "schemaVersion") != 1L) {
            throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        }
        val channelName = when (channel) {
            UpdateChannel.Stable -> "stable"
            UpdateChannel.Snapshot -> "snapshot"
        }
        if (string(root, "channel") != channelName) {
            throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        }
        val versionName = requiredString(root, "versionName")
        val commitSha = requiredString(root, "commitSha")
        if (!FULL_SHA.matches(commitSha)) {
            throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        }
        when (channel) {
            UpdateChannel.Stable ->
                if (!STABLE_VERSION.matches(versionName)) {
                    throw SourceFailure(UpdateIssueReason.InvalidMetadata)
                }

            UpdateChannel.Snapshot -> {
                val match = SNAPSHOT_VERSION.matchEntire(versionName)
                    ?: throw SourceFailure(UpdateIssueReason.InvalidMetadata)
                if (!commitSha.startsWith(match.groupValues[1])) {
                    throw SourceFailure(UpdateIssueReason.InvalidMetadata)
                }
            }
        }
        val versionCode = number(root, "versionCode")
        if (versionCode == null || versionCode !in 1..MAX_VERSION_CODE) {
            throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        }
        val generatedAt = requiredString(root, "generatedAt")
        val timestampValid = runCatching { Instant.parse(generatedAt) }.isSuccess ||
            runCatching { OffsetDateTime.parse(generatedAt) }.isSuccess
        if (!timestampValid) {
            throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        }
        val apk = root["apk"] as? JsonObject
            ?: throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        val apkName = requiredString(apk, "name")
        if (!isSafeApkName(apkName)) {
            throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        }
        val apkSize = number(apk, "size")
        if (apkSize == null || apkSize <= 0) {
            throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        }
        val apkSha256 = requiredString(apk, "sha256")
        if (!SHA256.matches(apkSha256)) {
            throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        }
        val changelogObject = root["changelog"] as? JsonObject
            ?: throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        if (changelogObject.keys != CHANGELOG_KEYS) {
            throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        }
        fun group(name: String): List<String> {
            val entries = changelogObject[name] as? JsonArray
                ?: throw SourceFailure(UpdateIssueReason.InvalidMetadata)
            return entries.map {
                (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content
                    ?: throw SourceFailure(UpdateIssueReason.InvalidMetadata)
            }
        }
        return MetadataContent(
            versionName = versionName,
            versionCode = versionCode,
            commitSha = commitSha,
            apkName = apkName,
            apkSize = apkSize,
            apkSha256 = apkSha256,
            changelog = UpdateChangelog(
                added = group("added"),
                changed = group("changed"),
                fixed = group("fixed"),
            ),
        )
    }

    private fun expectedTag(channel: UpdateChannel, metadata: MetadataContent): String =
        when (channel) {
            UpdateChannel.Stable -> "v${metadata.versionName}"
            UpdateChannel.Snapshot -> SNAPSHOT_TAG
        }

    private fun isSafeApkName(name: String): Boolean =
        name.endsWith(".apk") && name != ".apk" &&
            name.all { it.code >= 0x20 && it.code != 0x7f } &&
            '/' !in name && '\\' !in name

    private fun isRepoUrl(url: HttpUrl): Boolean =
        url.isHttps &&
            url.host == RELEASE_DOWNLOAD_HOST &&
            url.port == 443 &&
            url.encodedUsername.isEmpty() &&
            url.encodedPassword.isEmpty() &&
            url.encodedQuery == null &&
            url.encodedFragment == null

    private fun isReleaseDownloadUrl(raw: String, tag: String, expectedName: String): Boolean {
        val url = raw.toHttpUrlOrNull() ?: return false
        if (!isRepoUrl(url)) return false
        val segments = url.pathSegments
        return segments.size == 6 &&
            segments[0] == REPO_OWNER &&
            segments[1] == REPO_NAME &&
            segments[2] == "releases" &&
            segments[3] == "download" &&
            segments[4] == tag &&
            segments[5] == expectedName
    }

    private fun isReleaseHtmlUrl(raw: String, tag: String): Boolean {
        val url = raw.toHttpUrlOrNull() ?: return false
        if (!isRepoUrl(url)) return false
        val segments = url.pathSegments
        return segments.size == 5 &&
            segments[0] == REPO_OWNER &&
            segments[1] == REPO_NAME &&
            segments[2] == "releases" &&
            segments[3] == "tag" &&
            segments[4] == tag
    }

    private fun rateLimitRetryAt(response: Response): Long? {
        val raw = run {
            val retryAfter = response.header("Retry-After")
            if (retryAfter != null) {
                val seconds = retryAfter.trim().toLongOrNull()
                if (seconds != null && seconds >= 0) {
                    return@run clock() + seconds.coerceAtMost(86_400) * 1000
                }
                val parsed = runCatching {
                    ZonedDateTime.parse(retryAfter.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                }.getOrNull()?.toInstant()?.toEpochMilli()
                if (parsed != null) return@run parsed
                if (response.code == 429) {
                    return@run clock() + DEFAULT_RATE_LIMIT_WINDOW_MILLIS
                }
                return@run null
            }
            val remaining = response.header("X-RateLimit-Remaining")?.trim()?.toIntOrNull()
            if (remaining == 0) {
                return@run response.header("X-RateLimit-Reset")?.trim()?.toLongOrNull()
                    ?.let { it * 1000 }
                    ?: (clock() + DEFAULT_RATE_LIMIT_WINDOW_MILLIS)
            }
            if (response.code == 429) {
                return@run clock() + DEFAULT_RATE_LIMIT_WINDOW_MILLIS
            }
            null
        } ?: return null
        return raw.coerceIn(clock() + MIN_RETRY_BACKOFF_MILLIS, Long.MAX_VALUE)
    }

    private suspend fun rateLimitedOrUnavailable(response: Response): SourceFailure {
        val retryAt = rateLimitRetryAt(response)
        return if (retryAt != null) {
            recordRateLimit(retryAt)
            SourceFailure(UpdateIssueReason.RateLimited, retryAt)
        } else {
            SourceFailure(UpdateIssueReason.ReleaseUnavailable)
        }
    }

    private suspend fun currentRateLimit(): Long =
        rateLimitMutex.withLock { rateLimitedUntilMillis }

    private suspend fun recordRateLimit(retryAt: Long) {
        rateLimitMutex.withLock {
            rateLimitedUntilMillis = maxOf(rateLimitedUntilMillis, retryAt)
        }
    }

    private fun apiRequest(url: String): Request =
        Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()

    private suspend fun execute(request: Request): Response {
        val retryAt = currentRateLimit()
        if (clock() < retryAt) {
            throw SourceFailure(UpdateIssueReason.RateLimited, retryAt)
        }
        return httpClient.newCall(request).await()
    }

    private fun parseObject(body: String): JsonObject =
        try {
            json.parseToJsonElement(body).jsonObject
        } catch (_: Exception) {
            throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        }

    private fun readBodyCapped(response: Response, limit: Long): String {
        response.body.use { body ->
            val source = body.source()
            val buffer = Buffer()
            while (true) {
                val read = source.read(buffer, BODY_CHUNK)
                if (read == -1L) break
                if (buffer.size > limit) {
                    throw SourceFailure(UpdateIssueReason.InvalidMetadata)
                }
            }
            return buffer.readUtf8()
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resume(response) { response.close() }
                } else {
                    response.close()
                }
            }
        })
    }

    private class SourceFailure(
        val reason: UpdateIssueReason,
        val retryAtEpochMillis: Long? = null,
    ) : Exception()

    private sealed interface ReleaseResult {
        data class Payload(val body: String) : ReleaseResult

        data object Retry : ReleaseResult
    }

    private class MetadataContent(
        val versionName: String,
        val versionCode: Long,
        val commitSha: String,
        val apkName: String,
        val apkSize: Long,
        val apkSha256: String,
        val changelog: UpdateChangelog,
    )

    private fun string(obj: JsonObject, name: String): String? =
        (obj[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun requiredString(obj: JsonObject, name: String): String =
        string(obj, name) ?: throw SourceFailure(UpdateIssueReason.InvalidMetadata)

    private fun number(obj: JsonObject, name: String): Long? =
        (obj[name] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

    private fun requiredId(obj: JsonObject, name: String): Long {
        val value = number(obj, name) ?: throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        if (value <= 0) throw SourceFailure(UpdateIssueReason.InvalidMetadata)
        return value
    }

    private fun requiredBool(obj: JsonObject, name: String): Boolean =
        (obj[name] as? JsonPrimitive)
            ?.takeIf { !it.isString }
            ?.booleanOrNull
            ?: throw SourceFailure(UpdateIssueReason.InvalidMetadata)

    private companion object {
        const val REPO_OWNER = "lightStarrr"
        const val REPO_NAME = "starSchedule"
        const val API_ROOT = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME"
        const val RELEASE_DOWNLOAD_HOST = "github.com"
        const val SNAPSHOT_TAG = "snapshot"
        const val METADATA_ASSET_NAME = "update.json"
        const val USER_AGENT = "starSchedule-android"
        const val API_BODY_LIMIT = 1024L * 1024
        const val METADATA_BODY_LIMIT = 256L * 1024
        const val BODY_CHUNK = 8 * 1024L
        const val DEFAULT_RATE_LIMIT_WINDOW_MILLIS = 60_000L
        const val MIN_RETRY_BACKOFF_MILLIS = 1_000L
        const val MAX_VERSION_CODE = 2_100_000_000L
        val FULL_SHA = Regex("[0-9a-f]{40}")
        val SHA256 = Regex("[0-9a-f]{64}")
        val STABLE_VERSION = Regex("\\d+\\.\\d+\\.\\d+")
        val SNAPSHOT_VERSION = Regex("\\d+\\.\\d+\\.\\d+-snapshot\\.([0-9a-f]+)")
        val CHANGELOG_KEYS = setOf("added", "changed", "fixed")
    }
}
