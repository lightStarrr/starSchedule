package com.star.schedule.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun identifiesNewerSemanticVersion() {
        assertTrue(isNewerVersion("v1.3.0", "1.2.9"))
        assertTrue(isNewerVersion("1.2.1", "v1.2"))
    }

    @Test
    fun rejectsEqualOrOlderVersion() {
        assertFalse(isNewerVersion("v1.2.0", "1.2"))
        assertFalse(isNewerVersion("1.1.9", "1.2.0"))
    }

    @Test
    fun preservesZeroFallbackForNonNumericParts() {
        assertFalse(isNewerVersion("v1.preview.0", "1.0.0"))
    }
}
