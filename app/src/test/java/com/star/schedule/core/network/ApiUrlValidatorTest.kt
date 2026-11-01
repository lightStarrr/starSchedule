package com.star.schedule.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiUrlValidatorTest {

    @Test
    fun `accepts https base url`() {
        assertTrue(ApiUrlValidator.isUsable("https://schedule.leapstella.com/api/v1"))
    }

    @Test
    fun `accepts http base url for local debugging`() {
        assertTrue(ApiUrlValidator.isUsable("http://10.0.2.2:8080/api/v1"))
    }

    @Test
    fun `rejects blank value`() {
        assertFalse(ApiUrlValidator.isUsable(""))
        assertFalse(ApiUrlValidator.isUsable("   "))
    }

    @Test
    fun `rejects value without scheme`() {
        assertFalse(ApiUrlValidator.isUsable("schedule.leapstella.com/api/v1"))
    }

    @Test
    fun `rejects unsupported scheme`() {
        assertFalse(ApiUrlValidator.isUsable("ftp://schedule.leapstella.com/api/v1"))
    }
}
