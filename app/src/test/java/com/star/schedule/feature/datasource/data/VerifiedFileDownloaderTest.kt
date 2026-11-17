package com.star.schedule.feature.datasource.data

import com.star.schedule.core.network.fakeTransport
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VerifiedFileDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val urls = listOf("https://a.example/f", "https://b.example/f")
    private val payload = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
    private val payloadSha = sha256Hex(payload)

    private fun client(handler: (Request) -> Response) =
        OkHttpClient.Builder()
            .addInterceptor(fakeTransport(handler))
            .build()

    private fun responseOf(request: Request, code: Int, body: ByteArray): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(body.toResponseBody("application/octet-stream".toMediaType()))
            .build()

    private fun downloader(httpClient: OkHttpClient) =
        VerifiedFileDownloader(httpClient, Dispatchers.IO)

    private fun target() = File(tmp.root, "res.bin")

    @Test
    fun `downloads to target with monotonic progress ending at total size`() = runTest {
        val progress = mutableListOf<Long>()
        val httpClient = client { responseOf(it, 200, payload) }

        downloader(httpClient).download(
            urls, payload.size.toLong(), payloadSha, target(), progress::add,
        )

        assertArrayEquals(payload, target().readBytes())
        assertTrue(progress.isNotEmpty())
        assertEquals(progress.sorted(), progress)
        assertEquals(payload.size.toLong(), progress.last())
    }

    @Test
    fun `falls back to the next mirror on http error`() = runTest {
        val httpClient = client { request ->
            when (request.url.host) {
                "a.example" -> responseOf(request, 404, ByteArray(0))
                else -> responseOf(request, 200, payload)
            }
        }

        downloader(httpClient).download(
            urls, payload.size.toLong(), payloadSha, target(),
        ) {}

        assertArrayEquals(payload, target().readBytes())
    }

    @Test
    fun `falls back to the next mirror on io exception`() = runTest {
        val httpClient = client { request ->
            if (request.url.host == "a.example") {
                throw IOException("connection reset")
            }
            responseOf(request, 200, payload)
        }

        downloader(httpClient).download(
            urls, payload.size.toLong(), payloadSha, target(),
        ) {}

        assertArrayEquals(payload, target().readBytes())
    }

    @Test
    fun `fails and leaves no target or part when sha mismatches`() = runTest {
        val httpClient = client { responseOf(it, 200, payload) }
        val target = target()

        try {
            downloader(httpClient).download(
                urls, payload.size.toLong(), "0".repeat(64), target,
            ) {}
            fail("expected sha256 mismatch")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("sha256"))
        }
        assertFalse(target.exists())
        assertFalse(File(tmp.root, "res.bin.part").exists())
    }

    @Test
    fun `aborts when the response exceeds the expected size`() = runTest {
        val big = payload + byteArrayOf(9)
        val httpClient = client { responseOf(it, 200, big) }

        try {
            downloader(httpClient).download(
                urls, payload.size.toLong(), payloadSha, target(),
            ) {}
            fail("expected oversize failure")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("larger"))
        }
        assertFalse(target().exists())
    }

    @Test
    fun `throws the last error when every mirror fails`() = runTest {
        val httpClient = client { responseOf(it, 500, ByteArray(0)) }

        try {
            downloader(httpClient).download(
                urls, payload.size.toLong(), payloadSha, target(),
            ) {}
            fail("expected failure")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("500"))
        }
    }

    @Test
    fun `overwrites an existing target with verified content`() = runTest {
        val target = target()
        target.writeBytes(byteArrayOf(1, 1, 1))
        val httpClient = client { responseOf(it, 200, payload) }

        downloader(httpClient).download(
            urls, payload.size.toLong(), payloadSha, target,
        ) {}

        assertArrayEquals(payload, target.readBytes())
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
