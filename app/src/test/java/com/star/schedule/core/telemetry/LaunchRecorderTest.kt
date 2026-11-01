package com.star.schedule.core.telemetry

import com.star.schedule.core.network.ApiError
import com.star.schedule.core.network.ApiResult
import com.star.schedule.core.network.ServerClock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchRecorderTest {

    private val api = FakeDeviceApi()
    private val store = TestKeyValueStore()
    private val now = 1_000_000_000_000L
    private val credentials = DeviceCredentialStore(store, FakeCipher(), nowMillis = { now })
    private val session = DeviceSession(
        api = api,
        credentials = credentials,
        identity = DeviceIdentity({ "9774d56d682e549c" }, { "install-fallback" }),
        clientInfo = { telemetryClientInfo },
        nowMillis = { now },
    )
    private val queue = LaunchQueue(
        store = TestKeyValueStore(),
        nowMillis = { now },
    )

    private fun TestScope.recorder(
        ids: ArrayDeque<String>,
        processStartElapsedMillis: Long = 100L,
        elapsedMillis: () -> Long = { 920L },
    ) = LaunchRecorder(
        queue = queue,
        api = api,
        session = session,
        clock = ServerClock { now },
        clientInfo = { telemetryClientInfo },
        scope = this,
        processStartElapsedMillis = processStartElapsedMillis,
        elapsedMillis = elapsedMillis,
        newSessionId = { ids.removeFirst() },
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun `first foreground records cold and warm with cold start cost`() = runTest {
        api.launchResult = { ApiResult.Failure(networkError()) }
        val ids = ArrayDeque(listOf("session-cold", "session-warm"))
        val recorder = recorder(ids)

        recorder.onProcessStart()
        advanceUntilIdle()
        recorder.onForeground()
        advanceUntilIdle()

        val records = queue.snapshot()
        assertEquals(2, records.size)
        val cold = records.single { it.sessionId == "session-cold" }
        val warm = records.single { it.sessionId == "session-warm" }
        assertEquals(LaunchRecorder.LAUNCH_TYPE_COLD, cold.launchType)
        assertEquals(LaunchRecorder.LAUNCH_TYPE_WARM, warm.launchType)
        assertEquals(820L, cold.coldStartMs)
        assertNull(warm.coldStartMs)
    }

    @Test
    fun `returning from background adds only a warm record`() = runTest {
        api.launchResult = { ApiResult.Failure(networkError()) }
        val ids = ArrayDeque(listOf("cold", "warm-1", "warm-2"))
        val recorder = recorder(ids)

        recorder.onProcessStart()
        advanceUntilIdle()
        recorder.onForeground()
        advanceUntilIdle()
        recorder.onForeground()
        advanceUntilIdle()

        assertEquals(3, queue.snapshot().size)
        assertEquals(1, queue.snapshot().count { it.launchType == LaunchRecorder.LAUNCH_TYPE_COLD })
        assertEquals(2, queue.snapshot().count { it.launchType == LaunchRecorder.LAUNCH_TYPE_WARM })
    }

    @Test
    fun `flush delivers pending records and clears the queue`() = runTest {
        queue.upsert(record("a"))
        queue.upsert(record("b"))

        val pending = recorder(ArrayDeque()).flush()

        assertFalse(pending)
        assertEquals(2, api.launchCalls)
        assertEquals(0, queue.size())
    }

    @Test
    fun `flush sends cold start cost in extra`() = runTest {
        queue.upsert(record("a", coldStartMs = 512))

        recorder(ArrayDeque()).flush()

        assertEquals(512L, api.launchedBodies.single().extra?.get("cold_start_ms"))
    }

    @Test
    fun `permanent failures drop the record instead of blocking the queue`() = runTest {
        api.launchResult = {
            ApiResult.Failure(ApiError.Http(status = 400, code = "INVALID_ARGUMENT", message = "参数错误"))
        }
        queue.upsert(record("a"))

        val pending = recorder(ArrayDeque()).flush()

        assertFalse(pending)
        assertEquals(1, api.launchCalls)
        assertEquals(0, queue.size())
    }

    @Test
    fun `retryable failures keep the record for the next launch`() = runTest {
        api.launchResult = { ApiResult.Failure(networkError()) }
        queue.upsert(record("a"))

        val pending = recorder(ArrayDeque()).flush()

        assertTrue(pending)
        assertEquals(1, queue.size())
    }

    @Test
    fun `flush registers the device before reporting`() = runTest {
        queue.upsert(record("a"))

        recorder(ArrayDeque()).flush()

        assertEquals(1, api.registerCalls)
        assertEquals(1, api.launchCalls)
        assertEquals(1, api.meCalls)
    }

    private fun record(sessionId: String, coldStartMs: Long? = null) = LaunchRecord(
        sessionId = sessionId,
        launchedAt = "2026-11-01T00:00:00Z",
        launchType = LaunchRecorder.LAUNCH_TYPE_COLD,
        coldStartMs = coldStartMs,
        createdAtEpochMs = now,
    )
}
