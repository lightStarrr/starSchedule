package com.star.schedule.feature.importing.sleepdown.data

import com.star.schedule.feature.importing.sleepdown.domain.SleepDownProtocolException
import com.star.schedule.feature.importing.sleepdown.domain.SleepDownServiceBusyException
import com.star.schedule.feature.importing.sleepdown.domain.SleepDownShareRejectedException
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetSocketAddress

class OkHttpSleepDownShareSourceTest {
    private var server: HttpServer? = null

    @After
    fun tearDown() {
        server?.stop(0)
        server = null
    }

    private fun startServer(handler: (method: String, path: String, body: String) -> Pair<Int, String>): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/") { exchange ->
            val requestBody = exchange.requestBody.readBytes().decodeToString()
            val (status, response) = handler(exchange.requestMethod, exchange.requestURI.path, requestBody)
            val bytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        http.start()
        server = http
        return "http://127.0.0.1:${http.address.port}"
    }

    private fun source(baseUrl: String, client: OkHttpClient = OkHttpClient()) =
        OkHttpSleepDownShareSource(client = client, baseUrl = baseUrl)

    @Test
    fun `successful resolve returns payload and posts code as JSON`() = runTest {
        var capturedPath = ""
        var capturedBody = ""
        val baseUrl = startServer { method, path, body ->
            capturedPath = path
            capturedBody = body
            assertEquals("POST", method)
            200 to """{"payload":"{\"courses\":[]}","expiresAt":1893456000}"""
        }

        val payload = source(baseUrl).fetch("7K3QX9MZ2A")

        assertEquals("{\"courses\":[]}", payload)
        assertEquals("/api/v1/short-codes/resolve", capturedPath)
        assertTrue(capturedBody.contains("\"code\":\"7K3QX9MZ2A\""))
    }

    @Test
    fun `404 maps to rejected`() = runTest {
        val baseUrl = startServer { _, _, _ -> 404 to "{}" }
        assertFailsWith<SleepDownShareRejectedException> { source(baseUrl).fetch("7K3QX9MZ2A") }
    }

    @Test
    fun `429 and 503 map to busy`() = runTest {
        for (status in listOf(429, 503)) {
            val baseUrl = startServer { _, _, _ -> status to "{}" }
            assertFailsWith<SleepDownServiceBusyException> { source(baseUrl).fetch("7K3QX9MZ2A") }
            server?.stop(0)
            server = null
        }
    }

    @Test
    fun `other error status maps to protocol error`() = runTest {
        val baseUrl = startServer { _, _, _ -> 500 to "{}" }
        assertFailsWith<SleepDownProtocolException> { source(baseUrl).fetch("7K3QX9MZ2A") }
    }

    @Test
    fun `redirect is not followed`() = runTest {
        val baseUrl = startServer { _, _, _ -> 302 to "{}" }
        assertFailsWith<SleepDownProtocolException> { source(baseUrl).fetch("7K3QX9MZ2A") }
    }

    @Test
    fun `redirect not followed even when injected client enables it`() = runTest {
        val baseUrl = startServer { _, path, _ ->
            if (path == "/api/v1/short-codes/resolve") 302 to "{}" else 200 to """{"payload":"x"}"""
        }
        val client = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()
        assertFailsWith<SleepDownProtocolException> { source(baseUrl, client).fetch("7K3QX9MZ2A") }
    }

    @Test
    fun `non json body maps to protocol error`() = runTest {
        val baseUrl = startServer { _, _, _ -> 200 to "oops" }
        assertFailsWith<SleepDownProtocolException> { source(baseUrl).fetch("7K3QX9MZ2A") }
    }

    @Test
    fun `missing or blank payload maps to protocol error`() = runTest {
        var baseUrl = startServer { _, _, _ -> 200 to """{"expiresAt":1893456000}""" }
        assertFailsWith<SleepDownProtocolException> { source(baseUrl).fetch("7K3QX9MZ2A") }
        server?.stop(0)
        server = null
        baseUrl = startServer { _, _, _ -> 200 to """{"payload":"","expiresAt":1893456000}""" }
        assertFailsWith<SleepDownProtocolException> { source(baseUrl).fetch("7K3QX9MZ2A") }
    }

    @Test
    fun `non string payload maps to protocol error`() = runTest {
        val baseUrl = startServer { _, _, _ -> 200 to """{"payload":123,"expiresAt":1}""" }
        assertFailsWith<SleepDownProtocolException> { source(baseUrl).fetch("7K3QX9MZ2A") }
    }

    @Test
    fun `oversized response maps to protocol error`() = runTest {
        val big = """{"payload":"""" + "x".repeat(70 * 1024) + """"}"""
        val baseUrl = startServer { _, _, _ -> 200 to big }
        assertFailsWith<SleepDownProtocolException> { source(baseUrl).fetch("7K3QX9MZ2A") }
    }

    private suspend inline fun <reified T : Throwable> assertFailsWith(
        crossinline block: suspend () -> String,
    ) {
        try {
            block()
        } catch (exception: Throwable) {
            if (exception is T) return
            fail("expected ${T::class.simpleName}, got $exception")
        }
        fail("expected ${T::class.simpleName}, nothing thrown")
    }
}
