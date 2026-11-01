package com.star.schedule.core.telemetry

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchQueueTest {

    private val store = TestKeyValueStore()
    private var now = 1_000_000_000_000L

    private fun queue(maxRecords: Int = LaunchQueue.DEFAULT_MAX_RECORDS) = LaunchQueue(
        store = store,
        nowMillis = { now },
        maxRecords = maxRecords,
    )

    private fun record(
        sessionId: String,
        coldStartMs: Long? = null,
        createdAt: Long = now,
    ) = LaunchRecord(
        sessionId = sessionId,
        launchedAt = "2026-11-01T00:00:00Z",
        launchType = "cold",
        coldStartMs = coldStartMs,
        createdAtEpochMs = createdAt,
    )

    @Test
    fun `merges records with the same session id`() = runTest {
        queue().apply {
            upsert(record("a"))
            upsert(record("a", coldStartMs = 820))
        }

        val records = queue().snapshot()

        assertEquals(1, records.size)
        assertEquals(820L, records.single().coldStartMs)
    }

    @Test
    fun `merge never clears an existing cold start value`() = runTest {
        queue().apply {
            upsert(record("a", coldStartMs = 820))
            upsert(record("a"))
        }

        assertEquals(820L, queue().snapshot().single().coldStartMs)
    }

    @Test
    fun `evicts the oldest records beyond capacity`() = runTest {
        val subject = queue(maxRecords = 3)
        repeat(5) { index ->
            subject.upsert(record("s$index", createdAt = now + index))
        }

        val ids = subject.snapshot().map { it.sessionId }

        assertEquals(listOf("s2", "s3", "s4"), ids)
    }

    @Test
    fun `drops records older than the ttl`() = runTest {
        val subject = queue()
        subject.upsert(record("stale", createdAt = now - LaunchQueue.DEFAULT_TTL_MILLIS - 1))
        subject.upsert(record("fresh"))

        assertEquals(listOf("fresh"), subject.snapshot().map { it.sessionId })
    }

    @Test
    fun `survives corrupted storage`() = runTest {
        store.values[LaunchQueue.KEY_RECORDS] = "{ not json"

        assertTrue(queue().snapshot().isEmpty())
    }

    @Test
    fun `remove deletes only the given session`() = runTest {
        val subject = queue()
        subject.upsert(record("a"))
        subject.upsert(record("b"))

        subject.remove("a")

        assertEquals(listOf("b"), subject.snapshot().map { it.sessionId })
    }

    @Test
    fun `failed delivery keeps the record`() = runTest {
        val subject = queue()
        subject.upsert(record("a"))

        assertEquals(1, subject.size())
        assertNull(subject.snapshot().firstOrNull()?.coldStartMs)
    }

    @Test
    fun `clear drops every record`() = runTest {
        val subject = queue()
        subject.upsert(record("a"))
        subject.upsert(record("b"))

        subject.clear()

        assertEquals(0, subject.size())
        assertTrue(subject.snapshot().isEmpty())
    }
}
