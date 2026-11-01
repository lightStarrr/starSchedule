package com.star.schedule.core.telemetry

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 凭据加密。失败一律返回 null 而不是抛异常：
 * Keystore 在清数据、换机、系统升级后都可能失效，这时正确行为是「当成没有凭据」重新注册，
 * 而不是让应用起不来。
 */
interface SecretCipher {
    fun encrypt(plain: String): String?
    fun decrypt(stored: String): String?
}

/**
 * 用 Android Keystore 里的 AES-256-GCM 密钥加密。
 *
 * 刻意不设 `setUserAuthenticationRequired`：否则用户改锁屏方式就会让密钥永久不可用，
 * 而设备令牌本来就可以随时重新签发，不值得为此承担数据不可恢复的代价。
 */
class KeystoreAesGcmCipher(
    private val alias: String = DEFAULT_ALIAS,
) : SecretCipher {

    override fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }.getOrNull()

    override fun decrypt(stored: String): String? {
        if (stored.isBlank()) return null
        return runCatching {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            require(raw.size > IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                loadOrCreateKey(),
                GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES),
            )
            String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val DEFAULT_ALIAS = "star_schedule_device_token_v1"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}
