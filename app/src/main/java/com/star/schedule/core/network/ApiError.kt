package com.star.schedule.core.network

import java.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 后端统一错误体（契约见服务端 `docs/api.md` 第 2 节）：
 * `{"error":{"code","message","request_id","details"?}}`。
 *
 * `code` 稳定不变，供程序分支；`message` 可直接展示给用户。
 */
sealed interface ApiError {
    /** 服务端 request_id，排查时展示给用户。 */
    val requestId: String?

    /** 网络层失败：连接不上、超时、被取消以外的 IO 异常。 */
    data class Network(
        val cause: IOException,
        override val requestId: String? = null,
    ) : ApiError

    /** 服务端返回了非 2xx。 */
    data class Http(
        val status: Int,
        val code: String,
        val message: String,
        override val requestId: String? = null,
        val retryAfterSeconds: Long? = null,
        val details: JsonObject? = null,
    ) : ApiError {
        val isBanned: Boolean get() = status == 403 && code == CODE_DEVICE_BANNED

        /** 429 与 5xx 值得退避重试；其余 4xx 重发也不会成功。 */
        val isRetryable: Boolean get() = status == 429 || status >= 500
    }

    /** 2xx 但响应体解析失败，通常是契约变更或服务端异常。 */
    data class Parse(
        val cause: Exception,
        override val requestId: String? = null,
    ) : ApiError

    /** 401 且重新注册后仍然拿不到可用 token。 */
    data class Unauthorized(
        val code: String,
        override val requestId: String? = null,
    ) : ApiError

    /** 403 DEVICE_BANNED：设备被封禁，停止一切上报。 */
    data class Banned(
        override val requestId: String? = null,
    ) : ApiError

    companion object {
        const val CODE_DEVICE_BANNED = "DEVICE_BANNED"
    }
}

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

@Serializable
internal data class ErrorEnvelope(val error: ErrorBody)

@Serializable
internal data class ErrorBody(
    val code: String,
    val message: String,
    @SerialName("request_id") val requestId: String? = null,
    val details: JsonObject? = null,
)

/**
 * 把一次失败的 HTTP 响应映射成结构化错误。
 *
 * 请求 ID 优先取错误体里的值，回落响应头；`retryAfterSeconds` 先看 `Retry-After` 头，
 * 再看 `details.retry_after`，都没有就留给 [RetryPolicy] 兜底 3 秒。
 */
internal fun mapHttpError(
    status: Int,
    rawBody: String,
    headerRequestId: String?,
    retryAfterHeader: String?,
): ApiError {
    val body = runCatching { ApiJson.decodeFromString<ErrorEnvelope>(rawBody) }
        .getOrNull()
        ?.error

    val requestId = body?.requestId ?: headerRequestId
    if (status == 403 && body?.code == ApiError.CODE_DEVICE_BANNED) {
        return ApiError.Banned(requestId)
    }

    val retryAfter = retryAfterHeader?.trim()?.toLongOrNull()
        ?: body?.details?.get("retry_after")?.jsonPrimitive?.contentOrNull?.toLongOrNull()

    return ApiError.Http(
        status = status,
        code = body?.code ?: "HTTP_$status",
        message = body?.message?.takeIf { it.isNotBlank() } ?: defaultErrorMessage(status),
        requestId = requestId,
        retryAfterSeconds = retryAfter,
        details = body?.details,
    )
}

private fun defaultErrorMessage(status: Int): String = when {
    status == 401 -> "登录状态失效，请重新注册"
    status == 403 -> "该设备已被禁用"
    status == 404 -> "接口不存在"
    status == 413 -> "请求内容过大"
    status == 429 -> "请求过于频繁，请稍后重试"
    status >= 500 -> "服务暂时不可用，请稍后重试"
    else -> "请求失败（HTTP $status）"
}
