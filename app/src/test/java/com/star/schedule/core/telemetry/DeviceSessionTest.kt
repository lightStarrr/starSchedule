package com.star.schedule.core.telemetry

import com.star.schedule.core.network.ApiError
import com.star.schedule.core.network.ApiResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceSessionTest {

    private val api = FakeDeviceApi()
    private val store = TestKeyValueStore()
    private var now = 1_000_000L
    private val credentials = DeviceCredentialStore(store, FakeCipher(), nowMillis = { now })
    private val session = DeviceSession(
        api = api,
        credentials = credentials,
        identity = DeviceIdentity(androidIdProvider = { "9774d56d682e549c" }, installIdProvider = { "install-fallback" }),
        clientInfo = { telemetryClientInfo },
        nowMillis = { now },
    )

    @Test
    fun `concurrent refreshes register only once`() = runTest {
        api.registerDelayMillis = 10

        val tokens = List(5) { async { session.refreshAfter401("stale-token") } }.awaitAll()

        assertEquals(1, api.registerCalls)
        assertTrue(tokens.all { it == "token-1" })
    }

    @Test
    fun `reuses token that someone else already refreshed`() = runTest {
        credentials.saveRegistration("device-1", "fresh-token")

        val token = session.refreshAfter401("stale-token")

        assertEquals("fresh-token", token)
        assertEquals(0, api.registerCalls)
    }

    @Test
    fun `gives up when registration fails`() = runTest {
        api.registerResult = { ApiResult.Failure(networkError()) }

        val token = session.refreshAfter401("stale-token")

        assertNull(token)
        assertEquals(1, api.registerCalls)
    }

    @Test
    fun `banned device stops further attempts`() = runTest {
        api.registerResult = { ApiResult.Failure(ApiError.Banned()) }

        assertNull(session.refreshAfter401(null))
        assertNull(session.refreshAfter401(null))

        assertEquals(1, api.registerCalls)
    }

    @Test
    fun `retries registration after the ban cooldown`() = runTest {
        api.registerResult = { ApiResult.Failure(ApiError.Banned()) }
        session.refreshAfter401(null)

        now += DeviceCredentialStore.BAN_COOLDOWN_MILLIS + 1
        api.registerResult = { ApiResult.Success(registerResponseOf()) }
        val token = session.refreshAfter401(null)

        assertEquals("token-1", token)
        assertEquals(2, api.registerCalls)
    }

    @Test
    fun `ensure registered reuses the stored token`() = runTest {
        credentials.saveRegistration("device-1", "token-1")

        val result = session.ensureRegistered()

        assertTrue(result is ApiResult.Success)
        assertEquals("token-1", (result as ApiResult.Success).value)
        assertEquals(0, api.registerCalls)
    }

    @Test
    fun `ensure registered sends contract compliant profile`() = runTest {
        session.ensureRegistered()

        val body = api.registeredBodies.single()
        assertEquals(36, body.installId.length)
        assertEquals("9774d56d682e549c", body.androidId)
        assertEquals("1.2.0", body.appVersion)
        assertEquals(120, body.versionCode)
        assertEquals(34, body.sdkInt)
        assertEquals("arm64-v8a", body.arch)
        assertEquals("android", body.platform)
    }

    @Test
    fun `falls back to install id when android id is unusable`() = runTest {
        val fallbackSession = DeviceSession(
            api = api,
            credentials = credentials,
            identity = DeviceIdentity(androidIdProvider = { "" }, installIdProvider = { "install-fallback" }),
            clientInfo = { telemetryClientInfo },
            nowMillis = { now },
        )

        fallbackSession.ensureRegistered()

        assertEquals("install-fallback", api.registeredBodies.single().androidId)
    }

    @Test
    fun `check device marks banned status`() = runTest {
        api.meResult = { ApiResult.Success(DeviceMe(status = DeviceMe.STATUS_BANNED)) }

        val result = session.checkDevice()

        assertTrue(result is ApiResult.Success)
        assertTrue(credentials.bannedUntil()!! > now)
    }

    @Test
    fun `check device records the check time`() = runTest {
        session.checkDevice()

        assertEquals(now, credentials.lastDeviceCheckAt())
    }
}
