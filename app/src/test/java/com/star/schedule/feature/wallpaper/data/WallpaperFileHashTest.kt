package com.star.schedule.feature.wallpaper.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WallpaperFileHashTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `missing or changed cache file does not match stored hash`() {
        val file = File(temporaryFolder.root, "wallpaper")
        assertFalse(WallpaperFileHash.matches(file, "a".repeat(64)))

        file.writeText("image-a")
        val originalHash = WallpaperFileHash.sha256(file)
        assertTrue(WallpaperFileHash.matches(file, originalHash))

        file.writeText("image-b")
        assertFalse(WallpaperFileHash.matches(file, originalHash))
    }
}
