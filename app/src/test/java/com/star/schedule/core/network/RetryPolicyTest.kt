package com.star.schedule.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {

    @Test
    fun `register backs off 1s 3s 7s and stops after three retries`() {
        val delays = (0..2).map { RetryPolicy.Register.backoffDelay(it) }

        assertEquals(listOf(1_000L, 3_000L, 7_000L), delays)
        assertEquals(3, RetryPolicy.Register.backoffMillis.size)
    }

    @Test
    fun `launch totals five attempts`() {
        val delays = RetryPolicy.Launch.backoffMillis.mapIndexed { index, _ ->
            RetryPolicy.Launch.backoffDelay(index)
        }

        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L), delays)
        assertEquals(5, delays.size + 1)
    }

    @Test
    fun `backoff never exceeds the cap`() {
        assertTrue(RetryPolicy.Launch.backoffDelay(99) <= RetryPolicy.MAX_DELAY_MS)
    }

    @Test
    fun `rate limit prefers retry after header`() {
        assertEquals(12_000L, RetryPolicy.Read.rateLimitDelay(12))
    }

    @Test
    fun `rate limit falls back to three seconds`() {
        assertEquals(3_000L, RetryPolicy.Read.rateLimitDelay(null))
    }

    @Test
    fun `rate limit caps absurd retry after values`() {
        assertEquals(RetryPolicy.MAX_DELAY_MS, RetryPolicy.Read.rateLimitDelay(3_600))
    }

    @Test
    fun `none policy never retries`() {
        assertEquals(0, RetryPolicy.None.backoffMillis.size)
        assertEquals(0, RetryPolicy.None.maxServerErrorRetries)
    }
}
