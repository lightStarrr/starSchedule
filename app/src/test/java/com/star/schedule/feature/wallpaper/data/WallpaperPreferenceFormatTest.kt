package com.star.schedule.feature.wallpaper.data

import com.star.schedule.core.common.Constants
import com.star.schedule.feature.wallpaper.domain.WallpaperPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperPreferenceFormatTest {
    private val hash = "a".repeat(64)
    private val preference = WallpaperPreference(
        fileName = "wallpaper-$hash",
        contentHash = hash,
        seedArgb = 0xff336699.toInt(),
        extractorVersion = 1,
    )

    @Test
    fun `wallpaper key is scoped to timetable`() {
        assertEquals("timetable_wallpaper_42", Constants.timetableWallpaperPreference(42))
    }

    @Test
    fun `preference round trips and rejects invalid file names`() {
        assertEquals(preference, WallpaperPreferenceFormat.decode(WallpaperPreferenceFormat.encode(preference)))
        assertNull(WallpaperPreferenceFormat.decode("not json"))
        assertNull(
            WallpaperPreferenceFormat.decode(
                WallpaperPreferenceFormat.encode(preference.copy(fileName = "../outside")),
            ),
        )
    }

    @Test
    fun `seed cache depends on image hash and extractor version`() {
        assertTrue(WallpaperPreferenceFormat.canReuseSeed(preference, hash, 1))
        assertFalse(WallpaperPreferenceFormat.canReuseSeed(preference, "b".repeat(64), 1))
        assertFalse(WallpaperPreferenceFormat.canReuseSeed(preference, hash, 2))
        assertFalse(WallpaperPreferenceFormat.canReuseSeed(preference.copy(seedArgb = null), hash, 1))
    }
}
