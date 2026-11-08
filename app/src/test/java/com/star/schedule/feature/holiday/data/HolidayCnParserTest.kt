package com.star.schedule.feature.holiday.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HolidayCnParserTest {

    private fun body(vararg days: String, year: Int = 2026): String {
        val dayJson = days.joinToString(",")
        return """{"year": $year, "days": [$dayJson]}"""
    }

    private fun day(name: String, date: String, isOff: Boolean): String =
        """{"name": "$name", "date": "$date", "isOffDay": $isOff}"""

    @Test
    fun `parses holiday days including adjusted workdays`() {
        val result = HolidayCnParser.parseYear(
            body(
                day("元旦", "2026-01-01", true),
                day("春节", "2026-02-14", false),
            ),
            expectedYear = 2026,
        )

        assertEquals(2, result.size)
        assertEquals(LocalDate.of(2026, 1, 1), result[0].date)
        assertEquals("元旦", result[0].name)
        assertTrue(result[0].isOffDay)
        assertFalse(result[1].isOffDay)
    }

    @Test
    fun `rejects year mismatch between file and request`() {
        try {
            HolidayCnParser.parseYear(
                body(day("元旦", "2026-01-01", true), year = 2026),
                expectedYear = 2027,
            )
            fail("expected MalformedHolidayData")
        } catch (expected: HolidayCnParser.MalformedHolidayData) {
        }
    }

    @Test
    fun `rejects day outside the requested year`() {
        try {
            HolidayCnParser.parseYear(
                body(day("元旦", "2025-12-31", true)),
                expectedYear = 2026,
            )
            fail("expected MalformedHolidayData")
        } catch (expected: HolidayCnParser.MalformedHolidayData) {
        }
    }

    @Test
    fun `rejects malformed json`() {
        try {
            HolidayCnParser.parseYear("not json", expectedYear = 2026)
            fail("expected MalformedHolidayData")
        } catch (expected: HolidayCnParser.MalformedHolidayData) {
        }
    }

    @Test
    fun `rejects empty days`() {
        try {
            HolidayCnParser.parseYear(body(), expectedYear = 2026)
            fail("expected MalformedHolidayData")
        } catch (expected: HolidayCnParser.MalformedHolidayData) {
        }
    }

    @Test
    fun `keeps combined holiday names as-is`() {
        val result = HolidayCnParser.parseYear(
            body(day("国庆节、中秋节", "2025-10-01", true), year = 2025),
            expectedYear = 2025,
        )

        assertEquals("国庆节、中秋节", result.single().name)
    }

    private fun jiejiariapiBody(vararg entries: String): String {
        return "{" + entries.joinToString(",") + "}"
    }

    private fun jiejiariapiEntry(date: String, name: String, isOff: Boolean): String =
        """"$date": {"date": "$date", "name": "$name", "isOffDay": $isOff}"""

    @Test
    fun `parses jiejiariapi date-map format`() {
        val result = HolidayCnParser.parseYear(
            jiejiariapiBody(
                // 2026-02-15 是周日，放假；2026-02-14 是周六，调休上班
                jiejiariapiEntry("2026-02-14", "春节", false),
                jiejiariapiEntry("2026-02-15", "春节", true),
            ),
            expectedYear = 2026,
        )

        assertEquals(2, result.size)
        assertFalse(result[0].isOffDay)
        assertTrue(result[1].isOffDay)
    }

    @Test
    fun `jiejiariapi weekday informational entries are dropped`() {
        // 2026-02-10 是周二的北小年：isOffDay=false 但不是调休，只是信息条目，应丢弃
        val result = HolidayCnParser.parseYear(
            jiejiariapiBody(
                jiejiariapiEntry("2026-02-10", "北小年", false),
                jiejiariapiEntry("2026-02-14", "春节", false),
                jiejiariapiEntry("2026-02-15", "春节", true),
            ),
            expectedYear = 2026,
        )

        assertEquals(2, result.size)
        assertTrue(result.none { it.name == "北小年" })
    }

    @Test
    fun `rejects jiejiariapi entries outside the year`() {
        try {
            HolidayCnParser.parseYear(
                jiejiariapiBody(jiejiariapiEntry("2027-01-01", "元旦", true)),
                expectedYear = 2026,
            )
            fail("expected MalformedHolidayData")
        } catch (expected: HolidayCnParser.MalformedHolidayData) {
        }
    }
}
