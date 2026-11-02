package com.star.schedule.core.telemetry

import com.star.schedule.core.network.ApiResult
import com.star.schedule.core.network.TEST_BASE_URL
import com.star.schedule.core.network.errorBody
import com.star.schedule.core.network.fakeTransport
import com.star.schedule.core.network.responseOf
import com.star.schedule.feature.settings.data.RemoteAiModelRepository
import com.star.schedule.feature.settings.data.RemoteAiUsageRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceApiClientTest {
    private val api = FakeDeviceApi()
    private val credentials = DeviceCredentialStore(TestKeyValueStore(), FakeCipher())
    private val session = DeviceSession(
        api = api,
        credentials = credentials,
        identity = DeviceIdentity({ "9774d56d682e549c" }, { "install-fallback" }),
        clientInfo = { telemetryClientInfo },
    )

    @Test
    fun `models usage and telemetry share a single first registration`() = runTest {
        api.registerDelayMillis = 10
        val client = createDeviceApiClient(
            httpClient = OkHttpClient.Builder().addInterceptor(fakeTransport { request ->
                assertEquals("Bearer token-1", request.header("Authorization"))
                val body = if (request.url.encodedPath.endsWith("models")) {
                    """{"items":[{"alias":"chatgpt-6.1-sol","display_name":"GPT"}],"default":"chatgpt-6.1-sol"}"""
                } else {
                    """{"daily_quota":200000,"quota_unlimited":true,"remaining_tokens":null}"""
                }
                responseOf(request, 200, body)
            }).build(),
            baseUrl = TEST_BASE_URL,
            session = { session },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        val models = RemoteAiModelRepository(TEST_BASE_URL) { client }
        val usage = RemoteAiUsageRepository(TEST_BASE_URL) { client }

        val registration = async { session.ensureRegistered() }
        val catalog = async { models.fetchModels().getOrThrow() }
        val quota = async { usage.fetchUsage().getOrThrow() }

        assertTrue(registration.await() is ApiResult.Success)
        assertEquals("chatgpt-6.1-sol", catalog.await().defaultAlias)
        assertTrue(quota.await().quotaUnlimited)
        assertEquals(1, api.registerCalls)
    }

    @Test
    fun `expired token renews once and both repositories recover`() = runTest {
        credentials.saveRegistration("device-1", "expired")
        api.registerDelayMillis = 10
        val client = createDeviceApiClient(
            httpClient = OkHttpClient.Builder().addInterceptor(fakeTransport { request ->
                if (request.header("Authorization") == "Bearer expired") {
                    responseOf(request, 401, errorBody("TOKEN_EXPIRED"))
                } else {
                    responseOf(request, 200, """{"items":[],"daily_quota":1000,"remaining_tokens":1000}""")
                }
            }).build(),
            baseUrl = TEST_BASE_URL,
            session = { session },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        val results = listOf(
            async { RemoteAiModelRepository(TEST_BASE_URL) { client }.fetchModels().isSuccess },
            async { RemoteAiUsageRepository(TEST_BASE_URL) { client }.fetchUsage().isSuccess },
        ).awaitAll()

        assertTrue(results.all { it })
        assertEquals(1, api.registerCalls)
        assertEquals("token-1", credentials.token())
    }

    @Test
    fun `response refresh header is visible to next request before returning`() = runTest {
        credentials.saveRegistration("device-1", "old")
        val seen = mutableListOf<String?>()
        val client = createDeviceApiClient(
            httpClient = OkHttpClient.Builder().addInterceptor(fakeTransport { request ->
                synchronized(seen) { seen += request.header("Authorization") }
                responseOf(request, 200, """{"items":[]}""", mapOf("X-Refresh-Token" to "new"))
            }).build(),
            baseUrl = TEST_BASE_URL,
            session = { session },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        val models = RemoteAiModelRepository(TEST_BASE_URL) { client }

        assertTrue(models.fetchModels().isSuccess)
        assertEquals("new", credentials.token())
        assertTrue(models.fetchModels().isSuccess)
        assertEquals(listOf("Bearer old", "Bearer new"), seen)
        assertEquals(0, api.registerCalls)
    }

    @Test
    fun `AI ban stops following requests without registration or transmission`() = runTest {
        credentials.saveRegistration("device-1", "token")
        var calls = 0
        val client = createDeviceApiClient(
            httpClient = OkHttpClient.Builder().addInterceptor(fakeTransport { request ->
                calls++
                responseOf(request, 403, errorBody("DEVICE_BANNED"))
            }).build(),
            baseUrl = TEST_BASE_URL,
            session = { session },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        assertTrue(RemoteAiModelRepository(TEST_BASE_URL) { client }.fetchModels().isFailure)
        assertTrue(RemoteAiUsageRepository(TEST_BASE_URL) { client }.fetchUsage().isFailure)
        assertEquals(1, calls)
        assertEquals(0, api.registerCalls)
    }

    @Test
    fun `disabling privacy stops a scheduled network retry`() = runTest {
        credentials.saveRegistration("device-1", "token")
        var enabled = true
        var calls = 0
        val gatedSession = DeviceSession(
            api = api,
            credentials = credentials,
            identity = DeviceIdentity({ "9774d56d682e549c" }, { "install-fallback" }),
            clientInfo = { telemetryClientInfo },
            enabled = { enabled },
        )
        val client = createDeviceApiClient(
            httpClient = OkHttpClient.Builder().addInterceptor(fakeTransport { request ->
                calls++
                enabled = false
                responseOf(request, 503, errorBody("SERVICE_UNAVAILABLE"))
            }).build(),
            baseUrl = TEST_BASE_URL,
            session = { gatedSession },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        assertTrue(RemoteAiUsageRepository(TEST_BASE_URL) { client }.fetchUsage().isFailure)
        assertEquals(1, calls)
        assertEquals(0, api.registerCalls)
    }
}
