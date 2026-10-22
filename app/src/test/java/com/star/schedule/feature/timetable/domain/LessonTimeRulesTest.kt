package com.star.schedule.feature.timetable.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonTimeRulesTest {

    @Test
    fun parsesTwoDigitTime() {
        assertEquals(8 * 60, LessonTimeRules.parseMinutes("08:00"))
        assertEquals(8 * 60 + 45, LessonTimeRules.parseMinutes("08:45"))
        assertEquals(23 * 60 + 59, LessonTimeRules.parseMinutes("23:59"))
    }

    @Test
    fun parsesSingleDigitHourAndTrimsSpaces() {
        assertEquals(8 * 60 + 5, LessonTimeRules.parseMinutes(" 8:05 "))
    }

    @Test
    fun rejectsInvalidTimeText() {
        assertNull(LessonTimeRules.parseMinutes(""))
        assertNull(LessonTimeRules.parseMinutes("8点"))
        assertNull(LessonTimeRules.parseMinutes("24:00"))
        assertNull(LessonTimeRules.parseMinutes("08:60"))
        assertNull(LessonTimeRules.parseMinutes("08:0"))
        assertNull(LessonTimeRules.parseMinutes("08:00:00"))
    }

    @Test
    fun formatsMinutesWithTwoDigits() {
        assertEquals("08:05", LessonTimeRules.formatMinutes(8 * 60 + 5))
        assertEquals("00:00", LessonTimeRules.formatMinutes(0))
        assertEquals("23:59", LessonTimeRules.formatMinutes(24 * 60 - 1))
    }

    @Test
    fun formatsOutOfRangeMinutesToDayBounds() {
        assertEquals("00:00", LessonTimeRules.formatMinutes(-30))
        assertEquals("23:59", LessonTimeRules.formatMinutes(24 * 60 + 100))
    }

    @Test
    fun detectsRealOverlap() {
        val others = listOf(8 * 60..8 * 60 + 45)

        assertTrue(LessonTimeRules.overlaps(8 * 60 + 30, 9 * 60 + 15, others))
        assertTrue(LessonTimeRules.overlaps(7 * 60, 8 * 60 + 1, others))
        assertTrue(LessonTimeRules.overlaps(8 * 60 + 10, 8 * 60 + 20, others))
    }

    @Test
    fun allowsTouchingSpans() {
        val others = listOf(8 * 60..8 * 60 + 45)

        assertFalse(LessonTimeRules.overlaps(8 * 60 + 45, 9 * 60 + 30, others))
        assertFalse(LessonTimeRules.overlaps(7 * 60, 8 * 60, others))
    }

    @Test
    fun ignoresSpansWithoutOthers() {
        assertFalse(LessonTimeRules.overlaps(8 * 60, 8 * 60 + 45, emptyList()))
    }

    @Test
    fun computesGapBetweenPeriods() {
        assertEquals(5, LessonTimeRules.gapMinutes(8 * 60 + 45, 8 * 60 + 50))
        assertEquals(0, LessonTimeRules.gapMinutes(8 * 60 + 45, 8 * 60 + 45))
        assertEquals(-10, LessonTimeRules.gapMinutes(8 * 60 + 45, 8 * 60 + 35))
    }
}
