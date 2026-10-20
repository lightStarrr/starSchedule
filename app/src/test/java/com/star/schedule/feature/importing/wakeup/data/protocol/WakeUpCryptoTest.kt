package com.star.schedule.feature.importing.wakeup.data.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 基准值全部由 6.1.70 官方脚本原样跑出来，不是按理论值反推的。
 * 私有 DES 和 nativeHex 只要错一个位序，密文就会完全不同，所以这里必须逐字对齐。
 */
class WakeUpCryptoTest {
    @Test
    fun md5MatchesTheStandardDigest() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", WakeUpCrypto.md5Hex(""))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", WakeUpCrypto.md5Hex("abc"))
        assertEquals("900150983CD24FB0D6963F7D28E17F72", WakeUpCrypto.md5Upper("abc"))
    }

    @Test
    fun derivesStableDeviceIdentifiers() {
        assertEquals("C77D5D04D94F5F56C8A0A6DC3DBF240A|0", WakeUpCrypto.cuidFromAndroidId("0000000000000000"))
        assertEquals(
            "d58a81f457529caf0e642e7f9d97e447112bd763",
            WakeUpCrypto.adidFromAndroidId("0000000000000000"),
        )
    }

    @Test
    fun nativeHexWritesLowNibbleFirstWithReversedBits() {
        val bytes = byteArrayOf(0x01, 0xab.toByte(), 0x10, 0xff.toByte())
        val encoded = WakeUpCrypto.nativeHexEncode(bytes)

        assertEquals("08000d0500080f0f", encoded)
        assertTrue(WakeUpCrypto.nativeHexDecode(encoded).contentEquals(bytes))
    }

    @Test
    fun desEncryptMatchesThePrivateTable() {
        val plain = "8&%d*##ABCDEFGHIJ##318c6d4f74655d4f032fb0466bcfdfbc##CUID|0"
        val cipher = WakeUpCrypto.desEncrypt(plain.toByteArray(Charsets.UTF_8), "@fG2SuLA")

        assertEquals(
            "0b020b0403030a070c020501060c0a090005010f050e0b0207080901040f03010b0b050a0f07000801" +
                "02020409000105090c0f00020f00080b0b0b04060e050907010300080b0b06000804070e0f050c0e00" +
                "090b00060f04010d0807010308030d0a000e000203060c050907010d080c0707080e0603070d060f04" +
                "0a06010d0d",
            WakeUpCrypto.nativeHexEncode(cipher),
        )
        assertEquals(plain, String(WakeUpCrypto.desDecrypt(cipher, "@fG2SuLA"), Charsets.UTF_8))
    }

    @Test
    fun desDecryptsARealAntispamSignature() {
        // 真实抓包：rand10 + signB 换出的明文是 `rand10 + "##" + token`。
        val signB = "0b070703020b0b06090608060f030c000a0308020c0103080f0202020a06060b04010300030e0907" +
            "080c0d080b05090e"
        val rand10 = "dfMf5xqL20"

        val plain = String(
            WakeUpCrypto.desDecrypt(WakeUpCrypto.nativeHexDecode(signB), rand10.take(5) + "#G4"),
            Charsets.ISO_8859_1,
        )

        assertTrue(plain.startsWith(rand10))
        assertEquals("ia65puh45t", plain.substring(12, 22))
    }

    @Test
    fun rc4MatchesTheReferenceCipher() {
        val encrypted = WakeUpCrypto.rc4(
            "key=test".toByteArray(Charsets.UTF_8),
            RC4_KEY.toByteArray(Charsets.UTF_8),
        )
        assertEquals("5toxiM4dwyQ=", WakeUpCrypto.base64(encrypted))
    }

    @Test
    fun quotePlusKeepsTildeAndEscapesAsterisk() {
        assertEquals("Pixel+7", WakeUpCrypto.quotePlus("Pixel 7"))
        assertEquals("a%2Ab", WakeUpCrypto.quotePlus("a*b"))
        assertEquals("a~b-c_d.e", WakeUpCrypto.quotePlus("a~b-c_d.e"))
        assertEquals("C77D5D04D94F5F56C8A0A6DC3DBF240A%7C0", WakeUpCrypto.quotePlus("C77D5D04D94F5F56C8A0A6DC3DBF240A|0"))
    }

    private companion object {
        const val RC4_KEY =
            "ebfa6f1063c77b7fc4d415118bad86626a5e45b65b7fbfb7d551ef8edd50d4d98f42ede2ea6e77349e45a85dc0958f5f31cd22941af1d9898bc5ba70303e7668"
    }
}
