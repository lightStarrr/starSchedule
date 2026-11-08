package com.star.schedule.feature.timetable.domain

import com.star.schedule.feature.holiday.domain.HolidayDay
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SemesterStartSuggestionTest {

    private fun day(date: String, name: String, off: Boolean) =
        HolidayDay(LocalDate.parse(date), name, off)

    private val holidays2026 = listOf(
        day("2026-02-14", "春节", false),
        day("2026-02-15", "春节", true),
        day("2026-02-16", "除夕", true),
        day("2026-02-17", "春节", true),
        day("2026-02-23", "春节", true),
        day("2026-02-28", "春节", false),
    )

    @Test
    fun `spring start is the monday after the last spring festival off day`() {
        // 2026 春节假最后一天是 2/23（周一），其后第一个周一是 3/2
        assertEquals(
            LocalDate.of(2026, 3, 2),
            SemesterStartSuggestion.springStart(2026, holidays2026),
        )
    }

    @Test
    fun `spring start ignores festival names other than chunjie`() {
        val holidays = listOf(
            day("2026-10-07", "国庆节", true),
            day("2026-02-23", "春节", true),
        )
        assertEquals(
            LocalDate.of(2026, 3, 2),
            SemesterStartSuggestion.springStart(2026, holidays),
        )
    }

    @Test
    fun `spring start falls back to last monday of february without data`() {
        // 2027 没有节假日数据：2 月最后一个周一是 2/22
        assertEquals(
            LocalDate.of(2027, 2, 22),
            SemesterStartSuggestion.springStart(2027, emptyList()),
        )
    }

    @Test
    fun `suggest picks fall semester around september`() {
        // 8 月底创建：最近的开学日是当年 9/1
        val suggested = SemesterStartSuggestion.suggest(
            today = LocalDate.of(2026, 8, 25),
        ) { year -> if (year == 2026) holidays2026 else emptyList() }

        assertEquals(LocalDate.of(2026, 9, 1), suggested)
    }

    @Test
    fun `suggest picks next spring near year end`() {
        // 12 月中创建：明年春节假推算出的开学日比今年 9/1 更近
        val holidays2027 = listOf(
            day("2027-02-11", "春节", true),
        )
        val suggested = SemesterStartSuggestion.suggest(
            today = LocalDate.of(2026, 12, 15),
        ) { year -> if (year == 2027) holidays2027 else holidays2026 }

        // 2027 春节假最后一天 2/11（周四）→ 下一周一 2/15
        assertEquals(LocalDate.of(2027, 2, 15), suggested)
    }

    @Test
    fun `suggest picks spring during spring months`() {
        val suggested = SemesterStartSuggestion.suggest(
            today = LocalDate.of(2026, 3, 10),
        ) { year -> if (year == 2026) holidays2026 else emptyList() }

        assertEquals(LocalDate.of(2026, 3, 2), suggested)
    }
}
