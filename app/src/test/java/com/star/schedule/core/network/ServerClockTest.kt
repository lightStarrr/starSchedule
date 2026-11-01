package com.star.schedule.core.network

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerClockTest {

    @Test
    fun `applies offset from server time header`() {
        var local = 1_000_000L
        val clock = ServerClock { local }

        clock.updateFromHeader("2026-10-31T07:43:19Z")

        val expected = Instant.parse("2026-10-31T07:43:19Z").toEpochMilli() - 1_000_000L
        assertEquals(expected, clock.currentOffsetMillis)
        assertEquals("2026-10-31T07:43:19Z", clock.nowUtcRfc3339())
    }

    @Test
    fun `ignores unparsable header`() {
        val clock = ServerClock { 0L }

        clock.updateFromHeader("not-a-time")

        assertEquals(0L, clock.currentOffsetMillis)
    }

    @Test
    fun `ignores missing header`() {
        val clock = ServerClock { 0L }

        clock.updateFromHeader(null)

        assertEquals(0L, clock.currentOffsetMillis)
    }

    @Test
    fun `formats without milliseconds to match server`() {
        val clock = ServerClock { Instant.parse("2026-10-31T07:43:19.987Z").toEpochMilli() }

        assertEquals("2026-10-31T07:43:19Z", clock.nowUtcRfc3339())
    }

    @Test
    fun `accepts offset timestamps`() {
        val clock = ServerClock { 0L }

        clock.updateFromHeader("2026-11-01T00:00:00+08:00")

        assertEquals(Instant.parse("2026-10-31T16:00:00Z").toEpochMilli(), clock.currentOffsetMillis)
    }
}
