package com.star.schedule.platform.systembar

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusBarContrastSelectorTest {
    private val selector = StatusBarContrastSelector()

    @Test
    fun brightBackgroundInitiallyUsesDarkIcons() {
        assertEquals(
            StatusBarIconMode.DarkIcons,
            selector.select(backgroundLuminance = 0.8, currentMode = null),
        )
    }

    @Test
    fun darkBackgroundInitiallyUsesLightIcons() {
        assertEquals(
            StatusBarIconMode.LightIcons,
            selector.select(backgroundLuminance = 0.2, currentMode = null),
        )
    }

    @Test
    fun middleLuminanceKeepsDarkIcons() {
        assertEquals(
            StatusBarIconMode.DarkIcons,
            selector.select(
                backgroundLuminance = 0.5,
                currentMode = StatusBarIconMode.DarkIcons,
            ),
        )
    }

    @Test
    fun middleLuminanceKeepsLightIcons() {
        assertEquals(
            StatusBarIconMode.LightIcons,
            selector.select(
                backgroundLuminance = 0.5,
                currentMode = StatusBarIconMode.LightIcons,
            ),
        )
    }

    @Test
    fun darkIconsSwitchOnlyBelowLowerThreshold() {
        assertEquals(
            StatusBarIconMode.LightIcons,
            selector.select(
                backgroundLuminance = 0.3,
                currentMode = StatusBarIconMode.DarkIcons,
            ),
        )
    }

    @Test
    fun lightIconsSwitchOnlyAboveUpperThreshold() {
        assertEquals(
            StatusBarIconMode.DarkIcons,
            selector.select(
                backgroundLuminance = 0.7,
                currentMode = StatusBarIconMode.LightIcons,
            ),
        )
    }
}
