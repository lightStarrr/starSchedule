package com.star.schedule.feature.importing.wakeup.data.protocol

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale

/**
 * WakeUp 6.1.70 分享协议用到的字节级原语。
 *
 * 这里的 DES 不是标准 DES：IP/FP/E/P/S 盒和位序都来自该版本客户端的私有实现，
 * 用标准 `javax.crypto` 的 DES 只会得到完全不同、且永远对不上的密文。
 * `nativeHex` 也不是普通十六进制：每个字节拆成两个半字节，各自反转位序后再按
 * "低半字节先、高半字节后"输出，所以一个字节占四个字符。
 *
 * 所有函数都是纯函数，可脱离 Android 运行时在 JVM 单测里直接验证。
 */
internal object WakeUpCrypto {
    private const val HEX = "0123456789abcdef"

    fun md5Hex(value: String): String = md5Hex(value.toByteArray(Charsets.UTF_8))

    fun md5Hex(value: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(value)
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val unsigned = byte.toInt() and 0xff
                append(HEX[unsigned shr 4]).append(HEX[unsigned and 0x0f])
            }
        }
    }

    fun md5Upper(value: String): String = md5Hex(value).uppercase(Locale.ROOT)

    fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun unBase64(text: String): ByteArray = Base64.getDecoder().decode(text)

    /**
     * Android `URLEncoder.encode(value, "UTF-8")` 的等价物。
     *
     * 不能用 `URLEncoder` 直接替代：它保留的是 `.-*_` 而转义 `~`，Android 的
     * `quote_plus` 恰好相反（保留 `~`、转义 `*`）。签名用的是未编码的原文，
     * 编码差异只影响请求体，但差一个字符服务端就会判签名失败。
     */
    fun quotePlus(value: String): String = buildString {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val unsigned = byte.toInt() and 0xff
            val char = unsigned.toChar()
            when {
                char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' -> append(char)
                char == '-' || char == '_' || char == '.' || char == '~' -> append(char)
                char == ' ' -> append('+')
                else -> append('%')
                    .append(HEX[unsigned shr 4].uppercaseChar())
                    .append(HEX[unsigned and 0x0f].uppercaseChar())
            }
        }
    }

    fun rc4(data: ByteArray, key: ByteArray): ByteArray {
        require(key.isNotEmpty()) { "RC4 key must not be empty" }

        val state = IntArray(256) { it }
        var cursor = 0
        for (index in 0 until 256) {
            cursor = (cursor + state[index] + (key[index % key.size].toInt() and 0xff)) and 0xff
            val swap = state[index]
            state[index] = state[cursor]
            state[cursor] = swap
        }

        val output = ByteArray(data.size)
        var left = 0
        var right = 0
        for (index in data.indices) {
            left = (left + 1) and 0xff
            right = (right + state[left]) and 0xff
            val swap = state[left]
            state[left] = state[right]
            state[right] = swap
            val mask = state[(state[left] + state[right]) and 0xff]
            output[index] = (data[index].toInt() xor mask).toByte()
        }
        return output
    }

    fun reverseNibble(value: Int): Int =
        ((value and 1) shl 3) or ((value and 2) shl 1) or ((value and 4) shr 1) or ((value and 8) shr 3)

    /** 每字节输出 `0<低半字节反序>` + `0<高半字节反序>`，共四字符。 */
    fun nativeHexEncode(bytes: ByteArray): String = buildString(bytes.size * 4) {
        bytes.forEach { byte ->
            val unsigned = byte.toInt() and 0xff
            append('0').append(HEX[reverseNibble(unsigned and 0x0f)])
            append('0').append(HEX[reverseNibble(unsigned shr 4)])
        }
    }

    fun nativeHexDecode(text: String): ByteArray {
        val usable = text.length / 4 * 4
        val output = ByteArray(usable / 4)
        for (index in 0 until usable step 4) {
            val low = reverseNibble(Character.digit(text[index + 1], 16))
            val high = reverseNibble(Character.digit(text[index + 3], 16))
            output[index / 4] = (low or (high shl 4)).toByte()
        }
        return output
    }

    fun desEncrypt(plain: ByteArray, key: String): ByteArray {
        val padded = ByteArray((plain.size / 8 + 1) * 8)
        plain.copyInto(padded)
        // 填充方式是"M+1 个字节，最后一个字节写填充长度"，中间补 0，不是 PKCS#5。
        padded[padded.size - 1] = (padded.size - plain.size).toByte()
        return desBlocks(padded, desSubkeys(key))
    }

    fun desDecrypt(cipher: ByteArray, key: String): ByteArray {
        require(cipher.size % 8 == 0) { "WakeUp DES cipher length is invalid" }
        val plain = desBlocks(cipher, desSubkeys(key).reversed())
        val padding = plain.last().toInt() and 0xff
        require(padding in 1..plain.size) { "WakeUp DES padding is invalid" }
        return plain.copyOf(plain.size - padding)
    }

    fun cuidFromAndroidId(androidId: String): String =
        "${md5Upper("com.baidu$androidId")}|0"

    fun adidFromAndroidId(androidId: String): String {
        val prefix = md5Hex("alpha.beta$androidId")
        val folded = BigInteger(prefix.substring(0, 16), 16) xor
            BigInteger(prefix.substring(16), 16)
        val checksum = ((folded shr 32) xor folded) and BigInteger("ffffffff", 16)
        return prefix + checksum.toString(16).padStart(8, '0')
    }

    fun randomToken(length: Int = 10): String {
        val random = SecureRandom()
        return buildString(length) {
            repeat(length) { append(ALPHABET[random.nextInt(256) % ALPHABET.length]) }
        }
    }

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    private fun desBlocks(input: ByteArray, subkeys: List<IntArray>): ByteArray {
        val output = ByteArray(input.size)
        for (offset in input.indices step 8) {
            desBlock(input, offset, subkeys).copyInto(output, offset)
        }
        return output
    }

    private fun desBlock(input: ByteArray, offset: Int, subkeys: List<IntArray>): ByteArray {
        val bits = permute(bitsFromBytes(input, offset, 8), DES_IP)
        var left = bits.copyOfRange(0, 32)
        var right = bits.copyOfRange(32, 64)
        for (subkey in subkeys) {
            val mixed = feistel(right, subkey)
            val nextRight = IntArray(32) { left[it] xor mixed[it] }
            left = right
            right = nextRight
        }
        return bytesFromBits(permute(right + left, DES_FP))
    }

    private fun feistel(right: IntArray, subkey: IntArray): IntArray {
        val expanded = permute(right, DES_E)
        val mixed = IntArray(48) { expanded[it] xor subkey[it] }
        val output = IntArray(32)
        for (box in 0 until 8) {
            val block = mixed.copyOfRange(box * 6, box * 6 + 6)
            val row = block[0] * 2 + block[5]
            val column = block[1] * 8 + block[2] * 4 + block[3] * 2 + block[4]
            val value = DES_S_BOX[box][row][column]
            output[box * 4] = (value shr 3) and 1
            output[box * 4 + 1] = (value shr 2) and 1
            output[box * 4 + 2] = (value shr 1) and 1
            output[box * 4 + 3] = value and 1
        }
        return permute(output, DES_P)
    }

    private fun desSubkeys(key: String): List<IntArray> {
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        require(keyBytes.size == 8) { "WakeUp DES key must be 8 bytes" }
        val permuted = permute(bitsFromBytes(keyBytes, 0, 8), DES_PC1)
        var left = permuted.copyOfRange(0, 28)
        var right = permuted.copyOfRange(28, 56)
        return DES_SHIFTS.map { shift ->
            left = left.rotate(shift)
            right = right.rotate(shift)
            permute(left + right, DES_PC2)
        }
    }

    private fun IntArray.rotate(shift: Int): IntArray =
        IntArray(size) { this[(it + shift) % size] }

    private fun bitsFromBytes(bytes: ByteArray, offset: Int, length: Int): IntArray =
        IntArray(length * 8) { index ->
            (bytes[offset + index / 8].toInt() shr (index % 8)) and 1
        }

    private fun bytesFromBits(bits: IntArray): ByteArray {
        val output = ByteArray(bits.size / 8)
        for (index in output.indices) {
            var value = 0
            for (bit in 0 until 8) value = value or (bits[index * 8 + bit] shl bit)
            output[index] = value.toByte()
        }
        return output
    }

    private fun permute(bits: IntArray, table: IntArray): IntArray =
        IntArray(table.size) { bits[table[it]] }

    private val DES_IP = intArrayOf(
        57, 49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3,
        61, 53, 45, 37, 29, 21, 13, 5, 63, 55, 47, 39, 31, 23, 15, 7,
        56, 48, 40, 32, 24, 16, 8, 0, 58, 50, 42, 34, 26, 18, 10, 2,
        60, 52, 44, 36, 28, 20, 12, 4, 62, 54, 46, 38, 30, 22, 14, 6,
    )

    private val DES_FP = intArrayOf(
        39, 7, 47, 15, 55, 23, 63, 31, 38, 6, 46, 14, 54, 22, 62, 30,
        37, 5, 45, 13, 53, 21, 61, 29, 36, 4, 44, 12, 52, 20, 60, 28,
        35, 3, 43, 11, 51, 19, 59, 27, 34, 2, 42, 10, 50, 18, 58, 26,
        33, 1, 41, 9, 49, 17, 57, 25, 32, 0, 40, 8, 48, 16, 56, 24,
    )

    private val DES_E = intArrayOf(
        31, 0, 1, 2, 3, 4, 3, 4, 5, 6, 7, 8, 7, 8, 9, 10, 11, 12,
        11, 12, 13, 14, 15, 16, 15, 16, 17, 18, 19, 20, 19, 20, 21, 22, 23, 24,
        23, 24, 25, 26, 27, 28, 27, 28, 29, 30, 31, 0,
    )

    private val DES_P = intArrayOf(
        15, 6, 19, 20, 28, 11, 27, 16, 0, 14, 22, 25, 4, 17, 30, 9,
        1, 7, 23, 13, 31, 26, 2, 8, 18, 12, 29, 5, 21, 10, 3, 24,
    )

    private val DES_PC1 = intArrayOf(
        56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17, 9, 1,
        58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35, 62, 54, 46, 38,
        30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5, 60, 52, 44, 36,
        28, 20, 12, 4, 27, 19, 11, 3,
    )

    private val DES_PC2 = intArrayOf(
        13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9, 22, 18, 11, 3,
        25, 7, 15, 6, 26, 19, 12, 1, 40, 51, 30, 36, 46, 54, 29, 39,
        50, 44, 32, 46, 43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31,
    )

    private val DES_SHIFTS = intArrayOf(1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1)

    private val DES_S_BOX = arrayOf(
        arrayOf(
            intArrayOf(14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7),
            intArrayOf(0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8),
            intArrayOf(4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0),
            intArrayOf(15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13),
        ),
        arrayOf(
            intArrayOf(15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10),
            intArrayOf(3, 13, 4, 7, 15, 2, 8, 14, 12, 0, 1, 10, 6, 9, 11, 5),
            intArrayOf(0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15),
            intArrayOf(13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9),
        ),
        arrayOf(
            intArrayOf(10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8),
            intArrayOf(13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1),
            intArrayOf(13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7),
            intArrayOf(1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12),
        ),
        arrayOf(
            intArrayOf(7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15),
            intArrayOf(13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9),
            intArrayOf(10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4),
            intArrayOf(3, 15, 0, 6, 10, 1, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14),
        ),
        arrayOf(
            intArrayOf(2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9),
            intArrayOf(14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6),
            intArrayOf(4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14),
            intArrayOf(11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3),
        ),
        arrayOf(
            intArrayOf(12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11),
            intArrayOf(10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8),
            intArrayOf(9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6),
            intArrayOf(4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13),
        ),
        arrayOf(
            intArrayOf(4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1),
            intArrayOf(13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6),
            intArrayOf(1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2),
            intArrayOf(6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12),
        ),
        arrayOf(
            intArrayOf(13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7),
            intArrayOf(1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2),
            intArrayOf(7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8),
            intArrayOf(2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11),
        ),
    )
}
