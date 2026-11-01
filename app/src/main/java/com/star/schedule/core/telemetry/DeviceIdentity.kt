package com.star.schedule.core.telemetry

/**
 * 设备标识。
 *
 * `ANDROID_ID` 在少数 ROM 上会是空串或全 0，服务端要求 8–64 位，所以不合法时回落到
 * `install_id`——它本身就满足长度要求，也不会因为重启而变化。
 */
class DeviceIdentity(
    private val androidIdProvider: () -> String?,
    private val installIdProvider: suspend () -> String,
) {
    suspend fun androidId(): String {
        val raw = runCatching { androidIdProvider() }.getOrNull()?.trim().orEmpty()
        return if (raw.length in VALID_RANGE) raw else installIdProvider()
    }

    private companion object {
        val VALID_RANGE = 8..64
    }
}
