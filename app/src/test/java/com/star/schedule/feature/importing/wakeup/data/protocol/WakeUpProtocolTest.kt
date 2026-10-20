package com.star.schedule.feature.importing.wakeup.data.protocol

import com.star.schedule.feature.importing.wakeup.domain.WakeUpShareRejectedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WakeUpProtocolTest {
    private val device = WakeUpDevice()

    @Test
    fun derivesTheSameRc4KeyAsTheOfficialClient() {
        assertEquals(RC4_KEY, WakeUpProtocol.rc4Key("ABCDEFGHIJ"))
    }

    @Test
    fun buildsTheSameRequestBodyAsTheOfficialClient() {
        val request = WakeUpProtocol.buildShareRequest(
            code = "test-code_1",
            token = "ABCDEFGHIJ",
            device = device,
            common = device.commonParams(),
            nowMillis = 1_700_000_000_000,
        )

        assertEquals(RC4_KEY, request.rc4Key)
        assertEquals(EXPECTED_BODY, request.body)
    }

    @Test
    fun unwrapsShareDataFromTheEncryptedEnvelope() {
        val shareData = "{\"a\":1}\n[]\n{}\n[]\n[]"
        val key = WakeUpProtocol.rc4Key("ABCDEFGHIJ")
        val encrypted = WakeUpCrypto.base64(
            WakeUpCrypto.rc4(
                "{\"shareData\":\"{\\\"a\\\":1}\\n[]\\n{}\\n[]\\n[]\"}".toByteArray(Charsets.UTF_8),
                key.toByteArray(Charsets.UTF_8),
            ),
        )

        val unwrapped = WakeUpProtocol.unwrapShareData("""{"errNo":0,"data":{"data":"$encrypted"}}""", key)

        assertEquals(shareData, unwrapped)
    }

    @Test
    fun reportsRejectedShareCodesSeparately() {
        assertThrows(WakeUpShareRejectedException::class.java) {
            WakeUpProtocol.unwrapShareData("""{"errNo":4003,"errstr":"share code expired"}""", RC4_KEY)
        }
    }

    @Test
    fun acceptsSignBFromEitherEnvelopeShape() {
        val nested = """{"errNo":0,"errstr":"success","data":{"data":"0b0b"}}"""
        val flat = """{"errNo":0,"data":"0b0b"}"""

        assertEquals("0b0b", WakeUpProtocol.extractSignB(nested))
        assertEquals("0b0b", WakeUpProtocol.extractSignB(flat))
    }

    private companion object {
        const val RC4_KEY =
            "ebfa6f1063c77b7fc4d415118bad86626a5e45b65b7fbfb7d551ef8edd50d4d98f42ede2ea6e77349e45a85dc0958f5f31cd22941af1d9898bc5ba70303e7668"

        val EXPECTED_BODY = "&data=5toxiM4dwyQG%2B51UfCIP&area=&screensize=1080x2400" +
            "&cuid=C77D5D04D94F5F56C8A0A6DC3DBF240A%7C0&os=android&city=&abis=arm64-v8a" +
            "&channel=100271a&appBit=64&vc=450&deviceId=&token=1_XPXQH3c5HRPtFHkSwi3sCCURmT25QfxM" +
            "&adid=d58a81f457529caf0e642e7f9d97e447112bd763&province=" +
            "&pkgName=com.suda.yzune.wakeupschedule&appId=wakeup&download_type=1&vcname=6.1.70" +
            "&sdk=35&device=Pixel+7&brand=google&operatorid=&nt=wifi" +
            "&sign=594c843ed450ceb7d7911d3578746684&_t_=1700000000&kakorrhaphiophobia=1700000000000"
    }
}
