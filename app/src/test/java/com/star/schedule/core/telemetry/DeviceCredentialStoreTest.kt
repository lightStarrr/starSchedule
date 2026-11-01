package com.star.schedule.core.telemetry

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCredentialStoreTest {

    private val store = TestKeyValueStore()

    private fun credentialStore(cipher: SecretCipher, now: () -> Long = { 1_000L }) =
        DeviceCredentialStore(store, cipher, now)

    @Test
    fun `generates install id once and keeps it stable`() = runTest {
        val credentials = credentialStore(FakeCipher())

        val first = credentials.installId()
        val second = credentials.installId()

        assertEquals(first, second)
        assertEquals(36, first.length)
    }

    @Test
    fun `stores token encrypted`() = runTest {
        val credentials = credentialStore(FakeCipher())

        credentials.saveRegistration("device-1", "token-1")

        assertEquals("token-1", credentials.token())
        assertEquals("device-1", credentials.deviceId())
        assertTrue(store.values[DeviceCredentialStore.KEY_TOKEN]!!.startsWith("gcm:"))
    }

    @Test
    fun `keeps install id when token can not be decrypted`() = runTest {
        val cipher = FakeCipher()
        val credentials = credentialStore(cipher)
        val installId = credentials.installId()
        credentials.saveRegistration("device-1", "token-1")

        cipher.decryptFails = true
        val reloaded = credentialStore(cipher)

        assertNull(reloaded.token())
        assertEquals(installId, reloaded.installId())
        assertNull(store.values[DeviceCredentialStore.KEY_TOKEN])
    }

    @Test
    fun `falls back to plaintext when keystore is unusable`() = runTest {
        val cipher = FakeCipher(encryptFails = true)
        val credentials = credentialStore(cipher)

        credentials.saveRegistration("device-1", "token-1")

        assertTrue(store.values[DeviceCredentialStore.KEY_TOKEN]!!.startsWith("plain:"))
        assertEquals("token-1", credentialStore(cipher).token())
    }

    @Test
    fun `refreshing token keeps device identity`() = runTest {
        val credentials = credentialStore(FakeCipher())
        credentials.saveRegistration("device-1", "token-1")
        val installId = credentials.installId()

        credentials.updateToken("token-2")

        assertEquals("token-2", credentials.token())
        assertEquals("device-1", credentials.deviceId())
        assertEquals(installId, credentials.installId())
    }

    @Test
    fun `ban cooldown is anchored to now`() = runTest {
        val credentials = credentialStore(FakeCipher(), now = { 5_000L })

        credentials.markBanned(cooldownMillis = 1_000L)

        assertEquals(6_000L, credentials.bannedUntil())
    }

    @Test
    fun `clearing token does not touch install id`() = runTest {
        val credentials = credentialStore(FakeCipher())
        val installId = credentials.installId()
        credentials.saveRegistration("device-1", "token-1")

        credentials.clearToken()

        assertNull(credentials.token())
        assertEquals(installId, credentials.installId())
        assertNotEquals("", installId)
    }
}
