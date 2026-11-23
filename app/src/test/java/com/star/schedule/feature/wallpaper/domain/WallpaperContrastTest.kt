package com.star.schedule.feature.wallpaper.domain

import com.star.schedule.feature.emoji.domain.EmojiPattern
import com.star.schedule.feature.emoji.domain.EmojiWallpaperColors
import com.star.schedule.feature.emoji.domain.EmojiWallpaperSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperContrastTest {
    @Test
    fun srgbRelativeLuminanceMatchesBoundaryColors() {
        assertEquals(0f, srgbRelativeLuminance(0xFF000000.toInt()), 0.0001f)
        assertEquals(1f, srgbRelativeLuminance(0xFFFFFFFF.toInt()), 0.0001f)
        assertTrue(isWallpaperRegionDark(srgbRelativeLuminance(0xFF1976D2.toInt())))
        assertFalse(isWallpaperRegionDark(srgbRelativeLuminance(0xFFC5E9C8.toInt())))
        assertFalse(isWallpaperRegionDark(srgbRelativeLuminance(0xFFFFEB3B.toInt())))
    }

    @Test
    fun designLuminanceProfileClassifiesSolidAndEmojiWallpapers() {
        val darkSolid = designLuminanceProfile(WallpaperDesign(solidArgb = 0xFF121826.toInt()))
        val lightSolid = designLuminanceProfile(WallpaperDesign(solidArgb = 0xFFF3F6FA.toInt()))
        assertTrue(isWallpaperRegionDark(darkSolid.top))
        assertTrue(isWallpaperRegionDark(darkSolid.leftColumn))
        assertFalse(isWallpaperRegionDark(lightSolid.top))
        assertFalse(isWallpaperRegionDark(lightSolid.leftColumn))

        val darkEmoji = designLuminanceProfile(
            WallpaperDesign(
                emoji = EmojiWallpaperSpec(
                    emojis = listOf("🐢"),
                    pattern = EmojiPattern.STACK,
                    scale = 0.5f,
                    colors = EmojiWallpaperColors(
                        backgroundArgb = 0xFF152218.toInt(),
                        emojiArgb = 0xFF8BD89B.toInt(),
                        seedArgb = 0xFF448B50.toInt(),
                    ),
                    seed = 10,
                ),
            ),
        )
        val lightEmoji = designLuminanceProfile(
            WallpaperDesign(
                emoji = EmojiWallpaperSpec(
                    emojis = listOf("🐢"),
                    pattern = EmojiPattern.STACK,
                    scale = 0.5f,
                    colors = EmojiWallpaperColors(
                        backgroundArgb = 0xFFC5E9C8.toInt(),
                        emojiArgb = 0xFF448B50.toInt(),
                        seedArgb = 0xFF448B50.toInt(),
                    ),
                    seed = 10,
                ),
            ),
        )
        assertTrue(isWallpaperRegionDark(darkEmoji.top))
        assertFalse(isWallpaperRegionDark(lightEmoji.top))
    }

    @Test
    fun computeLuminanceProfileDetectsBrightTopAndDarkLeftColumnIndependently() {
        val width = 100
        val height = 200
        val profile = computeLuminanceProfile(width, height) { x, y ->
            when {
                y < height * 0.25f -> 0xFFF5F8FC.toInt() // 顶部亮天空
                x < width * 0.20f -> 0xFF10141C.toInt() // 左侧暗森林
                else -> 0xFFE8EEF5.toInt() // 中部偏亮
            }
        }
        assertFalse(isWallpaperRegionDark(profile.top))
        assertTrue(isWallpaperRegionDark(profile.leftColumn))
        assertFalse(isWallpaperRegionDark(profile.center))
    }

    @Test
    fun computeLuminanceProfileHandlesEmptyDimensionsGracefully() {
        val profile = computeLuminanceProfile(0, 0) { _, _ -> 0 }
        assertEquals(0.5f, profile.top, 0.0001f)
        assertEquals(0.5f, profile.leftColumn, 0.0001f)
        assertEquals(0.5f, profile.center, 0.0001f)
    }
}
