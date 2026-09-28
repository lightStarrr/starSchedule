package com.star.schedule.feature.wallpaper.data

import java.io.File
import java.security.MessageDigest

internal object WallpaperFileHash {
    private const val DEFAULT_BUFFER_SIZE = 8 * 1024

    fun matches(file: File, expectedHash: String): Boolean =
        file.isFile && sha256(file) == expectedHash

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}
