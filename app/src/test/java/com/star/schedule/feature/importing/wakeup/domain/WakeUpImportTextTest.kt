package com.star.schedule.feature.importing.wakeup.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.format.DateTimeParseException

class WakeUpImportTextTest {
    @Test
    fun extractsKeyFromTheStandardShareText() {
        val message = """
            这是来自「WakeUp课程表」的课表分享，30分钟内有效哦，如果失效请朋友再分享一遍叭。
            为了保护隐私我们选择不监听你的剪贴板，请复制这条消息后，打开App的主界面，
            右上角第二个按钮 -> 从分享口令导入，按操作提示即可完成导入~分享口令为「a1b2c3d4e5f6a7b8」
        """.trimIndent()

        assertEquals("a1b2c3d4e5f6a7b8", extractKeyFromShareText(message))
    }

    @Test
    fun returnsEmptyStringWhenShareKeyIsMissing() {
        assertEquals("", extractKeyFromShareText("没有分享口令"))
    }

    @Test
    fun acceptsBareShareKey() {
        assertEquals("a1b2c3d4e5f6a7b8", extractKeyFromShareText("  a1b2c3d4e5f6a7b8\n"))
    }

    /** 6.1.70 起口令不再保证是全小写十六进制，大小写和 - _ 都要原样保留。 */
    @Test
    fun preservesCaseAndSeparatorsInShareKey() {
        assertEquals("AbC-9_x2Qw", extractKeyFromShareText("分享口令为「AbC-9_x2Qw」"))
    }

    @Test
    fun prefersQuotedKeyOverOtherCandidates() {
        assertEquals(
            "a1b2c3d4e5f6a7b8",
            extractKeyFromShareText("课表 id 为 0123456789abcdef，分享口令为「a1b2c3d4e5f6a7b8」"),
        )
    }

    @Test
    fun ignoresTooShortCandidates() {
        assertEquals("", extractKeyFromShareText("分享口令为「a1b2c3」"))
    }

    @Test
    fun padsWakeUpDateComponents() {
        assertEquals("2026-09-02", parseWakeUpDate("2026-9-2"))
    }

    @Test
    fun acceptsSlashSeparatedWakeUpDate() {
        assertEquals("2026-08-31", parseWakeUpDate("2026/8/31"))
    }

    @Test
    fun rejectsInvalidWakeUpDate() {
        assertThrows(DateTimeParseException::class.java) {
            parseWakeUpDate("2026-13-40")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseWakeUpDate("2026年9月2日")
        }
    }
}
