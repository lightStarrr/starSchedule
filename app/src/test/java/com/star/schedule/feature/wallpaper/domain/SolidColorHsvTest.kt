package com.star.schedule.feature.wallpaper.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SolidColorHsvTest {

    @Test
    fun `primary colors convert accurately between argb and hsv`() {
        val samples = listOf(
            0xFFFF0000.toInt() to SolidColorHsv(0f, 1f, 1f),
            0xFF00FF00.toInt() to SolidColorHsv(120f, 1f, 1f),
            0xFF0000FF.toInt() to SolidColorHsv(240f, 1f, 1f),
            0xFFFFFF00.toInt() to SolidColorHsv(60f, 1f, 1f),
            0xFF00FFFF.toInt() to SolidColorHsv(180f, 1f, 1f),
            0xFFFF00FF.toInt() to SolidColorHsv(300f, 1f, 1f),
        )
        samples.forEach { (argb, expectedHsv) ->
            val converted = SolidColorHsv.fromArgb(argb)
            assertEquals(expectedHsv.hue, converted.hue, 0.5f)
            assertEquals(expectedHsv.saturation, converted.saturation, 0.01f)
            assertEquals(expectedHsv.value, converted.value, 0.01f)
            assertEquals(argb, expectedHsv.toOpaqueArgb())
        }
    }

    @Test
    fun `achromatic colors preserve fallback hue`() {
        val white = SolidColorHsv.fromArgb(0xFFFFFFFF.toInt(), fallbackHue = 210f)
        assertEquals(210f, white.hue, 0.01f)
        assertEquals(0f, white.saturation, 0.01f)
        assertEquals(1f, white.value, 0.01f)
        assertEquals(0xFFFFFFFF.toInt(), white.toOpaqueArgb())

        val black = SolidColorHsv.fromArgb(0xFF000000.toInt(), fallbackHue = 145f)
        assertEquals(145f, black.hue, 0.01f)
        assertEquals(0f, black.saturation, 0.01f)
        assertEquals(0f, black.value, 0.01f)
        assertEquals(0xFF000000.toInt(), black.toOpaqueArgb())
    }

    @Test
    fun `pastel color round-trips to opaque argb and formats hex`() {
        val pastelBlue = 0xFFD3E3FD.toInt()
        val hsv = SolidColorHsv.fromArgb(pastelBlue)
        val roundTripped = hsv.toOpaqueArgb()
        assertEquals(0xFF, roundTripped ushr 24)
        assertEquals(pastelBlue, roundTripped)
        assertEquals("#D3E3FD", formatSolidColorHex(pastelBlue))
        assertEquals("#00050F", formatSolidColorHex(0xFF00050F.toInt()))
    }
}
