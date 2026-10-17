package com.star.schedule.feature.wallpaper.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperCropTest {
    private val targetWidth = 1344
    private val targetHeight = 2992

    @Test
    fun cropsLandscapePhotoToPortraitScreen() {
        val spec = calculateWallpaperCrop(
            sourceWidth = 4000,
            sourceHeight = 3000,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
        )

        assertEquals(WallpaperCropSpec(left = 1326, top = 0, width = 1348, height = 3000, sampleSize = 1), spec)
    }

    @Test
    fun downsamplesHugePhotoDownToScreenSize() {
        val spec = calculateWallpaperCrop(
            sourceWidth = 8000,
            sourceHeight = 6000,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
        )

        assertEquals(WallpaperCropSpec(left = 2652, top = 0, width = 2695, height = 6000, sampleSize = 2), spec)
        assertEquals(1347, spec.width / spec.sampleSize)
        assertEquals(3000, spec.height / spec.sampleSize)
    }

    @Test
    fun cropsTallPhotoHorizontally() {
        val spec = calculateWallpaperCrop(
            sourceWidth = 3000,
            sourceHeight = 4000,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
        )

        assertEquals(WallpaperCropSpec(left = 601, top = 0, width = 1797, height = 4000, sampleSize = 1), spec)
    }

    @Test
    fun keepsSmallerImageUncropped() {
        val spec = calculateWallpaperCrop(
            sourceWidth = 800,
            sourceHeight = 600,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
        )

        assertEquals(WallpaperCropSpec(left = 0, top = 0, width = 800, height = 600, sampleSize = 1), spec)
    }

    @Test
    fun fallsBackToFullImageForInvalidInput() {
        val spec = calculateWallpaperCrop(
            sourceWidth = 0,
            sourceHeight = 0,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
        )

        assertEquals(WallpaperCropSpec(left = 0, top = 0, width = 0, height = 0, sampleSize = 1), spec)
    }

    @Test
    fun keepsImageThatCannotCoverScreen() {
        val spec = calculateWallpaperCrop(
            sourceWidth = 6000,
            sourceHeight = 2500,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
        )

        assertEquals(WallpaperCropSpec(left = 0, top = 0, width = 6000, height = 2500, sampleSize = 1), spec)
    }

    @Test
    fun decodedSizeNeverFallsBelowScreen() {
        val sources = listOf(
            4000 to 3000,
            8000 to 6000,
            3000 to 4000,
            5000 to 5000,
            12000 to 9000,
            2160 to 3840,
        )

        sources.forEach { (sourceWidth, sourceHeight) ->
            val spec = calculateWallpaperCrop(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
            )
            val decodedWidth = spec.width / spec.sampleSize
            val decodedHeight = spec.height / spec.sampleSize

            assertTrue(
                "source=$sourceWidth x $sourceHeight decoded=$decodedWidth x $decodedHeight",
                decodedWidth >= targetWidth && decodedHeight >= targetHeight,
            )
            assertTrue(
                "source=$sourceWidth x $sourceHeight decoded=$decodedWidth x $decodedHeight",
                decodedWidth < targetWidth * 2 && decodedHeight < targetHeight * 2,
            )
            assertTrue(
                "source=$sourceWidth x $sourceHeight rect exceeds source",
                spec.left + spec.width <= sourceWidth && spec.top + spec.height <= sourceHeight,
            )
        }
    }
}
