package com.star.schedule.feature.schedule.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SemesterWeekTest {
    private val semesterStart = LocalDate.of(2026, 9, 21)

    @Test
    fun returnsFirstWeekForFirstSevenDays() {
        assertEquals(1, semesterStart.getWeekOfSemester(semesterStart))
        assertEquals(1, semesterStart.plusDays(6).getWeekOfSemester(semesterStart))
    }

    @Test
    fun advancesWeekEverySevenDays() {
        assertEquals(2, semesterStart.plusDays(7).getWeekOfSemester(semesterStart))
        assertEquals(3, semesterStart.plusDays(14).getWeekOfSemester(semesterStart))
    }
}
