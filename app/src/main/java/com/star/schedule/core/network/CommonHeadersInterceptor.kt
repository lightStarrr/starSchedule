package com.star.schedule.core.network

import java.util.UUID
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 补全契约要求的公共请求头。
 *
 * 只补缺失的，不覆盖已有值：重试与重放必须复用同一个 `X-Request-Id`，
 * 那个值是 [ApiClient] 在构造请求时就固定的。
 */
class CommonHeadersInterceptor(
    private val clientInfo: () -> ClientInfo,
    private val requestIdFactory: () -> String = { UUID.randomUUID().toString() },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val info = clientInfo()
        val original = chain.request()
        val builder = original.newBuilder()

        if (original.header(HEADER_USER_AGENT) == null) {
            builder.header(HEADER_USER_AGENT, info.userAgent)
        }
        if (original.header(HEADER_CLIENT_VERSION) == null) {
            builder.header(HEADER_CLIENT_VERSION, info.normalizedVersionName)
        }
        if (original.header(HEADER_CLIENT_VERSION_CODE) == null) {
            builder.header(HEADER_CLIENT_VERSION_CODE, info.versionCode.toString())
        }
        if (original.header(HEADER_REQUEST_ID) == null) {
            builder.header(HEADER_REQUEST_ID, requestIdFactory())
        }
        if (original.header(HEADER_ACCEPT) == null) {
            builder.header(HEADER_ACCEPT, "application/json")
        }
        return chain.proceed(builder.build())
    }

    companion object {
        const val HEADER_USER_AGENT = "User-Agent"
        const val HEADER_CLIENT_VERSION = "X-Client-Version"
        const val HEADER_CLIENT_VERSION_CODE = "X-Client-Version-Code"
        const val HEADER_REQUEST_ID = "X-Request-Id"
        const val HEADER_ACCEPT = "Accept"
    }
}
