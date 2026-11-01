package com.star.schedule.core.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * 用拦截器冒充传输层：不引入 MockWebServer，纯 JVM 就能跑完整请求链路。
 */
internal fun fakeTransport(handler: (Request) -> Response): Interceptor =
    Interceptor { chain -> handler(chain.request()) }

internal fun responseOf(
    request: Request,
    code: Int,
    body: String = "",
    headers: Map<String, String> = emptyMap(),
): Response = Response.Builder()
    .request(request)
    .protocol(Protocol.HTTP_1_1)
    .code(code)
    .message(if (code in 200..299) "OK" else "Error")
    .apply { headers.forEach { (name, value) -> header(name, value) } }
    .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
    .build()

internal fun errorBody(
    code: String,
    message: String = "出错了",
    requestId: String? = null,
    details: String? = null,
): String {
    val fields = mutableListOf("\"code\":\"$code\"", "\"message\":\"$message\"")
    if (requestId != null) fields += "\"request_id\":\"$requestId\""
    if (details != null) fields += "\"details\":$details"
    return "{\"error\":{${fields.joinToString(",")}}}"
}

internal val testClientInfo = ClientInfo(
    versionName = "1.2.0",
    versionCode = 120,
    osVersion = "14",
    sdkInt = 34,
    arch = "arm64-v8a",
    locale = "zh-CN",
    timezone = "Asia/Shanghai",
)

internal const val TEST_BASE_URL = "http://127.0.0.1:8080/api/v1/"
