package com.star.schedule.feature.wallpaper.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WallpaperBlurTest {

    @Test
    fun `zero radius returns the pixels unchanged`() {
        val pixels = intArrayOf(0xff112233.toInt(), 0xff445566.toInt(), 0xff778899.toInt())

        assertArrayEquals(pixels, gaussianBlurArgb(pixels, 3, 1, 0f))
        assertArrayEquals(pixels, gaussianBlurArgb(pixels, 3, 1, -2f))
    }

    @Test
    fun `uniform image stays uniform`() {
        val color = 0xff336699.toInt()
        val pixels = IntArray(32 * 24) { color }

        val blurred = gaussianBlurArgb(pixels, 32, 24, 8f)

        assertTrue(blurred.all { it == color })
    }

    @Test
    fun `impulse stays symmetric and keeps its energy`() {
        val width = 41
        val height = 41
        val pixels = IntArray(width * height) { 0xff000000.toInt() }
        val center = (height / 2) * width + width / 2
        pixels[center] = 0xffffffff.toInt()

        val blurred = gaussianBlurArgb(pixels, width, height, 6f)

        fun luma(x: Int, y: Int): Int = blurred[y * width + x] and 0xff
        // 左右、上下对称
        for (d in 1..6) {
            assertEquals(luma(width / 2 + d, height / 2), luma(width / 2 - d, height / 2))
            assertEquals(luma(width / 2, height / 2 + d), luma(width / 2, height / 2 - d))
        }
        // 中心最亮、向外衰减；远处已衰减到 0 而中心仍有亮度
        assertTrue(luma(width / 2, height / 2) >= luma(width / 2 + 4, height / 2))
        assertTrue(luma(width / 2, height / 2) > 0)
        assertEquals(0, luma(width / 2, 2))
        // 能量大体守恒：整数 StackBlur 每次 pass 做 8bit 量化，
        // 单点脉冲这种极端输入会损失 ~10%（Skia 内部用定点数故无此损失，
        // 对真实照片该量化误差远低于 JPEG 压缩噪声，可忽略）。
        val inputEnergy = pixels.sumOf { (it and 0xff).toLong() }
        val outputEnergy = blurred.sumOf { (it and 0xff).toLong() }
        assertTrue(
            "energy $inputEnergy -> $outputEnergy",
            abs(outputEnergy - inputEnergy).toFloat() / inputEnergy < 0.15f,
        )
    }

    @Test
    fun `sample size is the largest power of two not undershooting the target`() {
        // 目标大于源 → 1（不采样）
        assertEquals(1, wallpaperSampleSize(900, 1600, 1080, 2400))
        // 源是目标的 4.5 倍 → 4（8 会把解码结果缩到目标以下）
        assertEquals(4, wallpaperSampleSize(9000, 18000, 2000, 4000))
        // 恰好 4 倍 → 4
        assertEquals(4, wallpaperSampleSize(8000, 8000, 2000, 2000))
        // 非法输入 → 1
        assertEquals(1, wallpaperSampleSize(0, 0, 100, 100))
    }

    @Test
    fun `bake size keeps the source fully covering the window`() {
        // 横图：宽度方向被放大到超过窗口，高度正好铺满。
        assertEquals(
            3000 to 2400,
            wallpaperBakeSize(2000, 1600, windowWidth = 1080, windowHeight = 2400),
        )
        // 竖图：高度方向放大超过窗口，宽度铺满。
        assertEquals(
            1080 to 1920,
            wallpaperBakeSize(900, 1600, windowWidth = 1080, windowHeight = 1600),
        )
    }
}
