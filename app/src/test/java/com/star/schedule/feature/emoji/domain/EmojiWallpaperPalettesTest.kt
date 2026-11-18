package com.star.schedule.feature.emoji.domain

import android.annotation.SuppressLint
import com.google.android.material.color.utilities.Hct
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@SuppressLint("RestrictedApi")
class EmojiWallpaperPalettesTest {

    @Test
    fun `ten fixed hues produce twenty palettes`() {
        assertEquals(20, emojiWallpaperPalettes().size)
    }

    @Test
    fun `system seed adds two palettes in front`() {
        val palettes = emojiWallpaperPalettes(systemSeedArgb = 0xFF6750A4.toInt())
        assertEquals(22, palettes.size)
        assertEquals(0xFF6750A4.toInt(), palettes[0].seedArgb)
        assertEquals(0xFF6750A4.toInt(), palettes[1].seedArgb)
    }

    @Test
    fun `emoji color is far enough from the background in HCT tone`() {
        emojiWallpaperPalettes(systemSeedArgb = 0xFF6750A4.toInt()).forEach { colors ->
            val bgTone = Hct.fromInt(colors.backgroundArgb).tone
            val emojiTone = Hct.fromInt(colors.emojiArgb).tone
            assertTrue(
                "tone gap ${kotlin.math.abs(bgTone - emojiTone)} too small",
                kotlin.math.abs(bgTone - emojiTone) >= 15.0,
            )
        }
    }

    @Test
    fun `seeds keep their own identity`() {
        val seeds = emojiWallpaperPalettes().map { it.seedArgb }.distinct()
        assertEquals(10, seeds.size)
    }
}
