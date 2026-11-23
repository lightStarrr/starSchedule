package com.star.schedule.feature.wallpaper.data

import com.star.schedule.feature.emoji.domain.EmojiPattern
import com.star.schedule.feature.emoji.domain.EmojiWallpaperColors
import com.star.schedule.feature.emoji.domain.EmojiWallpaperSpec
import com.star.schedule.feature.wallpaper.domain.RecentWallpaperEntry
import com.star.schedule.feature.wallpaper.domain.WallpaperDesign
import com.star.schedule.feature.wallpaper.domain.WallpaperState
import com.star.schedule.feature.wallpaper.domain.promoteRecentEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedWallpaperTest {
    private val design = WallpaperDesign(
        emoji = EmojiWallpaperSpec(
            emojis = listOf("🐢", "✌", "👨‍👩‍👧"),
            pattern = EmojiPattern.PRISM,
            scale = 17f / 24,
            colors = EmojiWallpaperColors(0xffc5e9c8.toInt(), 0xff448b50.toInt(), 0xff448b50.toInt()),
            seed = 10,
        ),
        blurRadius = 12f,
    )

    @Test
    fun `emoji parameters and blur survive persistence without a photo path`() {
        val encoded = WallpaperPreferenceFormat.encodeGenerated(design)
        assertEquals(WallpaperState.Generated(design), WallpaperPreferenceFormat.decodeGenerated(encoded))
        assertFalse(encoded.contains("fileName"))
        assertFalse(encoded.contains("contentHash"))
        assertEquals(
            WallpaperState.Generated(WallpaperDesign(solidArgb = 0xff112233.toInt())),
            WallpaperPreferenceFormat.decodeGenerated(
                WallpaperPreferenceFormat.encodeGenerated(WallpaperDesign(solidArgb = 0xff112233.toInt())),
            ),
        )
        val noScrimSolid = WallpaperDesign(solidArgb = 0xff112233.toInt(), scrimEnabled = false)
        assertEquals(
            WallpaperState.Generated(noScrimSolid),
            WallpaperPreferenceFormat.decodeGenerated(
                WallpaperPreferenceFormat.encodeGenerated(noScrimSolid),
            ),
        )
        val noScrimEmoji = design.copy(scrimEnabled = false)
        assertEquals(
            WallpaperState.Generated(noScrimEmoji),
            WallpaperPreferenceFormat.decodeGenerated(
                WallpaperPreferenceFormat.encodeGenerated(noScrimEmoji),
            ),
        )
    }

    @Test
    fun `invalid generated content does not become an active wallpaper`() {
        listOf(
            WallpaperDesign(),
            design.copy(solidArgb = 0xff123456.toInt()),
            design.copy(blurRadius = -1f),
            design.copy(emoji = design.emoji!!.copy(emojis = emptyList())),
            design.copy(emoji = design.emoji!!.copy(scale = 2f)),
        ).forEach {
            assertFalse(it.isValid())
            assertNull(WallpaperPreferenceFormat.decodeGenerated(WallpaperPreferenceFormat.encodeGenerated(it)))
        }
        assertNull(WallpaperPreferenceFormat.decodeGenerated("generated:broken"))
        assertNull(WallpaperPreferenceFormat.decodeGenerated(""))
    }

    @Test
    fun `generated recents keep distinct designs and share the history limit with photos`() {
        fun entry(design: WallpaperDesign) =
            RecentWallpaperEntry(kind = RecentWallpaperEntry.KIND_GENERATED, design = design)
        val first = entry(design)
        val second = entry(design.copy(blurRadius = 0f))
        val solid = entry(WallpaperDesign(solidArgb = 0xff112233.toInt()))
        val promoted = promoteRecentEntry(listOf(first, second), solid, maxImages = 2)
        assertEquals(listOf(solid, first), promoted)
        assertEquals(listOf(first, second), promoteRecentEntry(listOf(first, second), first))
        assertEquals(promoted, WallpaperRecentsFormat.decode(WallpaperRecentsFormat.encode(promoted)))
        assertTrue(design.isValid())
    }
}
