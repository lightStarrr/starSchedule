package com.star.schedule.autoupdate

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object TimetableAutoUpdateTypes {
    const val QIANGZHI_JW = "qiangzhi_jw"

    // legacy type kept for compatibility with old saved data
    const val LIDA_JW = "lida_jw"

    private val supportedTypes: Set<String> = setOf(QIANGZHI_JW, LIDA_JW)

    fun isSupported(type: String?): Boolean = !type.isNullOrBlank() && supportedTypes.contains(type)
}

@Serializable
sealed class TimetableAutoUpdateConfig

@Serializable
@SerialName(TimetableAutoUpdateTypes.LIDA_JW)
// legacy config kept for compatibility with old saved data
data class LidaJwAutoUpdateConfig(
    val account: String,
    val password: String
) : TimetableAutoUpdateConfig()

@Serializable
@SerialName(TimetableAutoUpdateTypes.QIANGZHI_JW)
data class QiangzhiJwAutoUpdateConfig(
    val baseUrl: String,
    val account: String,
    val password: String
) : TimetableAutoUpdateConfig()

object TimetableAutoUpdateJson {
    @PublishedApi
    internal val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        classDiscriminator = "type"
        encodeDefaults = true
    }

    fun getType(configJson: String?): String? {
        if (configJson.isNullOrBlank()) return null
        return runCatching {
            json.parseToJsonElement(configJson).jsonObject["type"]?.jsonPrimitive?.content
        }.getOrNull()
    }

    fun isSupported(configJson: String?): Boolean = TimetableAutoUpdateTypes.isSupported(getType(configJson))

    fun decode(configJson: String?): TimetableAutoUpdateConfig? {
        if (configJson.isNullOrBlank()) return null
        return runCatching {
            json.decodeFromString(TimetableAutoUpdateConfig.serializer(), configJson)
        }.getOrNull()
    }

    inline fun <reified T : TimetableAutoUpdateConfig> decodeAs(configJson: String?): T? {
        if (configJson.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<T>(configJson) }.getOrNull()
    }

    fun encode(config: TimetableAutoUpdateConfig): String =
        json.encodeToString(TimetableAutoUpdateConfig.serializer(), config)
}
