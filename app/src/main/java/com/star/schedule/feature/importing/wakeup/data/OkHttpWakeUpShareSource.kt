package com.star.schedule.feature.importing.wakeup.data

import com.star.schedule.feature.importing.wakeup.data.protocol.WakeUpDevice
import com.star.schedule.feature.importing.wakeup.data.protocol.WakeUpProtocol
import com.star.schedule.feature.importing.wakeup.domain.WakeUpProtocolException
import com.star.schedule.feature.importing.wakeup.domain.WakeUpShareSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 用 6.1.70 客户端的两段式握手换取分享数据。
 *
 * 没有 WebView：OkHttp 没有同源限制，请求头、顺序、超时都能自己控，
 * 加密与签名逻辑也能在 JVM 单测里跑（WebView 方案做不到）。
 */
class OkHttpWakeUpShareSource(
    private val client: OkHttpClient = defaultClient(),
    private val device: WakeUpDevice = WakeUpDevice(),
) : WakeUpShareSource {
    override suspend fun fetch(key: String): String = withContext(Dispatchers.IO) {
        val common = device.commonParams()
        val (signA, rand10) = WakeUpProtocol.makeSignA(device.cuid)

        val antispam = post(
            WakeUpProtocol.antispamUrl,
            WakeUpProtocol.antispamBody(signA, common),
        )
        val signB = WakeUpProtocol.extractSignB(antispam)
            ?: throw WakeUpProtocolException("WakeUp antispam response has no sign")
        val token = WakeUpProtocol.tokenFromSignB(signB, rand10)

        val request = WakeUpProtocol.buildShareRequest(key, token, device, common)
        WakeUpProtocol.unwrapShareData(post(WakeUpProtocol.shareUrl, request.body), request.rc4Key)
    }

    private fun post(url: String, body: String): String {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(FORM_MEDIA_TYPE))
            .header("na__zyb_source__", "wakeup")
            .header("zyb-cuid", device.cuid)
            .header("zyb-adid", device.adid)
            .apply { if (device.did.isNotEmpty()) header("zyb-did", device.did) }
            .build()

        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            // 非 2xx 说明这两个私有接口搬走或改版了，不是口令问题。
            if (!response.isSuccessful) {
                throw WakeUpProtocolException("WakeUp request failed: ${response.code} ${response.message}")
            }
            return payload
        }
    }

    private companion object {
        val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded; charset=UTF-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
