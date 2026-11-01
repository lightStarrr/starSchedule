package com.star.schedule.core.network

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiClientTest {

    private fun apiClient(
        dispatcher: CoroutineDispatcher,
        transport: Interceptor,
        tokenProvider: suspend () -> String? = { "token-0" },
        onUnauthorized: suspend (String?) -> String? = { null },
    ): ApiClient {
        val httpClient = OkHttpClient.Builder()
            .addInterceptor(CommonHeadersInterceptor({ testClientInfo }))
            .addInterceptor(transport)
            .build()
        return ApiClient(httpClient, TEST_BASE_URL, tokenProvider, onUnauthorized, dispatcher)
    }

    @Test
    fun `replays once after 401 and reuses request id`() = runTest {
        val seen = mutableListOf<Request>()
        val transport = fakeTransport { request ->
            seen += request
            if (seen.size == 1) {
                responseOf(request, 401, errorBody("TOKEN_EXPIRED"))
            } else {
                responseOf(request, 204)
            }
        }
        val client = apiClient(
            dispatcher = StandardTestDispatcher(testScheduler),
            transport = transport,
            onUnauthorized = { "token-1" },
        )

        val result = client.call(ApiRequest("POST", "launches", body = "{}", retryPolicy = RetryPolicy.Launch)) {}

        assertTrue(result is ApiResult.Success)
        assertEquals(2, seen.size)
        assertEquals(seen[0].header("X-Request-Id"), seen[1].header("X-Request-Id"))
        assertEquals("Bearer token-0", seen[0].header("Authorization"))
        assertEquals("Bearer token-1", seen[1].header("Authorization"))
    }

    @Test
    fun `stops after a second 401`() = runTest {
        val seen = mutableListOf<Request>()
        val transport = fakeTransport { request ->
            seen += request
            responseOf(request, 401, errorBody("TOKEN_INVALID"))
        }
        val client = apiClient(
            dispatcher = StandardTestDispatcher(testScheduler),
            transport = transport,
            onUnauthorized = { "token-1" },
        )

        val result = client.call(ApiRequest("GET", "devices/me", retryPolicy = RetryPolicy.Read)) { it }

        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is ApiError.Unauthorized)
        assertEquals(2, seen.size)
    }

    @Test
    fun `fails without sending when no token can be obtained`() = runTest {
        var calls = 0
        val transport = fakeTransport { request ->
            calls++
            responseOf(request, 204)
        }
        val client = apiClient(
            dispatcher = StandardTestDispatcher(testScheduler),
            transport = transport,
            tokenProvider = { null },
            onUnauthorized = { null },
        )

        val result = client.call(ApiRequest("GET", "devices/me")) { it }

        assertTrue(result is ApiResult.Failure)
        assertEquals(0, calls)
    }

    @Test
    fun `retries 5xx per policy then gives up`() = runTest {
        var calls = 0
        val transport = fakeTransport { request ->
            calls++
            responseOf(request, 503, errorBody("SERVICE_UNAVAILABLE"))
        }
        val client = apiClient(StandardTestDispatcher(testScheduler), transport)

        val result = client.call(ApiRequest("GET", "devices/me", retryPolicy = RetryPolicy.Read)) { it }

        assertTrue(result is ApiResult.Failure)
        assertEquals(3, calls) // 首次 + 2 次重试
    }

    @Test
    fun `does not retry client errors`() = runTest {
        var calls = 0
        val transport = fakeTransport { request ->
            calls++
            responseOf(request, 400, errorBody("INVALID_ARGUMENT"))
        }
        val client = apiClient(StandardTestDispatcher(testScheduler), transport)

        val result = client.call(ApiRequest("POST", "launches", body = "{}", retryPolicy = RetryPolicy.Launch)) {}

        assertTrue(result is ApiResult.Failure)
        assertEquals(1, calls)
    }

    @Test
    fun `retries network failures up to the policy limit`() = runTest {
        var calls = 0
        val transport = fakeTransport {
            calls++
            throw java.io.IOException("网络不可用")
        }
        val client = apiClient(StandardTestDispatcher(testScheduler), transport)

        val result = client.call(ApiRequest("GET", "devices/me", retryPolicy = RetryPolicy.Read)) { it }

        assertTrue(result is ApiResult.Failure)
        assertTrue((result as ApiResult.Failure).error is ApiError.Network)
        assertEquals(3, calls)
    }

    @Test
    fun `omits authorization header for anonymous requests`() = runTest {
        var authorization: String? = "unset"
        val transport = fakeTransport { request ->
            authorization = request.header("Authorization")
            responseOf(request, 200, "{}")
        }
        val client = apiClient(StandardTestDispatcher(testScheduler), transport)

        client.call(ApiRequest("POST", "devices/register", body = "{}", authorized = false)) { it }

        assertNull(authorization)
    }

    @Test
    fun `sends contract headers`() = runTest {
        var captured: Request? = null
        val transport = fakeTransport { request ->
            captured = request
            responseOf(request, 200, "{}")
        }
        val client = apiClient(StandardTestDispatcher(testScheduler), transport)

        client.call(ApiRequest("GET", "devices/me")) { it }

        val request = requireNotNull(captured)
        assertEquals(testClientInfo.userAgent, request.header("User-Agent"))
        assertEquals("1.2.0", request.header("X-Client-Version"))
        assertEquals("120", request.header("X-Client-Version-Code"))
        assertTrue(request.header("X-Request-Id").isNullOrBlank().not())
        assertEquals("http://127.0.0.1:8080/api/v1/devices/me", request.url.toString())
    }
}
