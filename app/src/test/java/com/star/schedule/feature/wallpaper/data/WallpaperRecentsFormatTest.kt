package com.star.schedule.feature.wallpaper.data

import com.star.schedule.feature.wallpaper.domain.RecentWallpaperEntry
import com.star.schedule.feature.wallpaper.domain.RecentWallpaperEntry.Companion.KIND_DEFAULT
import com.star.schedule.feature.wallpaper.domain.RecentWallpaperEntry.Companion.KIND_IMAGE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WallpaperRecentsFormatTest {
    private val hash = "a".repeat(64)

    @Test
    fun `missing or unparsable value decodes to null`() {
        assertNull(WallpaperRecentsFormat.decode(null))
        assertNull(WallpaperRecentsFormat.decode(""))
        assertNull(WallpaperRecentsFormat.decode("not json"))
    }

    @Test
    fun `entries round trip`() {
        val entries = listOf(
            RecentWallpaperEntry(
                kind = KIND_IMAGE,
                fileName = "wallpaper-$hash",
                contentHash = hash,
                seedArgb = 0xff336699.toInt(),
                scrimEnabled = false,
            ),
            RecentWallpaperEntry(kind = KIND_DEFAULT),
        )

        assertEquals(
            entries,
            WallpaperRecentsFormat.decode(WallpaperRecentsFormat.encode(entries)),
        )
    }

    @Test
    fun `invalid entries are dropped individually`() {
        val raw = """[
            {"kind":"image","fileName":"../escape","contentHash":"$hash"},
            {"kind":"image","fileName":"wallpaper-$hash","contentHash":"not-a-hash"},
            {"kind":"image","fileName":"wallpaper-$hash"},
            {"kind":"other"},
            {"kind":"default"},
            {"kind":"image","fileName":"wallpaper-$hash","contentHash":"$hash"}
        ]""".trimIndent()

        assertEquals(
            listOf(
                RecentWallpaperEntry(kind = KIND_DEFAULT),
                RecentWallpaperEntry(
                    kind = KIND_IMAGE,
                    fileName = "wallpaper-$hash",
                    contentHash = hash,
                ),
            ),
            WallpaperRecentsFormat.decode(raw),
        )
    }
}
