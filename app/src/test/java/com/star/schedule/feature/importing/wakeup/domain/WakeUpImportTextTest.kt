package com.star.schedule.feature.importing.wakeup.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.format.DateTimeParseException

class WakeUpImportTextTest {
    @Test
    fun extractsLowercaseShareKey() {
        assertEquals(
            "a1b2c3",
            extractKeyFromShareText("这是WakeUp分享，分享口令为「a1b2c3」请复制使用"),
        )
    }

    @Test
    fun returnsEmptyStringWhenShareKeyIsMissing() {
        assertEquals("", extractKeyFromShareText("没有分享口令"))
    }

    @Test
    fun acceptsBareShareKey() {
        assertEquals("a1b2c3d4e5f6a7b8", extractKeyFromShareText("  a1b2c3d4e5f6a7b8\n"))
    }

    @Test
    fun normalizesUppercaseShareKey() {
        assertEquals("a1b2c3d4e5f6a7b8", extractKeyFromShareText("A1B2C3D4E5F6A7B8"))
    }

    @Test
    fun prefersQuotedKeyOverOtherHexRuns() {
        assertEquals(
            "a1b2c3",
            extractKeyFromShareText("课表 id 为 0123456789abcdef，分享口令为「a1b2c3」"),
        )
    }

    @Test
    fun padsWakeUpDateComponents() {
        assertEquals("2026-09-02", parseWakeUpDate("2026-9-2"))
    }

    @Test
    fun rejectsInvalidWakeUpDate() {
        assertThrows(IllegalArgumentException::class.java) {
            parseWakeUpDate("2026/9/2")
        }
        assertThrows(DateTimeParseException::class.java) {
            parseWakeUpDate("2026-13-40")
        }
    }
}
