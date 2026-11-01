package com.star.schedule.core.network

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 用响应头 `X-Server-Time` 校正本地时钟。
 *
 * 设备时间可能被手动改过，而服务端对 `launched_at` 有「偏差超 7 天就丢弃」的规则，
 * 所以上报时间一律用校正后的时间，而不是 `System.currentTimeMillis()`。
 */
class ServerClock(
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var offsetMillis: Long = 0L

    val currentOffsetMillis: Long get() = offsetMillis

    /** 解析失败只是忽略这一次校正，绝不影响请求本身。 */
    fun updateFromHeader(value: String?) {
        val serverMillis = parseServerTimeMillis(value) ?: return
        offsetMillis = serverMillis - wallClockMillis()
    }

    fun nowUtc(): Instant = Instant.ofEpochMilli(wallClockMillis() + offsetMillis)

    /**
     * 服务端用的是 `time.RFC3339`（没有毫秒），这里保持一致，
     * 免得同一时刻出现 `...:19.123Z` 与 `...:19Z` 两种写法。
     */
    fun nowUtcRfc3339(): String =
        DateTimeFormatter.ISO_INSTANT.format(nowUtc().truncatedTo(ChronoUnit.SECONDS))
}

internal fun parseServerTimeMillis(value: String?): Long? {
    val raw = value?.trim().orEmpty()
    if (raw.isEmpty()) return null
    return runCatching {
        OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant().toEpochMilli()
    }.getOrNull()
}
