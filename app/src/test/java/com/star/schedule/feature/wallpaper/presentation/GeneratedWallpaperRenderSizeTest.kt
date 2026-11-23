package com.star.schedule.feature.wallpaper.presentation

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class GeneratedWallpaperRenderSizeTest {
    @Test
    fun returnsEmptyWhenCanvasIsEmpty() {
        assertEquals(
            EmojiWallpaperRenderPlan.Empty,
            computeEmojiRenderPlan(IntSize.Zero, IntSize(1080, 2400)),
        )
        assertEquals(
            EmojiWallpaperRenderPlan.Empty,
            computeEmojiRenderPlan(IntSize(0, 360), null),
        )
    }

    @Test
    fun returnsExactCanvasPlanWithoutPreviewViewport() {
        assertEquals(
            EmojiWallpaperRenderPlan(
                bitmapWidth = 540,
                bitmapHeight = 1200,
                layoutWidth = 540,
                layoutHeight = 1200,
            ),
            computeEmojiRenderPlan(IntSize(540, 1200), previewViewport = null),
        )
    }

    @Test
    fun keepsStableCroppedSquarePlanDuringStripWidthAnimation() {
        val viewport = IntSize(1080, 2400)
        val collapsed = computeEmojiRenderPlan(IntSize(144, 360), viewport)
        val midAnimation = computeEmojiRenderPlan(IntSize(252, 360), viewport)
        val expanded = computeEmojiRenderPlan(IntSize(360, 360), viewport)

        assertEquals(
            EmojiWallpaperRenderPlan(
                bitmapWidth = 360,
                bitmapHeight = 360,
                layoutWidth = 360,
                layoutHeight = 800,
            ),
            collapsed,
        )
        assertEquals(collapsed, midAnimation)
        assertEquals(collapsed, expanded)
    }

    @Test
    fun matchesCroppedPortraitPlanForSquareThumbnailTestCase() {
        assertEquals(
            EmojiWallpaperRenderPlan(
                bitmapWidth = 300,
                bitmapHeight = 300,
                layoutWidth = 300,
                layoutHeight = 500,
            ),
            computeEmojiRenderPlan(IntSize(300, 300), IntSize(300, 500)),
        )
    }

    @Test
    fun keepsStableCroppedSquarePlanInLandscapeViewport() {
        val viewport = IntSize(2400, 1080)
        val collapsed = computeEmojiRenderPlan(IntSize(144, 360), viewport)
        val expanded = computeEmojiRenderPlan(IntSize(360, 360), viewport)

        assertEquals(
            EmojiWallpaperRenderPlan(
                bitmapWidth = 360,
                bitmapHeight = 360,
                layoutWidth = 800,
                layoutHeight = 360,
            ),
            collapsed,
        )
        assertEquals(collapsed, expanded)
    }
}
