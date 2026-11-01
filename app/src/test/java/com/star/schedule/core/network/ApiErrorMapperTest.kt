package com.star.schedule.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorMapperTest {

    @Test
    fun `parses business code and request id from body`() {
        val error = mapHttpError(
            status = 401,
            rawBody = errorBody(code = "TOKEN_EXPIRED", message = "登录已过期", requestId = "req-1"),
            headerRequestId = "header-req",
            retryAfterHeader = null,
        )

        val http = error as ApiError.Http
        assertEquals("TOKEN_EXPIRED", http.code)
        assertEquals("登录已过期", http.message)
        assertEquals("req-1", http.requestId)
        assertFalse(http.isRetryable)
    }

    @Test
    fun `falls back to header request id when body is not json`() {
        val error = mapHttpError(
            status = 401,
            rawBody = "<html>bad gateway</html>",
            headerRequestId = "header-req",
            retryAfterHeader = null,
        )

        val http = error as ApiError.Http
        assertEquals("HTTP_401", http.code)
        assertEquals("header-req", http.requestId)
        assertTrue(http.message.isNotBlank())
    }

    @Test
    fun `reads retry after header`() {
        val error = mapHttpError(429, errorBody("RATE_LIMITED"), null, retryAfterHeader = "12")

        assertEquals(12L, (error as ApiError.Http).retryAfterSeconds)
    }

    @Test
    fun `falls back to details retry_after`() {
        val error = mapHttpError(
            status = 429,
            rawBody = errorBody("RATE_LIMITED", details = """{"retry_after":5}"""),
            headerRequestId = null,
            retryAfterHeader = null,
        )

        assertEquals(5L, (error as ApiError.Http).retryAfterSeconds)
    }

    @Test
    fun `leaves retry after empty when server gives nothing`() {
        val error = mapHttpError(429, errorBody("RATE_LIMITED"), null, null)

        assertEquals(null, (error as ApiError.Http).retryAfterSeconds)
    }

    @Test
    fun `maps banned device to dedicated error`() {
        val error = mapHttpError(403, errorBody("DEVICE_BANNED"), null, null)

        assertTrue(error is ApiError.Banned)
    }

    @Test
    fun `marks 429 and 5xx retryable but not 413`() {
        assertTrue((mapHttpError(429, "", null, null) as ApiError.Http).isRetryable)
        assertTrue((mapHttpError(502, "", null, null) as ApiError.Http).isRetryable)
        assertFalse((mapHttpError(413, "", null, null) as ApiError.Http).isRetryable)
        assertFalse((mapHttpError(400, "", null, null) as ApiError.Http).isRetryable)
    }
}
