package com.star.schedule.feature.wallpaper.domain

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 烘焙壁纸用的纯 Kotlin 高斯模糊（三次 box blur 近似）。
 *
 * Compose `Modifier.blur(radius.dp)` 走 RenderEffect.createBlurEffect，AOSP 的
 * RenderEffect JNI 里用 SkBlurMask::ConvertRadiusToSigma 把像素半径换成 sigma：
 * sigma = radius * 0.57735 + 0.5（见 frameworks/base graphics/RenderEffect.cpp），
 * 这里用同一个换算，烘焙效果才与预览页的运行时模糊一致。
 *
 * 只依赖 IntArray ARGB，可 JVM 单测。
 */

/** 与 SkBlurMask::ConvertRadiusToSigma 相同。 */
internal fun blurRadiusToSigma(radiusPx: Float): Float = 0.57735f * radiusPx + 0.5f

/**
 * 「boxes for gauss」标准公式：把高斯拆成 [count] 次等宽 box blur。
 * 返回每次 box 的宽度（奇数）。
 */
internal fun boxesForGauss(sigma: Float, count: Int = 3): IntArray {
    val wIdeal = sqrt(12f * sigma * sigma / count + 1f)
    var wl = floor(wIdeal).toInt()
    if (wl % 2 == 0) wl--
    wl = wl.coerceAtLeast(1)
    val wu = wl + 2
    val mIdeal = (12f * sigma * sigma - count * wl * wl - 4f * count * wl - 3f * count) /
        (-4f * wl - 4f)
    val m = mIdeal.roundToInt().coerceIn(0, count)
    return IntArray(count) { if (it < m) wl else wu }
}

/**
 * 对 [pixels]（ARGB，行优先，width*height）做高斯模糊并返回新数组。
 * [radiusPx] 为 Compose blur 的像素半径；<= 0 时原样返回。
 * 边缘用 clamp-replicate 处理。
 */
internal fun gaussianBlurArgb(
    pixels: IntArray,
    width: Int,
    height: Int,
    radiusPx: Float,
): IntArray {
    if (radiusPx <= 0f || width <= 0 || height <= 0 || pixels.size != width * height) {
        return pixels.copyOf()
    }
    var src = pixels.copyOf()
    var dst = IntArray(pixels.size)
    for (boxWidth in boxesForGauss(blurRadiusToSigma(radiusPx))) {
        val radius = boxWidth / 2
        if (radius <= 0) continue
        boxBlurHorizontal(src, dst, width, height, radius)
        val tmp = src; src = dst; dst = tmp
        boxBlurHorizontalTranspose(src, dst, width, height, radius)
        val tmp2 = src; src = dst; dst = tmp2
    }
    return src
}

/**
 * 烘焙尺寸：预览/主页显示时原图按 Cover 铺满窗口，缩放 s = max(winW/srcW, winH/srcH)。
 * 烘焙把整张原图（不裁切、保宽高比）放大到屏幕像素 1:1，模糊再按像素做，
 * 这样烘出来的文件按 Cover 显示时与运行时模糊逐像素对应。
 */
internal fun wallpaperBakeSize(
    sourceWidth: Int,
    sourceHeight: Int,
    windowWidth: Int,
    windowHeight: Int,
): Pair<Int, Int> {
    if (sourceWidth <= 0 || sourceHeight <= 0 || windowWidth <= 0 || windowHeight <= 0) {
        return sourceWidth.coerceAtLeast(1) to sourceHeight.coerceAtLeast(1)
    }
    val scale = max(
        windowWidth.toFloat() / sourceWidth,
        windowHeight.toFloat() / sourceHeight,
    )
    return (sourceWidth * scale).roundToInt().coerceAtLeast(1) to
        (sourceHeight * scale).roundToInt().coerceAtLeast(1)
}

/** 水平 box blur；垂直方向通过转置复用（连续内存访问更快）。 */
private fun boxBlurHorizontal(
    src: IntArray,
    dst: IntArray,
    width: Int,
    height: Int,
    radius: Int,
) {
    val divisor = 2 * radius + 1
    val half = divisor / 2
    for (y in 0 until height) {
        val row = y * width
        var alpha = 0L
        var red = 0L
        var green = 0L
        var blue = 0L
        for (i in -radius..radius) {
            val pixel = src[row + i.coerceIn(0, width - 1)]
            alpha += pixel ushr 24 and 0xff
            red += pixel ushr 16 and 0xff
            green += pixel ushr 8 and 0xff
            blue += pixel and 0xff
        }
        for (x in 0 until width) {
            // 四舍五入除法：多次 box 叠加时截断误差会系统性吃掉能量。
            dst[row + x] = (((alpha + half) / divisor).toInt() shl 24) or
                (((red + half) / divisor).toInt() shl 16) or
                (((green + half) / divisor).toInt() shl 8) or
                ((blue + half) / divisor).toInt()
            val removed = src[row + (x - radius).coerceAtLeast(0)]
            val added = src[row + (x + radius + 1).coerceAtMost(width - 1)]
            alpha += (added ushr 24 and 0xff) - (removed ushr 24 and 0xff)
            red += (added ushr 16 and 0xff) - (removed ushr 16 and 0xff)
            green += (added ushr 8 and 0xff) - (removed ushr 8 and 0xff)
            blue += (added and 0xff) - (removed and 0xff)
        }
    }
}

/** 垂直 box blur：按列滑动窗口，写入仍是行优先布局。 */
private fun boxBlurHorizontalTranspose(
    src: IntArray,
    dst: IntArray,
    width: Int,
    height: Int,
    radius: Int,
) {
    val divisor = 2 * radius + 1
    val half = divisor / 2
    for (x in 0 until width) {
        var alpha = 0L
        var red = 0L
        var green = 0L
        var blue = 0L
        for (i in -radius..radius) {
            val pixel = src[i.coerceIn(0, height - 1) * width + x]
            alpha += pixel ushr 24 and 0xff
            red += pixel ushr 16 and 0xff
            green += pixel ushr 8 and 0xff
            blue += pixel and 0xff
        }
        for (y in 0 until height) {
            dst[y * width + x] = (((alpha + half) / divisor).toInt() shl 24) or
                (((red + half) / divisor).toInt() shl 16) or
                (((green + half) / divisor).toInt() shl 8) or
                ((blue + half) / divisor).toInt()
            val removed = src[(y - radius).coerceAtLeast(0) * width + x]
            val added = src[min(y + radius + 1, height - 1) * width + x]
            alpha += (added ushr 24 and 0xff) - (removed ushr 24 and 0xff)
            red += (added ushr 16 and 0xff) - (removed ushr 16 and 0xff)
            green += (added ushr 8 and 0xff) - (removed ushr 8 and 0xff)
            blue += (added and 0xff) - (removed and 0xff)
        }
    }
}
