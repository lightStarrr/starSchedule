package com.star.schedule.core.network

import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * 一次请求的静态描述。
 *
 * `requestId` 在构造时就固定，重试与 401 重放都复用它——服务端日志靠它把一次逻辑请求的
 * 多次物理请求串起来。
 */
class ApiRequest(
    val method: String,
    val path: String,
    val body: String? = null,
    val authorized: Boolean = true,
    val retryPolicy: RetryPolicy = RetryPolicy.None,
    val requestId: String = UUID.randomUUID().toString(),
)

/**
 * 带鉴权与重试的挂起执行器。
 *
 * 401 的重放**不放拦截器**：拦截器里只能 `runBlocking` 等注册结果，会占住 OkHttp 的线程；
 * 而且注册请求自身也会经过同一个拦截器，`Mutex` 不可重入就会死锁。
 * 放在这里则天然是挂起语义，还能把 429/403 的具体原因映射成结构化错误。
 */
class ApiClient(
    private val httpClient: OkHttpClient,
    baseUrl: String,
    private val tokenProvider: suspend () -> String?,
    private val onUnauthorized: suspend (failedToken: String?) -> String?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/') + "/"

    suspend fun <T> call(
        request: ApiRequest,
        deserialize: (String) -> T,
    ): ApiResult<T> = withContext(ioDispatcher) {
        executeWithRetry(request, deserialize)
    }

    private suspend fun <T> executeWithRetry(
        request: ApiRequest,
        deserialize: (String) -> T,
    ): ApiResult<T> {
        var attempt = 0
        var authRetried = false

        var token: String? = if (request.authorized) tokenProvider() else null
        if (request.authorized && token == null) {
            // 本地没有 token：先注册一次再发，而不是直接失败
            token = onUnauthorized(null)
        }
        if (request.authorized && token == null) {
            return ApiResult.Failure(ApiError.Unauthorized(code = "UNAUTHENTICATED"))
        }

        while (true) {
            when (val outcome = executeOnce(request, token)) {
                is Outcome.Success -> {
                    return try {
                        ApiResult.Success(deserialize(outcome.body))
                    } catch (e: Exception) {
                        ApiResult.Failure(ApiError.Parse(e, outcome.requestId))
                    }
                }

                is Outcome.Failure -> {
                    val error = outcome.error

                    // 401：静默重注册后重放一次，且不消耗网络/5xx 的重试额度
                    if (error is ApiError.Http && error.status == 401 && request.authorized) {
                        if (authRetried) {
                            return ApiResult.Failure(
                                ApiError.Unauthorized(error.code, error.requestId),
                            )
                        }
                        authRetried = true
                        val refreshed = onUnauthorized(token)
                        if (refreshed == null) {
                            return ApiResult.Failure(
                                ApiError.Unauthorized(error.code, error.requestId),
                            )
                        }
                        token = refreshed
                        continue
                    }

                    val retryLimit = when {
                        error is ApiError.Network -> request.retryPolicy.backoffMillis.size
                        error is ApiError.Http && error.status == 429 ->
                            request.retryPolicy.backoffMillis.size
                        error is ApiError.Http && error.isRetryable ->
                            request.retryPolicy.maxServerErrorRetries
                        else -> 0
                    }
                    if (attempt < retryLimit) {
                        val wait = if (error is ApiError.Http && error.status == 429) {
                            request.retryPolicy.rateLimitDelay(error.retryAfterSeconds)
                        } else {
                            request.retryPolicy.backoffDelay(attempt)
                        }
                        delay(wait)
                        attempt++
                        continue
                    }
                    return ApiResult.Failure(error)
                }
            }
        }
    }

    private suspend fun executeOnce(request: ApiRequest, token: String?): Outcome {
        val httpRequest = try {
            request.toHttpRequest(token)
        } catch (e: IllegalArgumentException) {
            return Outcome.Failure(ApiError.Network(IOException("请求地址不合法：${request.path}", e)))
        }

        val call = httpClient.newCall(httpRequest)
        val response = try {
            suspendCancellableCoroutine<Response> { continuation: CancellableContinuation<Response> ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (continuation.isActive) {
                            continuation.resume(response)
                        } else {
                            response.close()
                        }
                    }
                })
            }
        } catch (e: IOException) {
            return Outcome.Failure(ApiError.Network(e))
        }

        response.use { resp ->
            val requestId = resp.header(CommonHeadersInterceptor.HEADER_REQUEST_ID)
            val rawBody = resp.body?.string().orEmpty()
            if (resp.isSuccessful) {
                return Outcome.Success(rawBody, requestId)
            }
            return Outcome.Failure(
                mapHttpError(resp.code, rawBody, requestId, resp.header(HEADER_RETRY_AFTER)),
            )
        }
    }

    private fun ApiRequest.toHttpRequest(token: String?): Request {
        val builder = Request.Builder()
            .url(normalizedBaseUrl + path.trimStart('/'))
            .header(CommonHeadersInterceptor.HEADER_REQUEST_ID, requestId)
        if (token != null) {
            builder.header(HEADER_AUTHORIZATION, "Bearer $token")
        }
        val verb = method.uppercase()
        return if (verb == "GET" || verb == "HEAD" || verb == "DELETE") {
            builder.method(verb, null).build()
        } else {
            builder.method(verb, (body ?: "").toRequestBody(ApiJsonMediaType)).build()
        }
    }

    private sealed interface Outcome {
        data class Success(val body: String, val requestId: String?) : Outcome
        data class Failure(val error: ApiError) : Outcome
    }

    companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
        const val HEADER_RETRY_AFTER = "Retry-After"
    }
}
