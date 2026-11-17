@file:SuppressLint("RestrictedApi")

package com.star.schedule.core.designsystem.theme

import android.annotation.SuppressLint
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * 照片取色由官方 Material Color Utilities（SchemeTonalSpot + MaterialDynamicColors）生成。
 * MCU 是纯 Java 实现，JVM 单测可直接加载。
 */
class SeedColorSchemeTest {
    private val seed = 0xFF4ED8E9.toInt()

    @Test
    fun `primary comes from the official tonal spot scheme`() {
        val scheme = SchemeTonalSpot(Hct.fromInt(seed), false, 0.0)
        val expected = MaterialDynamicColors().primary().getArgb(scheme)

        assertEquals(expected, seedColorScheme(seed, isDark = false).primary.toArgb())
    }

    @Test
    fun `dark primary comes from the official tonal spot scheme`() {
        val scheme = SchemeTonalSpot(Hct.fromInt(seed), true, 0.0)
        val expected = MaterialDynamicColors().primary().getArgb(scheme)

        assertEquals(expected, seedColorScheme(seed, isDark = true).primary.toArgb())
    }

    @Test
    fun `surface container roles no longer fall back to the baseline scheme`() {
        assertNotEquals(
            lightColorScheme().surfaceContainerHigh.toArgb(),
            seedColorScheme(seed, isDark = false).surfaceContainerHigh.toArgb(),
        )
        assertNotEquals(
            darkColorScheme().surfaceContainerHigh.toArgb(),
            seedColorScheme(seed, isDark = true).surfaceContainerHigh.toArgb(),
        )
    }

    @Test
    fun `container luminance descends in light and ascends in dark`() {
        val light = seedColorScheme(seed, isDark = false)
        assertLadder(
            descending = true,
            listOf(
                light.surfaceContainerLowest,
                light.surfaceContainerLow,
                light.surfaceContainer,
                light.surfaceContainerHigh,
                light.surfaceContainerHighest,
            ),
        )
        val dark = seedColorScheme(seed, isDark = true)
        assertLadder(
            descending = false,
            listOf(
                dark.surfaceContainerLowest,
                dark.surfaceContainerLow,
                dark.surfaceContainer,
                dark.surfaceContainerHigh,
                dark.surfaceContainerHighest,
            ),
        )
    }

    private fun assertLadder(descending: Boolean, colors: List<Color>) {
        for (index in 0 until colors.size - 1) {
            val a = colors[index].relativeLuminance()
            val b = colors[index + 1].relativeLuminance()
            assertTrue(
                "step $index: $a vs $b",
                if (descending) a > b else a < b,
            )
        }
    }

    /** CIE 相对亮度，用于比较表面阶的明暗顺序。 */
    private fun Color.relativeLuminance(): Float {
        fun linear(component: Float) = component.toDouble().pow(2.2).toFloat()
        return 0.2126f * linear(red) + 0.7152f * linear(green) + 0.0722f * linear(blue)
    }
}
