package com.star.schedule.core.network

/**
 * 客户端档案：请求头与上报载荷共用同一份值，避免各处自行拼装导致口径不一致。
 *
 * 这里刻意不碰任何 Android API，构造好后是纯数据，可直接在 JVM 单测里断言。
 */
data class ClientInfo(
    val versionName: String,
    val versionCode: Int,
    val osVersion: String,
    val sdkInt: Int,
    val arch: String,
    val locale: String,
    val timezone: String,
) {
    /** 服务端 app_version 限 32 字符，超出会被拒绝。 */
    val normalizedVersionName: String get() = versionName.take(32)

    /** 形如 `StarSchedule-Android/1.2.0 (Android 14; sdk 34; arm64-v8a)`。 */
    val userAgent: String
        get() = buildString {
            append("StarSchedule-Android/")
            append(normalizedVersionName)
            append(" (Android ")
            append(osVersion.ifBlank { "unknown" })
            append("; sdk ")
            append(sdkInt)
            if (arch.isNotBlank()) {
                append("; ")
                append(arch)
            }
            append(')')
        }
}
