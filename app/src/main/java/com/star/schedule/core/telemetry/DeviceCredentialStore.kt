package com.star.schedule.core.telemetry

import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * `device.xml` 的读写入口，也是进程内唯一持有 token 的地方。
 *
 * 三条规则：
 * 1. `install_id` 明文、永不因解密失败清除——它是服务端注册的幂等键，丢了就等于换了一台设备。
 * 2. token 加密存；解不开说明 Keystore 已经不可用，只清 token，下次自动重新注册。
 * 3. 加解密失败都不抛异常，降级明文只为避免 Keystore 坏掉的机器陷入「每次启动都重新注册」。
 */
class DeviceCredentialStore(
    private val store: KeyValueStore,
    private val cipher: SecretCipher,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    private var cachedToken: String? = null
    private var tokenLoaded = false

    suspend fun installId(): String = mutex.withLock { installIdLocked() }

    suspend fun deviceId(): String? = mutex.withLock { store.getString(KEY_DEVICE_ID) }

    suspend fun token(): String? = mutex.withLock { tokenLocked() }

    suspend fun saveRegistration(deviceId: String, token: String) = mutex.withLock {
        store.putString(KEY_DEVICE_ID, deviceId)
        writeTokenLocked(token)
    }

    /** 收到 `X-Refresh-Token` 时静默覆盖，不能动 install_id / device_id。 */
    suspend fun updateToken(token: String) = mutex.withLock { writeTokenLocked(token) }

    suspend fun clearToken() = mutex.withLock {
        store.remove(KEY_TOKEN)
        cachedToken = null
        tokenLoaded = true
    }

    suspend fun bannedUntil(): Long? = mutex.withLock {
        store.getString(KEY_BANNED_UNTIL)?.toLongOrNull()
    }

    /**
     * 记下封禁冷却期。到期后允许再试一次，这样管理员解封后客户端能自动恢复，
     * 而不是永久停摆。
     */
    suspend fun markBanned(cooldownMillis: Long = BAN_COOLDOWN_MILLIS) = mutex.withLock {
        store.putString(KEY_BANNED_UNTIL, (nowMillis() + cooldownMillis).toString())
    }

    suspend fun lastDeviceCheckAt(): Long? = mutex.withLock {
        store.getString(KEY_LAST_ME_AT)?.toLongOrNull()
    }

    suspend fun markDeviceChecked() = mutex.withLock {
        store.putString(KEY_LAST_ME_AT, nowMillis().toString())
    }

    private fun installIdLocked(): String {
        val existing = store.getString(KEY_INSTALL_ID)
        if (existing != null && existing.length in INSTALL_ID_RANGE) return existing
        val generated = UUID.randomUUID().toString()
        store.putString(KEY_INSTALL_ID, generated)
        return generated
    }

    private fun tokenLocked(): String? {
        if (tokenLoaded) return cachedToken
        tokenLoaded = true
        val stored = store.getString(KEY_TOKEN) ?: return null
        val plain = decodeToken(stored)
        cachedToken = plain
        if (plain == null) store.remove(KEY_TOKEN)
        return plain
    }

    private fun writeTokenLocked(token: String) {
        val encrypted = cipher.encrypt(token)
        val encoded = if (encrypted != null) {
            PREFIX_ENCRYPTED + encrypted
        } else {
            PREFIX_PLAIN + token
        }
        store.putString(KEY_TOKEN, encoded)
        cachedToken = token
        tokenLoaded = true
    }

    private fun decodeToken(stored: String): String? = when {
        stored.startsWith(PREFIX_ENCRYPTED) -> cipher.decrypt(stored.removePrefix(PREFIX_ENCRYPTED))
        stored.startsWith(PREFIX_PLAIN) -> stored.removePrefix(PREFIX_PLAIN).takeIf { it.isNotBlank() }
        else -> null
    }

    companion object {
        const val PREFERENCES_NAME = "device"
        const val KEY_INSTALL_ID = "install_id"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_TOKEN = "access_token"
        const val KEY_BANNED_UNTIL = "banned_until"
        const val KEY_LAST_ME_AT = "last_me_at"

        private const val PREFIX_ENCRYPTED = "gcm:"
        private const val PREFIX_PLAIN = "plain:"
        private val INSTALL_ID_RANGE = 8..64

        /** 被封禁后多久才再试一次，避免每次都撞 403。 */
        const val BAN_COOLDOWN_MILLIS = 24L * 60 * 60 * 1000

        /** 距离上次校验设备状态超过这个时间才重新请求 /devices/me。 */
        const val DEVICE_CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
    }
}
