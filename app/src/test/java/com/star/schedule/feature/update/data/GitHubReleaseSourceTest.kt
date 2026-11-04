package com.star.schedule.feature.update.data

import com.star.schedule.core.network.fakeTransport
import com.star.schedule.core.network.responseOf
import com.star.schedule.feature.update.domain.CommitRelation
import com.star.schedule.feature.update.domain.UpdateCacheStore
import com.star.schedule.feature.update.domain.UpdateChannel
import com.star.schedule.feature.update.domain.UpdateFetchOutcome
import com.star.schedule.feature.update.domain.UpdateIssueReason
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GitHubReleaseSourceTest {
    private var now = 1_000_000L
    private val requests = mutableListOf<Request>()
    private var handler: (Request) -> Response = { responseOf(it, 404) }
    private val cache = FakeCacheStore()

    private class FakeCacheStore : UpdateCacheStore {
        val entries = mutableMapOf<String, String>()

        override suspend fun read(key: String): String? = entries[key]

        override suspend fun write(key: String, value: String) {
            entries[key] = value
        }
    }

    private fun source() = GitHubReleaseSource(
        httpClient = OkHttpClient.Builder()
            .followSslRedirects(false)
            .addInterceptor(fakeTransport { request ->
                requests += request
                handler(request)
            })
            .build(),
        cacheStore = cache,
        clock = { now },
        snapshotRetryDelayMillis = 0,
    )

    private fun respondStable(
        body: String = stableReleaseBody(),
        code: Int = 200,
        headers: Map<String, String> = emptyMap(),
        metadataBody: String = metadataBody(),
        metadataCode: Int = 200,
        metadataHeaders: Map<String, String> = emptyMap(),
    ) {
        handler = { request ->
            when {
                request.url.encodedPath.endsWith("/releases/latest") ->
                    responseOf(request, code, body, headers)

                request.url.encodedPath.contains("/releases/download/") ->
                    responseOf(request, metadataCode, metadataBody, metadataHeaders)

                else -> responseOf(request, 404)
            }
        }
    }

    private fun metadataBody(
        channel: String = "stable",
        versionName: String = "1.3.0",
        versionCode: Int = 130,
        commitSha: String = SHA_REMOTE,
        apkName: String = "starSchedule-v1.3.0.apk",
        apkSize: Long = 12_345,
        apkSha256: String = SHA256_APK,
        generatedAt: String = "2024-06-01T00:00:00Z",
        schemaVersion: Int = 1,
    ) = """
        {"schemaVersion":$schemaVersion,"channel":"$channel","versionName":"$versionName",
         "versionCode":$versionCode,"commitSha":"$commitSha","generatedAt":"$generatedAt",
         "apk":{"name":"$apkName","size":$apkSize,"sha256":"$apkSha256"},
         "changelog":{"added":["新功能 A"],"changed":["优化 B"],"fixed":[]}}
    """.trimIndent()

    private fun asset(id: Long, name: String, size: Long, tag: String, url: String? = null) =
        """{"id":$id,"name":"$name","size":$size,"browser_download_url":"${
            url ?: "https://github.com/lightStarrr/starSchedule/releases/download/$tag/$name"
        }"}"""

    private fun stableReleaseBody(
        id: Long = 55,
        tag: String = "v1.3.0",
        draft: Boolean = false,
        prerelease: Boolean = false,
        metaAssetId: Long = 501,
        apkAssetId: Long = 502,
        assets: List<String> = listOf(
            asset(metaAssetId, "update.json", 900, tag),
            asset(apkAssetId, "starSchedule-v1.3.0.apk", 12_345, tag),
        ),
    ) = """
        {"id":$id,"tag_name":"$tag","draft":$draft,"prerelease":$prerelease,
         "html_url":"https://github.com/lightStarrr/starSchedule/releases/tag/$tag",
         "assets":[${assets.joinToString(",")}]}
    """.trimIndent()

    private fun snapshotReleaseBody(
        tag: String = "snapshot",
        prerelease: Boolean = true,
        assets: List<String> = listOf(
            asset(601, "update.json", 900, tag),
            asset(602, "starSchedule-snapshot-abcdefg.apk", 12_345, tag),
        ),
    ) = stableReleaseBody(
        id = 66,
        tag = tag,
        prerelease = prerelease,
        metaAssetId = 601,
        apkAssetId = 602,
        assets = assets,
    )

    private fun respondSnapshot(metadataBody: String = snapshotMetadataBody()) {
        handler = { request ->
            when {
                request.url.encodedPath.endsWith("/releases/tags/snapshot") ->
                    responseOf(request, 200, snapshotReleaseBody())

                request.url.encodedPath.contains("/releases/download/") ->
                    responseOf(request, 200, metadataBody)

                else -> responseOf(request, 404)
            }
        }
    }

    private fun snapshotMetadataBody() = metadataBody(
        channel = "snapshot",
        versionName = "1.2.0-snapshot.abcdef0",
        versionCode = 120,
        apkName = "starSchedule-snapshot-abcdefg.apk",
        commitSha = SHA_SNAPSHOT,
    )

    private fun reason(outcome: UpdateFetchOutcome) =
        (outcome as UpdateFetchOutcome.Failure).issue.reason

    @Test
    fun `stable release fetches metadata asset and builds the release`() = runTest {
        respondStable()
        val outcome = source().fetchRelease(UpdateChannel.Stable)

        val release = (outcome as UpdateFetchOutcome.Success).release
        assertEquals(UpdateChannel.Stable, release.channel)
        assertEquals("1.3.0", release.versionName)
        assertEquals(130, release.versionCode)
        assertEquals(SHA_REMOTE, release.commitSha)
        assertEquals("starSchedule-v1.3.0.apk", release.apkName)
        assertEquals(12_345, release.apkSize)
        assertEquals(SHA256_APK, release.apkSha256)
        assertEquals(listOf("新功能 A"), release.changelog.added)
        assertEquals(listOf("优化 B"), release.changelog.changed)
        assertEquals(55, release.releaseId)
        assertEquals(501, release.metadataAssetId)
        assertEquals(502, release.apkAssetId)

        val releaseRequest = requests.first()
        assertEquals(
            "/repos/lightStarrr/starSchedule/releases/latest",
            releaseRequest.url.encodedPath,
        )
        assertEquals("https", releaseRequest.url.scheme)
        assertNull(releaseRequest.header("Authorization"))
        assertTrue(releaseRequest.header("User-Agent").orEmpty().isNotBlank())
        assertEquals(
            "https://github.com/lightStarrr/starSchedule/releases/download/v1.3.0/starSchedule-v1.3.0.apk",
            release.apkDownloadUrl,
        )
    }

    @Test
    fun `etag revalidation reuses cached release on 304`() = runTest {
        val source = source()
        respondStable(headers = mapOf("ETag" to "\"stable-1\""))
        val first = source.fetchRelease(UpdateChannel.Stable) as UpdateFetchOutcome.Success

        respondStable(code = 304)
        val second = source.fetchRelease(UpdateChannel.Stable) as UpdateFetchOutcome.Success

        assertEquals(first.release, second.release)
        val releaseRequests = requests.filter { it.url.encodedPath.endsWith("/latest") }
        assertEquals(2, releaseRequests.size)
        assertEquals("\"stable-1\"", releaseRequests[1].header("If-None-Match"))
        val metadataRequests = requests.count {
            it.url.encodedPath.contains("/releases/download/")
        }
        assertEquals(1, metadataRequests)
    }

    @Test
    fun `304 without a cached body is not a success`() = runTest {
        respondStable(code = 304)
        val outcome = source().fetchRelease(UpdateChannel.Stable)
        assertTrue(outcome is UpdateFetchOutcome.Failure)
        assertEquals(
            2,
            requests.count { it.url.encodedPath.endsWith("/latest") },
        )
    }

    @Test
    fun `304 with unusable cache refetches without the etag`() = runTest {
        val etags = mutableListOf<String?>()
        handler = { request ->
            when {
                request.url.encodedPath.endsWith("/releases/latest") -> {
                    etags += request.header("If-None-Match")
                    if (etags.size == 1) {
                        responseOf(request, 304)
                    } else {
                        responseOf(request, 200, stableReleaseBody(), mapOf("ETag" to "\"x\""))
                    }
                }

                else -> responseOf(request, 200, metadataBody())
            }
        }
        cache.write(
            "release.stable",
            """{"releaseEtag":"\"stale\"","releaseBody":"","metadataAssetId":0,"metadataEtag":null,"metadataBody":""}""",
        )
        val outcome = source().fetchRelease(UpdateChannel.Stable)
        assertTrue(outcome is UpdateFetchOutcome.Success)
        assertEquals(listOf("\"stale\"", null), etags)
    }

    @Test
    fun `snapshot requires a prerelease tagged snapshot`() = runTest {
        respondSnapshot()
        val release = (source().fetchRelease(UpdateChannel.Snapshot)
            as UpdateFetchOutcome.Success).release
        assertEquals(UpdateChannel.Snapshot, release.channel)
        assertEquals("1.2.0-snapshot.abcdef0", release.versionName)
        assertTrue(
            requests.first().url.encodedPath.endsWith("/releases/tags/snapshot"),
        )

        handler = { request ->
            when {
                request.url.encodedPath.endsWith("/releases/tags/snapshot") ->
                    responseOf(request, 200, snapshotReleaseBody(prerelease = false))

                else -> responseOf(request, 200, snapshotMetadataBody())
            }
        }
        val outcome = GitHubReleaseSource(
            httpClient = OkHttpClient.Builder()
                .addInterceptor(fakeTransport { requests += it; handler(it) })
                .build(),
            cacheStore = FakeCacheStore(),
            clock = { now },
            snapshotRetryDelayMillis = 0,
        ).fetchRelease(UpdateChannel.Snapshot)
        assertEquals(UpdateIssueReason.ReleaseUnavailable, reason(outcome))
    }

    @Test
    fun `snapshot 404 retries once then reports the release unavailable`() = runTest {
        var snapshotCalls = 0
        handler = { request ->
            if (request.url.encodedPath.endsWith("/releases/tags/snapshot")) {
                snapshotCalls++
            }
            responseOf(request, 404)
        }
        val outcome = source().fetchRelease(UpdateChannel.Snapshot)
        assertEquals(2, snapshotCalls)
        assertEquals(UpdateIssueReason.ReleaseUnavailable, reason(outcome))
    }

    @Test
    fun `network failure and non rate limited 403 are not rate limits`() = runTest {
        handler = { throw IOException("no network") }
        assertEquals(
            UpdateIssueReason.Network,
            reason(source().fetchRelease(UpdateChannel.Stable)),
        )

        handler = { responseOf(it, 403) }
        assertEquals(
            UpdateIssueReason.ReleaseUnavailable,
            reason(source().fetchRelease(UpdateChannel.Stable)),
        )
    }

    @Test
    fun `rate limit headers produce a retry window served from cache`() = runTest {
        val source = source()
        respondStable()
        source.fetchRelease(UpdateChannel.Stable)

        val resetSeconds = 1_700_000_000L
        handler = {
            responseOf(
                it, 403, "",
                mapOf(
                    "X-RateLimit-Remaining" to "0",
                    "X-RateLimit-Reset" to resetSeconds.toString(),
                ),
            )
        }
        val rateLimited = source.fetchRelease(UpdateChannel.Stable) as UpdateFetchOutcome.Failure
        assertEquals(UpdateIssueReason.RateLimited, rateLimited.issue.reason)
        assertEquals(resetSeconds * 1000, rateLimited.issue.retryAtEpochMillis)

        val before = requests.size
        now = resetSeconds * 1000 - 1
        val inWindow = source.fetchRelease(UpdateChannel.Stable) as UpdateFetchOutcome.Failure
        assertEquals(UpdateIssueReason.RateLimited, inWindow.issue.reason)
        assertEquals(before, requests.size)
        assertTrue(inWindow.staleRelease != null)

        now = resetSeconds * 1000 + 1
        respondStable()
        val after = source.fetchRelease(UpdateChannel.Stable)
        assertTrue(after is UpdateFetchOutcome.Success)
    }

    @Test
    fun `retry after header on 429 also opens a rate limit window`() = runTest {
        handler = { responseOf(it, 429, "", mapOf("Retry-After" to "30")) }
        val outcome = source().fetchRelease(UpdateChannel.Stable) as UpdateFetchOutcome.Failure
        assertEquals(UpdateIssueReason.RateLimited, outcome.issue.reason)
        assertEquals(now + 30_000, outcome.issue.retryAtEpochMillis)
    }

    @Test
    fun `invalid metadata shapes are rejected`() = runTest {
        suspend fun expect(
            metadataBody: String = metadataBody(),
            releaseBody: String = stableReleaseBody(),
            reason: UpdateIssueReason = UpdateIssueReason.InvalidMetadata,
        ) {
            val localSource = GitHubReleaseSource(
                httpClient = OkHttpClient.Builder()
                    .addInterceptor(fakeTransport { request ->
                        when {
                            request.url.encodedPath.endsWith("/releases/latest") ->
                                responseOf(request, 200, releaseBody)

                            else -> responseOf(request, 200, metadataBody)
                        }
                    })
                    .build(),
                cacheStore = FakeCacheStore(),
                clock = { now },
                snapshotRetryDelayMillis = 0,
            )
            val outcome = localSource.fetchRelease(UpdateChannel.Stable)
            assertEquals(reason, reason(outcome))
        }

        expect(metadataBody = metadataBody(schemaVersion = 2))
        expect(metadataBody = metadataBody(versionName = "1.3"))
        expect(metadataBody = metadataBody(versionCode = 0))
        expect(metadataBody = metadataBody(commitSha = "abc"))
        expect(metadataBody = metadataBody(apkSha256 = "xyz"))
        expect(metadataBody = metadataBody(apkSize = 0))
        expect(metadataBody = metadataBody(generatedAt = "not a time"))
        expect(metadataBody = metadataBody(apkName = "evil.apk"))
        expect(metadataBody = metadataBody(channel = "beta"))
        expect(metadataBody = "not json")
        expect(
            releaseBody = stableReleaseBody(assets = emptyList()),
            reason = UpdateIssueReason.MetadataUnavailable,
        )
        expect(
            releaseBody = stableReleaseBody(
                assets = listOf(
                    asset(501, "update.json", 900, "v1.3.0"),
                    asset(503, "update.json", 900, "v1.3.0"),
                    asset(502, "starSchedule-v1.3.0.apk", 12_345, "v1.3.0"),
                ),
            ),
            reason = UpdateIssueReason.MetadataUnavailable,
        )
        expect(
            releaseBody = stableReleaseBody(
                assets = listOf(
                    asset(501, "update.json", 900, "v1.3.0"),
                    asset(
                        502, "starSchedule-v1.3.0.apk", 12_345, "v1.3.0",
                        url = "https://evil.example.com/starSchedule-v1.3.0.apk",
                    ),
                ),
            ),
        )
        expect(
            releaseBody = stableReleaseBody(tag = "v9.9.9"),
        )
    }

    @Test
    fun `compare maps github status to relation`() = runTest {
        val source = source()
        for ((status, expected) in listOf(
            "ahead" to CommitRelation.Ahead,
            "behind" to CommitRelation.Behind,
            "identical" to CommitRelation.Identical,
            "diverged" to CommitRelation.Diverged,
        )) {
            handler = { request ->
                assertTrue(request.url.encodedPath.contains("/compare/"))
                responseOf(request, 200, """{"status":"$status"}""")
            }
            assertEquals(expected, source.compareCommits(SHA_LOCAL, SHA_REMOTE))
        }
        handler = { responseOf(it, 500) }
        assertEquals(CommitRelation.Unknown, source.compareCommits(SHA_LOCAL, SHA_REMOTE))
    }

    @Test
    fun `cancellation propagates instead of a generic failure`() = runTest {
        val latch = CountDownLatch(1)
        handler = { request ->
            latch.await(10, TimeUnit.SECONDS)
            responseOf(request, 200, stableReleaseBody())
        }
        val job = async { source().fetchRelease(UpdateChannel.Stable) }
        kotlinx.coroutines.yield()
        job.cancel()
        try {
            job.await()
            fail("expected cancellation")
        } catch (_: CancellationException) {
        } finally {
            latch.countDown()
        }
    }

    private class TrackedBody(
        private val content: String,
        val onClose: () -> Unit,
    ) : ResponseBody() {
        override fun contentType() = "application/json; charset=utf-8".toMediaType()

        override fun contentLength() = content.toByteArray().size.toLong()

        override fun source() = okio.Buffer().writeUtf8(content)

        override fun close() {
            onClose()
            super.close()
        }
    }

    private fun trackedResponse(
        request: Request,
        code: Int,
        body: String,
        onClose: () -> Unit,
        headers: Map<String, String> = emptyMap(),
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("t")
        .apply { headers.forEach { (n, v) -> header(n, v) } }
        .body(TrackedBody(body, onClose))
        .build()

    @Test
    fun `every response body is closed on all status branches`() = runTest {
        var closes = 0
        for (code in listOf(200, 304, 403, 404)) {
            closes = 0
            handler = { trackedResponse(it, code, stableReleaseBody(), { closes++ }) }
            GitHubReleaseSource(
                httpClient = OkHttpClient.Builder()
                    .addInterceptor(fakeTransport { requests += it; handler(it) })
                    .build(),
                cacheStore = FakeCacheStore(),
                clock = { now },
                snapshotRetryDelayMillis = 0,
            ).fetchRelease(UpdateChannel.Stable)
            assertTrue("code $code leaked", closes >= 1)
        }
        closes = 0
        handler = { trackedResponse(it, 500, """{"status":"ahead"}""", { closes++ }) }
        source().compareCommits(SHA_LOCAL, SHA_REMOTE)
        assertTrue(closes >= 1)
    }

    @Test
    fun `strict json types reject coerced values`() = runTest {
        suspend fun expect(body: String) {
            val localSource = GitHubReleaseSource(
                httpClient = OkHttpClient.Builder()
                    .addInterceptor(
                        fakeTransport { request ->
                            when {
                                request.url.encodedPath.endsWith("/releases/latest") ->
                                    responseOf(request, 200, body)
                                else -> responseOf(request, 200, metadataBody())
                            }
                        },
                    )
                    .build(),
                cacheStore = FakeCacheStore(),
                clock = { now },
                snapshotRetryDelayMillis = 0,
            )
            assertEquals(
                UpdateIssueReason.InvalidMetadata,
                reason(localSource.fetchRelease(UpdateChannel.Stable)),
            )
        }
        expect(stableReleaseBody().replace("\"id\":55", "\"id\":\"55\""))
        expect(stableReleaseBody().replace("\"draft\":false", "\"draft\":\"false\""))
        expect(stableReleaseBody().replace("\"id\":55", "\"id\":55.5"))
        expect(
            stableReleaseBody().replace(
                "https://github.com/lightStarrr/starSchedule/releases/tag/v1.3.0",
                "https://evil.example.com/x",
            ),
        )
        expect(
            stableReleaseBody().replace(
                "https://github.com/lightStarrr/starSchedule/releases/tag/v1.3.0",
                "https://github.com/lightStarrr/starSchedule/releases/tag/v1.3.0?x=1",
            ),
        )
    }

    @Test
    fun `metadata strict types and changelog coercion are rejected`() = runTest {
        suspend fun expect(metaBody: String) {
            val localSource = GitHubReleaseSource(
                httpClient = OkHttpClient.Builder()
                    .addInterceptor(
                        fakeTransport { request ->
                            when {
                                request.url.encodedPath.endsWith("/releases/latest") ->
                                    responseOf(request, 200, stableReleaseBody())
                                else -> responseOf(request, 200, metaBody)
                            }
                        },
                    )
                    .build(),
                cacheStore = FakeCacheStore(),
                clock = { now },
                snapshotRetryDelayMillis = 0,
            )
            assertEquals(
                UpdateIssueReason.InvalidMetadata,
                reason(localSource.fetchRelease(UpdateChannel.Stable)),
            )
        }
        expect(metadataBody().replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""))
        expect(metadataBody().replace("\"versionCode\":130", "\"versionCode\":\"130\""))
        expect(metadataBody().replace("\"size\":12345", "\"size\":\"12345\""))
        expect(metadataBody().replace("[\"新功能 A\"]", "[1]"))
        expect(metadataBody().replace("\"versionName\":\"1.3.0\"", "\"versionName\":null"))

        expect(metadataBody(apkName = "../evil.apk"))
        expect(metadataBody(apkName = "a\b.apk"))
        expect(
            metadataBody().replace(
                "starSchedule-v1.3.0.apk",
                "..%2f..%2fevil.apk",
            ),
        )
    }

    @Test
    fun `cache read and write failures still produce a release plus storage issue`() = runTest {
        val failing = object : UpdateCacheStore {
            var data: String? = null

            override suspend fun read(key: String): String? = error("read fail")

            override suspend fun write(key: String, value: String) {
                error("write fail")
            }
        }
        respondStable()
        val local = GitHubReleaseSource(
            httpClient = OkHttpClient.Builder()
                .addInterceptor(fakeTransport { requests += it; handler(it) })
                .build(),
            cacheStore = failing,
            clock = { now },
            snapshotRetryDelayMillis = 0,
        )
        val outcome = local.fetchRelease(UpdateChannel.Stable)
        assertTrue(outcome is UpdateFetchOutcome.Success)
        assertTrue(
            (outcome as UpdateFetchOutcome.Success).issues.any {
                it.reason == UpdateIssueReason.Storage
            },
        )
    }

    @Test
    fun `a cached entry with a different release id does not reuse metadata`() = runTest {
        respondStable(headers = mapOf("ETag" to "\"old\""))
        source().fetchRelease(UpdateChannel.Stable)
        val metadataCallsBefore = requests.count {
            it.url.encodedPath.contains("/releases/download/")
        }
        requests.clear()
        respondStable(
            body = stableReleaseBody(id = 99),
            headers = mapOf("ETag" to "\"new\""),
        )
        source().fetchRelease(UpdateChannel.Stable)
        val metadataCalls = requests.count {
            it.url.encodedPath.contains("/releases/download/")
        }
        assertTrue(metadataCalls >= 1)
    }

    @Test
    fun `rate limit persists across source instances and compare honors it`() = runTest {
        val shared = FakeCacheStore()
        fun local() = GitHubReleaseSource(
            httpClient = OkHttpClient.Builder()
                .addInterceptor(fakeTransport { requests += it; handler(it) })
                .build(),
            cacheStore = shared,
            clock = { now },
            snapshotRetryDelayMillis = 0,
        )
        handler = { responseOf(it, 429, "", mapOf("Retry-After" to "120")) }
        local().fetchRelease(UpdateChannel.Stable)
        val calls = requests.size
        val second = local().fetchRelease(UpdateChannel.Stable) as UpdateFetchOutcome.Failure
        assertEquals(UpdateIssueReason.RateLimited, second.issue.reason)
        assertEquals(calls, requests.size)
        assertEquals(
            CommitRelation.Unknown,
            local().compareCommits(SHA_LOCAL, SHA_REMOTE),
        )
        assertEquals(calls, requests.size)
    }

    @Test
    fun `compare 429 suppresses subsequent release fetches`() = runTest {
        val source = source()
        handler = { responseOf(it, 429, "", mapOf("Retry-After" to "60")) }
        assertEquals(CommitRelation.Unknown, source.compareCommits(SHA_LOCAL, SHA_REMOTE))
        val calls = requests.size
        val outcome = source.fetchRelease(UpdateChannel.Stable) as UpdateFetchOutcome.Failure
        assertEquals(UpdateIssueReason.RateLimited, outcome.issue.reason)
        assertEquals(calls, requests.size)
    }

    @Test
    fun `http date retry-after and past reset are normalized`() = runTest {
        handler = {
            responseOf(
                it, 429, "",
                mapOf("Retry-After" to "Wed, 21 Oct 2099 07:28:00 GMT"),
            )
        }
        val outcome = source().fetchRelease(UpdateChannel.Stable) as UpdateFetchOutcome.Failure
        assertEquals(UpdateIssueReason.RateLimited, outcome.issue.reason)
        assertTrue(outcome.issue.retryAtEpochMillis!! > now)

        handler = {
            responseOf(
                it, 403, "",
                mapOf(
                    "X-RateLimit-Remaining" to "0",
                    "X-RateLimit-Reset" to "1",
                ),
            )
        }
        val past = GitHubReleaseSource(
            httpClient = OkHttpClient.Builder()
                .addInterceptor(fakeTransport { requests += it; handler(it) })
                .build(),
            cacheStore = FakeCacheStore(),
            clock = { now },
            snapshotRetryDelayMillis = 0,
        ).fetchRelease(UpdateChannel.Stable) as UpdateFetchOutcome.Failure
        assertEquals(UpdateIssueReason.RateLimited, past.issue.reason)
        assertTrue(past.issue.retryAtEpochMillis!! >= now)
    }

    private companion object {
        const val SHA_REMOTE = "cccccccccccccccccccccccccccccccccccccccc"
        const val SHA_LOCAL = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val SHA_SNAPSHOT = "abcdef0000000000000000000000000000000000"
        const val SHA256_APK = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
    }
}
