package com.star.schedule.feature.emoji.domain

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiWallpaperLayoutTest {
    private fun layout(pattern: EmojiPattern, step: Int = 12, count: Int = 2, width: Float = 1080f, height: Float = 2400f) =
        emojiWallpaperLayout(pattern, count, step / 24f, EMOJI_LAYOUT_SEED, width, height)

    @Test
    fun `every pattern and all 25 stops produce repeatable valid geometry`() {
        EmojiPattern.entries.forEach { pattern ->
            for (step in 0..24) {
                val result = layout(pattern, step)
                assertEquals(result, layout(pattern, step))
                assertFalse("$pattern $step is empty", result.placements.isEmpty())
                result.placements.forEach {
                    assertTrue(it.centerX.isFinite() && it.centerY.isFinite() && it.size.isFinite())
                    assertTrue(it.size > 0)
                    assertTrue(it.emojiIndex in 0..1)
                }
                assertEquals(pattern == EmojiPattern.PRISM, result.tiling != null)
            }
        }
    }

    @Test
    fun `plus and minus walk through exactly 25 distinct stops`() {
        var scale = 0f
        for (step in 0..24) {
            assertEquals(step, emojiScaleStep(scale))
            scale = quantizedEmojiScale(scale + 1f / 24f)
        }
        for (step in 24 downTo 0) {
            assertEquals(step, emojiScaleStep(scale))
            scale = quantizedEmojiScale(scale - 1f / 24f)
        }
        assertEquals(0f, quantizedEmojiScale(-2f))
        assertEquals(1f, quantizedEmojiScale(2f))
        assertEquals(0f, quantizedEmojiScale(Float.NaN))
    }

    @Test
    fun `reference size curve preserves the larger changes in the final stops`() {
        assertEquals(45f, layout(EmojiPattern.LOTUS, 0).placements.first().size)
        assertEquals(84f, layout(EmojiPattern.LOTUS, 12).placements.first().size)
        assertEquals(283f, layout(EmojiPattern.LOTUS, 24).placements.first().size)
        assertEquals(60f, layout(EmojiPattern.STACK, 0).placements.first().size)
        assertEquals(663f, layout(EmojiPattern.STACK, 24).placements.first().size)
    }

    @Test
    fun `lotus retains a fixed center and golden angle spiral at every stop`() {
        for (step in 0..24) {
            val result = layout(EmojiPattern.LOTUS, step)
            val size = result.placements.first().size
            val center = result.placements.first()
            assertEquals(540 + size / 2, center.centerX, .01f)
            assertEquals(1200f, center.centerY, .01f)
            val next = result.placements[1]
            val radius = hypot(next.centerX - 540, next.centerY - 1200)
            assertEquals(size * 1.1f, radius, .01f)
            assertTrue(result.placements.all { it.rotationDegrees == 0f })
        }
    }

    @Test
    fun `mosaic squares never overlap and retain multiple sizes`() {
        for (step in 0..24) {
            val points = layout(EmojiPattern.MOSAIC, step).placements
            assertTrue(points.map { it.size }.distinct().size > 1)
            points.forEachIndexed { index, a ->
                points.drop(index + 1).forEach { b ->
                    val side = (a.size + b.size) / 2
                    assertTrue(abs(a.centerX - b.centerX) >= side || abs(a.centerY - b.centerY) >= side)
                }
            }
        }
    }

    @Test
    fun `scatter has air between rotated elements for every stop`() {
        for (step in 0..24) {
            val points = layout(EmojiPattern.SCATTER, step).placements
            points.forEachIndexed { index, a ->
                assertTrue(a.rotationDegrees in -30f..30f)
                points.drop(index + 1).forEach { b ->
                    assertTrue(hypot(a.centerX - b.centerX, a.centerY - b.centerY) > (a.size + b.size) * .85f)
                }
            }
        }
    }

    @Test
    fun `prism mirror grid stays fixed across stops while glyph size changes`() {
        val first = layout(EmojiPattern.PRISM, 0)
        val last = layout(EmojiPattern.PRISM, 24)
        assertNotNull(first.tiling)
        assertEquals(first.tiling, last.tiling)
        assertTrue(first.placements.maxOf { it.size } < last.placements.maxOf { it.size })
        first.tiling!!.transforms.forEach {
            assertEquals(1f, abs(it.a * it.d - it.b * it.c), .0001f)
        }
        assertTrue(first.tiling.transforms.any { it.a * it.d - it.b * it.c < 0 })
    }

    @Test
    fun `preview and export geometry scales proportionally`() {
        EmojiPattern.entries.forEach { pattern ->
            for (step in 0..24) {
                val large = layout(pattern, step)
                val small = layout(pattern, step, width = 270f, height = 600f)
                assertEquals(large.placements.size, small.placements.size)
                large.placements.zip(small.placements).forEach { (a, b) ->
                    assertEquals(a.centerX / 4, b.centerX, .001f)
                    assertEquals(a.centerY / 4, b.centerY, .001f)
                    assertEquals(a.size / 4, b.size, .001f)
                }
            }
        }
    }

    @Test
    fun `single emoji and empty selection are safe on portrait and landscape canvases`() {
        EmojiPattern.entries.forEach { pattern ->
            listOf(1080f to 2400f, 2400f to 1080f, 720f to 720f).forEach { (width, height) ->
                for (step in 0..24) {
                    assertTrue(layout(pattern, step, 0, width, height).placements.isEmpty())
                    val points = layout(pattern, step, 1, width, height).placements
                    assertTrue(points.isNotEmpty())
                    assertTrue(points.all { it.emojiIndex == 0 })
                }
            }
            assertTrue(layout(pattern, width = 0f).placements.isEmpty())
        }
    }
}
