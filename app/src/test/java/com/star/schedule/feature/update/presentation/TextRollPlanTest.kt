package com.star.schedule.feature.update.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextRollPlanTest {

    private fun plan(old: String, new: String): TextRollPlan =
        planTextRoll(old.rollGlyphs(), new.rollGlyphs())

    private fun keptOldSet(old: String, new: String) =
        plan(old, new).kept.map { it.first }.toSet()

    @Test
    fun `whole change keeps nothing`() {
        assertTrue(plan("检查更新", "正在检查更新…").kept.isEmpty())
    }

    @Test
    fun `partial roll keeps shared suffix`() {
        assertEquals(
            listOf(2 to 2, 3 to 3),
            plan("下载更新", "检查更新").kept,
        )
    }

    @Test
    fun `progress label change rolls a single glyph`() {
        val kept = keptOldSet("已下载 12.3 MB / 45.6 MB", "已下载 12.4 MB / 45.6 MB")
        val old = "已下载 12.3 MB / 45.6 MB".rollGlyphs()
        assertEquals(old.size - 1, kept.size)
        assertFalse(kept.contains(old.indexOf("3")))
    }

    @Test
    fun `separator stays while digits roll`() {
        val old = "已下载 1.2 MB / 48 MB".rollGlyphs()
        val plan = plan("已下载 1.2 MB / 48 MB", "已下载 2.5 MB / 48 MB")
        val dotIdx = old.indexOf(".")
        assertTrue(plan.kept.contains(dotIdx to dotIdx))
        val keptOld = plan.kept.map { it.first }.toSet()
        assertFalse(keptOld.contains(old.indexOf("1")))
        assertFalse(keptOld.contains(old.indexOf("2")))
    }

    @Test
    fun `separator survives digit width growth`() {
        val old = "9.9 MB".rollGlyphs()
        val new = "10.0 MB".rollGlyphs()
        val plan = plan("9.9 MB", "10.0 MB")
        assertTrue(plan.kept.contains(old.indexOf(".") to new.indexOf(".")))
        val keptOld = plan.kept.map { it.first }.toSet()
        assertTrue(keptOld.contains(old.indexOf(" ")))
        assertTrue(keptOld.contains(old.indexOf("M")))
        assertTrue(keptOld.contains(old.indexOf("B")))
    }

    @Test
    fun `both dots survive version roll`() {
        val old = "1.9.9".rollGlyphs()
        val new = "2.0.0".rollGlyphs()
        val plan = plan("1.9.9", "2.0.0")
        assertEquals(
            listOf(1 to 1, 3 to 3),
            plan.kept,
        )
    }

    @Test
    fun `only middle digit rolls in version bump`() {
        val plan = plan("1.2.0", "1.3.0")
        val keptOld = plan.kept.map { it.first }.toSet()
        assertEquals(4, plan.kept.size)
        assertFalse(keptOld.contains(2))
    }

    @Test
    fun `identical text keeps every glyph`() {
        assertEquals(
            (0 until 4).map { it to it },
            plan("检查更新", "检查更新").kept,
        )
    }

    @Test
    fun `surrogate pair counts as one glyph`() {
        assertEquals(3, "a😀b".rollGlyphs().size)
        assertEquals(listOf(0 to 0), plan("😀a", "😀b").kept)
    }

    @Test
    fun `stagger delays stay within bounds`() {
        assertEquals(emptyList<Long>(), rollStaggerDelays(0))
        assertEquals(listOf(0L), rollStaggerDelays(1))
        val five = rollStaggerDelays(5)
        assertEquals(120L, five.last())
        assertTrue(five.zipWithNext { a, b -> b - a }.all { it <= 30L })
        val twenty = rollStaggerDelays(20)
        assertTrue(twenty.last() <= 200L)
        assertTrue(twenty.zipWithNext { a, b -> b - a }.all { it in 1..30L })
    }
}
