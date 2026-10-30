package com.star.schedule.feature.timetable.domain

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模板列表里的「最后更新时间」：
 * 只锁「按给定时区把时刻排成日期时间」这件事，具体分隔符交给系统格式，不写死。
 */
class LessonTimeTemplateUpdatedAtTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun `formats the timestamp with the given zone`() {
        val millis = LocalDateTime.of(2026, 10, 30, 14, 5)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val text = formatTemplateUpdatedAt(millis, zone = zone, locale = Locale.CHINA)

        assertTrue(text.contains("2026"))
        assertTrue(text.contains("14:05"))
    }

    @Test
    fun `the same instant shows a different wall clock in another zone`() {
        val millis = LocalDateTime.of(2026, 10, 30, 14, 5)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

        val utc = formatTemplateUpdatedAt(millis, zone = ZoneId.of("UTC"), locale = Locale.CHINA)

        assertTrue(utc.contains("06:05"))
    }
}
