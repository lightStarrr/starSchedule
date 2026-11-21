package com.star.schedule.feature.importing.sleepdown.data

import com.star.schedule.feature.importing.sleepdown.domain.SleepDownProtocolException
import com.star.schedule.feature.importing.sleepdown.domain.SleepDownServiceBusyException
import com.star.schedule.feature.importing.sleepdown.domain.SleepDownShareRejectedException
import com.star.schedule.feature.importing.sleepdown.domain.SleepDownShareSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 用 SleepDown 2.0 短口令换取 canonical 课表 JSON。
 *
 * `POST {baseUrl}/api/v1/short-codes/resolve`，body `{"code":"..."}`，
 * 回包 `{"payload":"<课表 JSON>","expiresAt":<epoch 秒>}`，这里只把 payload 交出去。
 * 口令是敏感凭证：不跟重定向、不进日志、回包按上限截断读。
 */
class OkHttpSleepDownShareSource(
    client: OkHttpClient = defaultClient(),
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val json: Json = Json,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SleepDownShareSource {
    // 注入的 client 也可能开着重定向，一律强制关掉。
    private val client = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override suspend fun fetch(code: String): String = withContext(ioDispatcher) {
        val body = buildJsonObject { put("code", code) }.toString()
        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/api/v1/short-codes/resolve")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> Unit
                response.code == 404 -> throw SleepDownShareRejectedException(
                    "SleepDown share code rejected: 404",
                )
                response.code == 429 || response.code == 503 -> throw SleepDownServiceBusyException(
                    "SleepDown service busy: ${response.code}",
                )
                else -> throw SleepDownProtocolException(
                    "SleepDown resolve failed: ${response.code}",
                )
            }

            val responseBody = response.body
                ?: throw SleepDownProtocolException("SleepDown resolve response has no body")
            val bytes = responseBody.byteStream().use { it.readNBytes(MAX_BODY_BYTES + 1) }
            if (bytes.size > MAX_BODY_BYTES) {
                throw SleepDownProtocolException("SleepDown resolve response too large")
            }
            extractPayload(bytes.decodeToString())
        }
    }

    private fun extractPayload(body: String): String {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (exception: SerializationException) {
            throw SleepDownProtocolException("SleepDown resolve response is not JSON", exception)
        } catch (exception: IllegalArgumentException) {
            throw SleepDownProtocolException("SleepDown resolve response is not an object", exception)
        }
        return (root["payload"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: throw SleepDownProtocolException("SleepDown resolve response has no payload")
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.sleepdownschedule.cn"
        const val MAX_BODY_BYTES = 64 * 1024

        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }
}
