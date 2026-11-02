package com.star.schedule.core.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TelemetryStartupGateTest {
    private val events = mutableListOf<String>()
    private val gate = TelemetryStartupGate(
        onProcessStart = { events += "cold" },
        onForeground = { events += "warm" },
        onConsentChanged = { events += "consent:$it" },
    )

    @Test
    fun `startup waits for privacy preference then preserves cold and warm order`() {
        gate.processStarted()
        gate.foregrounded()
        assertFalse(gate.enabled)
        assertEquals(emptyList<String>(), events)

        gate.updateConsent(true)
        gate.updateConsent(true)
        assertEquals(listOf("consent:true", "cold", "warm"), events)
    }

    @Test
    fun `disabled consent discards early events rather than replaying them later`() {
        gate.processStarted()
        gate.foregrounded()
        gate.updateConsent(false)
        gate.foregrounded()
        gate.updateConsent(true)
        assertEquals(listOf("consent:false", "consent:true"), events)

        gate.foregrounded()
        assertEquals(listOf("consent:false", "consent:true", "warm"), events)
    }
}
