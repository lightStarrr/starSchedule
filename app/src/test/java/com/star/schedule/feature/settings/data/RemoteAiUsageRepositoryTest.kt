package com.star.schedule.feature.settings.data

import com.star.schedule.core.network.ApiClient
import com.star.schedule.core.network.TEST_BASE_URL
import com.star.schedule.core.network.fakeTransport
import com.star.schedule.core.network.responseOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteAiUsageRepositoryTest {
    @Test
    fun `nullable remaining tokens and unlimited flag decode together`() = runTest {
        val usage = repository { request ->
            responseOf(
                request,
                200,
                """{"daily_quota":200000,"quota_unlimited":true,"used_tokens":540,"remaining_tokens":null,"request_count":2}""",
            )
        }.fetchUsage().getOrThrow()

        assertTrue(usage.quotaUnlimited)
        assertNull(usage.remainingTokens)
        assertEquals(540L, usage.usedTokens)
        assertEquals(2L, usage.requestCount)
        assertEquals(0f, usage.usedFraction, 0f)
    }

    @Test
    fun `legacy zero quota still means unlimited`() = runTest {
        val usage = repository { request ->
            responseOf(request, 200, """{"daily_quota":0,"used_tokens":540,"remaining_tokens":0}""")
        }.fetchUsage().getOrThrow()

        assertTrue(usage.quotaUnlimited)
        assertNull(usage.remainingTokens)
    }

    @Test
    fun `finite quota uses server remaining and supports legacy response`() = runTest {
        val usage = repository { request ->
            responseOf(request, 200, """{"daily_quota":1000,"used_tokens":240,"remaining_tokens":700}""")
        }.fetchUsage().getOrThrow()

        assertFalse(usage.quotaUnlimited)
        assertEquals(700L, usage.remainingTokens)
        assertEquals(0.24f, usage.usedFraction, 0.0001f)
    }

    @Test
    fun `missing remaining value is derived and clamped for finite quota`() = runTest {
        for ((used, remaining) in listOf(240L to 760L, 1200L to 0L)) {
            val usage = repository { request ->
                responseOf(
                    request,
                    200,
                    """{"daily_quota":1000,"quota_unlimited":false,"used_tokens":$used,"remaining_tokens":null}""",
                )
            }.fetchUsage().getOrThrow()

            assertFalse(usage.quotaUnlimited)
            assertEquals(remaining, usage.remainingTokens)
        }
    }

    @Test
    fun `missing quota does not turn a malformed response into unlimited access`() = runTest {
        assertTrue(repository { responseOf(it, 200, "{}") }.fetchUsage().isFailure)
    }

    @Test
    fun `explicit finite flag is not overridden by legacy zero quota inference`() = runTest {
        assertTrue(
            repository {
                responseOf(it, 200, """{"daily_quota":0,"quota_unlimited":false}""")
            }.fetchUsage().isFailure,
        )
    }

    private fun TestScope.repository(transport: (Request) -> Response): RemoteAiUsageRepository {
        val client = ApiClient(
            httpClient = OkHttpClient.Builder().addInterceptor(fakeTransport(transport)).build(),
            baseUrl = TEST_BASE_URL,
            tokenProvider = { "test-token" },
            onUnauthorized = { null },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        return RemoteAiUsageRepository(TEST_BASE_URL) { client }
    }
}
