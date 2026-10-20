package com.star.schedule.feature.importing.wakeup.data.protocol

import com.star.schedule.feature.importing.wakeup.domain.WakeUpProtocolException
import com.star.schedule.feature.importing.wakeup.domain.WakeUpShareRejectedException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * 「WakeUp 课程表」6.1.70 官方渠道的分享口令协议。
 *
 * 旧版 `i.wakeup.fun/share_schedule/get?key=` 已经被服务端关掉（固定返回
 * `{"status":5000004,"message":"当前版本过低，请升级"}`），现在必须走两段式：
 *
 * 1. `POST /pluto/app/antispam`，带上用私有 DES 加密的 signA，换回加密的 signB；
 * 2. 用 signB 解出 10 位 antispam token，再用 token 派生 RC4 密钥、签出 sign，
 *    `POST /share_schedule/getv2` 取回密文；
 * 3. 用同一把 RC4 密钥解出 `shareData`（5 行 JSON）。
 *
 * 常量与 6.1.70 客户端绑定，对方改版后这里会整体失效——失效时服务端通常返回
 * "版本过低"或校验错误，此时应提示"暂不支持"，而不是"口令无效"。
 */
internal object WakeUpProtocol {
    const val HOST = "https://api.wakeup.fun"
    const val ANTISPAM_PATH = "/pluto/app/antispam"
    const val SHARE_PATH = "/share_schedule/getv2"

    const val PACKAGE_NAME = "com.suda.yzune.wakeupschedule"
    const val VERSION_NAME = "6.1.70"
    const val VERSION_CODE = 450
    const val CHANNEL = "100271a"
    const val PUBLIC_TOKEN = "1_XPXQH3c5HRPtFHkSwi3sCCURmT25QfxM"
    private const val SIGNATURE_MD5 = "318c6d4f74655d4f032fb0466bcfdfbc"
    private const val MAGIC = "8&%d*"
    private const val SIGN_A_KEY = "@fG2SuLA"
    private const val KEY_SALT = "@#AIjd83#@6B"

    private val json = Json { ignoreUnknownKeys = true }

    val antispamUrl: String get() = HOST + ANTISPAM_PATH

    val shareUrl: String get() = HOST + SHARE_PATH

    class ShareRequest(val body: String, val rc4Key: String)

    /** signA 与它用的随机串必须成对传递：随机串前 5 位就是 signB 的解密密钥。 */
    fun makeSignA(cuid: String): Pair<String, String> {
        val rand10 = WakeUpCrypto.randomToken()
        val plain = "$MAGIC##$rand10##$SIGNATURE_MD5##$cuid"
        return WakeUpCrypto.nativeHexEncode(WakeUpCrypto.desEncrypt(plain.toByteArray(Charsets.UTF_8), SIGN_A_KEY)) to rand10
    }

    fun antispamBody(signA: String, common: List<Pair<String, String>>): String =
        formEncode(listOf("data" to signA) + common) + "&"

    fun tokenFromSignB(signB: String, rand10: String): String {
        val plain = runCatching {
            WakeUpCrypto.desDecrypt(WakeUpCrypto.nativeHexDecode(signB), rand10.take(5) + "#G4")
        }.getOrElse { throw WakeUpProtocolException("WakeUp antispam sign is not decodable", it) }

        // 服务端回的是 Latin-1 文本，不是 UTF-8，按字节直读才不会错位。
        val text = String(plain, Charsets.ISO_8859_1)
        if (text.length < 22 || !text.startsWith(rand10)) {
            throw WakeUpProtocolException("WakeUp antispam token does not match the request")
        }
        return text.substring(12, 22)
    }

    fun rc4Key(token: String): String {
        val head = WakeUpCrypto.md5Hex(KEY_SALT) + WakeUpCrypto.md5Hex(VERSION_CODE.toString())
        val raw = WakeUpCrypto.md5Hex("[$token]@")
        val tail = raw.substring(17).reversed() + raw.substring(15, 17) + raw.substring(0, 15).reversed()
        val chars = (head + tail).toCharArray()

        for (index in 0 until 3) {
            val right = chars.size - 1 - index
            val swap = chars[index]
            chars[index] = chars[right]
            chars[right] = swap
        }

        val extended = String(chars).let { (it + WakeUpCrypto.md5Hex(it)).toCharArray() }
        for (index in 0 until 60) {
            val right = extended.size - 1 - index
            val swap = extended[index]
            extended[index] = extended[right]
            extended[right] = swap
        }
        return String(extended)
    }

    fun buildShareRequest(
        code: String,
        token: String,
        device: WakeUpDevice,
        common: List<Pair<String, String>>,
        nowMillis: Long = System.currentTimeMillis(),
    ): ShareRequest {
        val rc4Key = rc4Key(token)
        val keyBytes = rc4Key.toByteArray(Charsets.UTF_8)
        val encryptedKey = WakeUpCrypto.rc4(
            "key=${WakeUpCrypto.quotePlus(code)}".toByteArray(Charsets.UTF_8),
            keyBytes,
        )
        val data = WakeUpCrypto.base64(encryptedKey)
        val seconds = nowMillis / 1000

        // 签名用未做 URL 编码的原文，且参与排序的键值里带上了 data/nt/_t_/kakorrhaphiophobia。
        val signItems = buildList {
            add("data=$data")
            common.forEach { (key, value) -> add("$key=$value") }
            if (device.did.isNotEmpty()) add("did=${device.did}")
            add("nt=${device.nt}")
            add("_t_=$seconds")
            add("kakorrhaphiophobia=$nowMillis")
        }.sorted().joinToString("")

        val sign = WakeUpCrypto.md5Hex("$MAGIC[${WakeUpCrypto.md5Hex(token)}]@${WakeUpCrypto.base64(signItems.toByteArray(Charsets.UTF_8))}")

        val fields = buildList {
            add("data" to data)
            addAll(common)
            if (device.did.isNotEmpty()) add("did" to device.did)
            add("nt" to device.nt)
        }
        val body = buildString {
            append('&').append(formEncode(fields))
            append("&sign=").append(sign)
            append("&_t_=").append(seconds)
            append("&kakorrhaphiophobia=").append(nowMillis)
        }
        return ShareRequest(body, rc4Key)
    }

    /** antispam 的 signB 可能在 `data` 或 `data.data` 里，两种都认。 */
    fun extractSignB(payload: String): String? {
        val root = runCatching { json.parseToJsonElement(payload) }.getOrNull() as? JsonObject ?: return null
        val data = root["data"]
        if (data is JsonObject) return data["data"]?.jsonPrimitive?.contentOrNull
        if (data is JsonPrimitive && data.isString) return data.contentOrNull
        return (root["result"] as? JsonObject)?.get("data")?.jsonPrimitive?.contentOrNull
    }

    fun unwrapShareData(responseBody: String, rc4Key: String): String {
        val root = runCatching { json.parseToJsonElement(responseBody) }.getOrNull() as? JsonObject
            ?: throw WakeUpProtocolException("WakeUp share response is not JSON")

        val data = root["data"]
        val encrypted = when (data) {
            is JsonObject -> data["data"]?.jsonPrimitive?.contentOrNull
            is JsonPrimitive -> data.contentOrNull
            else -> null
        }

        if (encrypted.isNullOrEmpty()) {
            val code = root["errNo"]?.jsonPrimitive?.intOrNull
            val message = root["errstr"]?.jsonPrimitive?.contentOrNull
            // 口令错了/过期了，或者版本校验没过，服务端都只是给个 errNo，只能按提示区分。
            if (code != null && code != 0 && message != null && VERSION_HINTS.any { message.contains(it) }) {
                throw WakeUpProtocolException("WakeUp version handshake failed: $message")
            }
            throw WakeUpShareRejectedException("WakeUp rejected the share code: errNo=$code $message")
        }

        val decrypted = runCatching {
            WakeUpCrypto.rc4(WakeUpCrypto.unBase64(encrypted), rc4Key.toByteArray(Charsets.UTF_8))
        }.getOrElse { throw WakeUpProtocolException("WakeUp share payload is not decodable", it) }

        val body = runCatching { json.parseToJsonElement(String(decrypted, Charsets.UTF_8)) }.getOrNull() as? JsonObject
            ?: throw WakeUpProtocolException("WakeUp share payload is not JSON")

        return body["shareData"]?.jsonPrimitive?.contentOrNull
            ?: throw WakeUpProtocolException("WakeUp share payload has no shareData")
    }

    private fun formEncode(items: List<Pair<String, String>>): String =
        items.joinToString("&") { (key, value) -> "$key=${WakeUpCrypto.quotePlus(value)}" }

    private val VERSION_HINTS = listOf("版本过低", "请升级", "签名", "sign")
}

/**
 * 协议里的设备字段。WakeUp 要求这些值齐全，否则 antispam 直接拒；
 * 这里刻意用固定值 + 固定 androidId，避免把真实设备指纹带出去。
 */
class WakeUpDevice(
    val androidId: String = "0000000000000000",
    val sdk: Int = 35,
    val model: String = "Pixel 7",
    val brand: String = "google",
    val screenSize: String = "1080x2400",
    val abis: String = "arm64-v8a",
    val appBit: String = "64",
    val appId: String = "wakeup",
    val downloadType: String = "1",
    val network: String = "wifi",
    val province: String = "",
    val city: String = "",
    val area: String = "",
    val deviceId: String = "",
    val operatorId: String = "",
    val did: String = "",
) {
    val cuid: String = WakeUpCrypto.cuidFromAndroidId(androidId)

    val adid: String = WakeUpCrypto.adidFromAndroidId(androidId)

    val nt: String get() = network

    /** 顺序必须和客户端一致：签名虽然会排序，但请求体顺序参与服务端校验。 */
    fun commonParams(): List<Pair<String, String>> = listOf(
        "area" to area,
        "screensize" to screenSize,
        "cuid" to cuid,
        "os" to "android",
        "city" to city,
        "abis" to abis,
        "channel" to WakeUpProtocol.CHANNEL,
        "appBit" to appBit,
        "vc" to WakeUpProtocol.VERSION_CODE.toString(),
        "deviceId" to deviceId,
        "token" to WakeUpProtocol.PUBLIC_TOKEN,
        "adid" to adid,
        "province" to province,
        "pkgName" to WakeUpProtocol.PACKAGE_NAME,
        "appId" to appId,
        "download_type" to downloadType,
        "vcname" to WakeUpProtocol.VERSION_NAME,
        "sdk" to sdk.toString(),
        "device" to model,
        "brand" to brand,
        "operatorid" to operatorId,
    )
}
