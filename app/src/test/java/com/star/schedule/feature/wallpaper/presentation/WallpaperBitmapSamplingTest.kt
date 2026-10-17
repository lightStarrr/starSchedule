package com.star.schedule.feature.wallpaper.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperBitmapSamplingTest {
    @Test
    fun keepsSmallImageAtOriginalSize() {
        assertEquals(1, calculateInSampleSize(1200, 800, targetLongEdge = 2400))
        assertEquals(1, calculateInSampleSize(2400, 1600, targetLongEdge = 2400))
    }

    @Test
    fun halvesLargeImageOnceTargetIsExceeded() {
        assertEquals(2, calculateInSampleSize(4000, 3000, targetLongEdge = 2400))
        assertEquals(2, calculateInSampleSize(4800, 3200, targetLongEdge = 2400))
    }

    @Test
    fun keepsHalvingForExtremelyLargeImage() {
        assertEquals(4, calculateInSampleSize(8000, 6000, targetLongEdge = 2400))
        assertEquals(16, calculateInSampleSize(20000, 15000, targetLongEdge = 2400))
        assertEquals(32, calculateInSampleSize(40000, 30000, targetLongEdge = 2400))
    }

    @Test
    fun doesNotExceedTargetLongEdge() {
        val cases = listOf(
            2400 to 2400,
            4000 to 2000,
            5000 to 1250,
            12000 to 1500,
        )
        cases.forEach { (sourceLongEdge, expectedLongEdge) ->
            val target = 2400
            val sampleSize = calculateInSampleSize(sourceLongEdge, sourceLongEdge, target)
            assertEquals(
                "source=$sourceLongEdge",
                expectedLongEdge,
                sourceLongEdge / sampleSize,
            )
        }
    }

    @Test
    fun fallsBackToOriginalSizeForInvalidInput() {
        assertEquals(1, calculateInSampleSize(0, 0, targetLongEdge = 2400))
        assertEquals(1, calculateInSampleSize(4000, 3000, targetLongEdge = 0))
        assertEquals(1, calculateInSampleSize(-1, 3000, targetLongEdge = 2400))
    }
}
