package com.star.schedule.core.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * 从响应里捞两个客户端必须处理的头。
 *
 * 这里只能在 OkHttp 的线程上同步执行，所以回调必须是非阻塞的
 * （实现里就是 `scope.launch(IO) { … }`），否则会把连接池线程占住。
 */
class ResponseMetadataInterceptor(
    private val serverClock: ServerClock,
    private val onRefreshedToken: (String) -> Unit,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        serverClock.updateFromHeader(response.header(HEADER_SERVER_TIME))
        response.header(HEADER_REFRESH_TOKEN)
            ?.takeIf { it.isNotBlank() }
            ?.let(onRefreshedToken)
        return response
    }

    companion object {
        const val HEADER_SERVER_TIME = "X-Server-Time"
        const val HEADER_REFRESH_TOKEN = "X-Refresh-Token"
    }
}
