package com.star.schedule.feature.settings.data

import com.star.schedule.core.network.ApiClient
import com.star.schedule.core.network.TEST_BASE_URL
import com.star.schedule.core.network.errorBody
import com.star.schedule.core.network.fakeTransport
import com.star.schedule.core.network.responseOf
import com.star.schedule.feature.settings.domain.AiModelCatalog
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteAiModelRepositoryTest {
    @Test
    fun `unconfigured service is a successful empty catalog`() = runTest {
        val repository = repository { request ->
            responseOf(request, 503, errorBody("AI_NOT_CONFIGURED"))
        }

        assertEquals(AiModelCatalog(), repository.fetchModels().getOrThrow())
    }

    @Test
    fun `temporary service outage stays a failure instead of an empty catalog`() = runTest {
        val repository = repository { request ->
            responseOf(request, 503, errorBody("SERVICE_UNAVAILABLE"))
        }

        assertTrue(repository.fetchModels().isFailure)
    }

    @Test
    fun `gateway 503 without an API error code stays a failure`() = runTest {
        val repository = repository { request ->
            responseOf(request, 503, "Service temporarily unavailable")
        }

        assertTrue(repository.fetchModels().isFailure)
    }

    @Test
    fun `unconfigured code at another status does not imply an empty catalog`() = runTest {
        val repository = repository { request ->
            responseOf(request, 500, errorBody("AI_NOT_CONFIGURED"))
        }

        assertTrue(repository.fetchModels().isFailure)
    }

    @Test
    fun `successful model response preserves aliases and default selection`() = runTest {
        val repository = repository { request ->
            responseOf(
                request,
                200,
                """{"items":[{"alias":"model-a","display_name":"模型甲"}],"default":"model-a"}""",
            )
        }

        val catalog = repository.fetchModels().getOrThrow()

        assertEquals("model-a", catalog.defaultModel?.alias)
        assertEquals("模型甲", catalog.defaultModel?.displayName)
    }

    @Test
    fun `missing items is a parse failure rather than a successful empty catalog`() = runTest {
        assertTrue(repository { responseOf(it, 200, "{}") }.fetchModels().isFailure)
    }

    private fun TestScope.repository(transport: (Request) -> Response): RemoteAiModelRepository {
        val client = ApiClient(
            httpClient = OkHttpClient.Builder().addInterceptor(fakeTransport(transport)).build(),
            baseUrl = TEST_BASE_URL,
            tokenProvider = { "test-token" },
            onUnauthorized = { null },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        return RemoteAiModelRepository(TEST_BASE_URL) { client }
    }
}
