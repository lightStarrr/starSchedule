package com.star.schedule.feature.wallpaper.presentation

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class GeneratedWallpaperRenderSizeTest {
    @Test
    fun returnsZeroWhenCanvasIsEmpty() {
        assertEquals(IntSize.Zero, computeEmojiRenderSize(IntSize.Zero, IntSize(1080, 2400)))
        assertEquals(IntSize.Zero, computeEmojiRenderSize(IntSize(0, 360), null))
    }

    @Test
    fun returnsExactCanvasSizeWithoutPreviewViewport() {
        assertEquals(
            IntSize(540, 1200),
            computeEmojiRenderSize(IntSize(540, 1200), previewViewport = null),
        )
    }

    @Test
    fun keepsStableRenderSizeDuringStripWidthAnimation() {
        val viewport = IntSize(1080, 2400)
        val collapsed = computeEmojiRenderSize(IntSize(144, 360), viewport)
        val midAnimation = computeEmojiRenderSize(IntSize(252, 360), viewport)
        val expanded = computeEmojiRenderSize(IntSize(360, 360), viewport)

        assertEquals(IntSize(360, 800), collapsed)
        assertEquals(collapsed, midAnimation)
        assertEquals(collapsed, expanded)
    }

    @Test
    fun matchesPortraitViewportForSquareThumbnailTestCase() {
        assertEquals(
            IntSize(300, 500),
            computeEmojiRenderSize(IntSize(300, 300), IntSize(300, 500)),
        )
    }

    @Test
    fun keepsStableRenderSizeInLandscapeViewport() {
        val viewport = IntSize(2400, 1080)
        val collapsed = computeEmojiRenderSize(IntSize(144, 360), viewport)
        val expanded = computeEmojiRenderSize(IntSize(360, 360), viewport)

        assertEquals(IntSize(800, 360), collapsed)
        assertEquals(collapsed, expanded)
    }
}
