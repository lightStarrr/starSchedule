package com.star.schedule.feature.update.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextRollPlanTest {

    private fun plan(old: String, new: String): TextRollPlan =
        planTextRoll(old.rollGlyphs(), new.rollGlyphs())

    @Test
    fun `whole change when nothing overlaps`() {
        val plan = plan("检查更新", "正在检查更新…")
        assertTrue(plan.wholeChange)
        assertEquals(0, plan.prefixLength)
        assertEquals(0, plan.suffixLength)
    }

    @Test
    fun `partial roll keeps shared suffix`() {
        val plan = plan("下载更新", "检查更新")
        assertFalse(plan.wholeChange)
        assertEquals(0, plan.prefixLength)
        assertEquals(2, plan.suffixLength)
    }

    @Test
    fun `progress label change rolls a single glyph`() {
        val plan = plan("已下载 12.3 MB / 45.6 MB", "已下载 12.4 MB / 45.6 MB")
        assertFalse(plan.wholeChange)
        val oldSize = "已下载 12.3 MB / 45.6 MB".rollGlyphs().size
        val newSize = "已下载 12.4 MB / 45.6 MB".rollGlyphs().size
        assertEquals(oldSize - plan.prefixLength - plan.suffixLength, 1)
        assertEquals(newSize - plan.prefixLength - plan.suffixLength, 1)
    }

    @Test
    fun `width growth splits prefix and suffix correctly`() {
        val plan = plan("9.9 MB", "10.0 MB")
        assertFalse(plan.wholeChange)
        assertEquals(0, plan.prefixLength)
        assertEquals(3, plan.suffixLength)
    }

    @Test
    fun `identical text keeps every glyph`() {
        val plan = plan("检查更新", "检查更新")
        assertFalse(plan.wholeChange)
        assertEquals(4, plan.prefixLength)
        assertEquals(0, plan.suffixLength)
    }

    @Test
    fun `surrogate pair counts as one glyph`() {
        assertEquals(3, "a😀b".rollGlyphs().size)
        val plan = plan("😀a", "😀b")
        assertFalse(plan.wholeChange)
        assertEquals(1, plan.prefixLength)
        assertEquals(0, plan.suffixLength)
    }

    @Test
    fun `stagger delays stay within bounds`() {
        assertEquals(emptyList<Long>(), rollStaggerDelays(0))
        assertEquals(listOf(0L), rollStaggerDelays(1))
        val five = rollStaggerDelays(5)
        assertEquals(5, five.size)
        assertEquals(120L, five.last())
        assertTrue(five.zipWithNext { a, b -> b - a }.all { it <= 30L })
        val twenty = rollStaggerDelays(20)
        assertEquals(20, twenty.size)
        assertTrue(twenty.last() <= 200L)
        assertTrue(twenty.zipWithNext { a, b -> b - a }.all { it in 1..30L })
    }
}
