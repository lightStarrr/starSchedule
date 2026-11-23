package com.star.schedule.feature.wallpaper.domain

import kotlin.math.pow

/**
 * 壁纸各文字承载区的平均 WCAG 相对亮度（0f = 纯黑，1f = 纯白）：
 * - [top]：顶栏与星期表头区（顶部约 24%）
 * - [leftColumn]：左侧节次与时间栏（左侧约 16%、纵向 18%..95%）
 * - [center]：中部空状态文案区（横向 15%..85%、纵向 35%..65%）
 */
data class WallpaperLuminanceProfile(
    val top: Float,
    val leftColumn: Float,
    val center: Float,
) {
    companion object {
        fun uniform(luminance: Float): WallpaperLuminanceProfile {
            val clamped = luminance.coerceIn(0f, 1f)
            return WallpaperLuminanceProfile(
                top = clamped,
                leftColumn = clamped,
                center = clamped,
            )
        }
    }
}

// 相对亮度低于该阈值（约对应感知明度 60% 以下的深色/中深色）时使用浅色字，否则使用深色字。
internal const val WALLPAPER_DARK_BACKGROUND_LUMINANCE_THRESHOLD = 0.28f

// 预计算 sRGB 8-bit 通道到线性光的查找表，消除运行时 Math.pow 开销。
private val SRGB_TO_LINEAR = FloatArray(256) { index ->
    val channel = index / 255.0
    val linear = if (channel <= 0.04045) {
        channel / 12.92
    } else {
        ((channel + 0.055) / 1.055).pow(2.4)
    }
    linear.toFloat()
}

internal fun srgbRelativeLuminance(argb: Int): Float {
    val r = SRGB_TO_LINEAR[(argb ushr 16) and 0xFF]
    val g = SRGB_TO_LINEAR[(argb ushr 8) and 0xFF]
    val b = SRGB_TO_LINEAR[argb and 0xFF]
    return (0.2126f * r + 0.7152f * g + 0.0722f * b).coerceIn(0f, 1f)
}

internal fun isWallpaperRegionDark(luminance: Float): Boolean =
    luminance < WALLPAPER_DARK_BACKGROUND_LUMINANCE_THRESHOLD

/**
 * 纯色与表情壁纸无需任何像素采样，直接 O(1) 计算亮度档案。
 * 表情壁纸由大面积底色主导，按 82% 底色 + 18% 表情色加权估算视觉亮度。
 */
internal fun designLuminanceProfile(design: WallpaperDesign): WallpaperLuminanceProfile {
    val luminance = when {
        design.solidArgb != null -> srgbRelativeLuminance(design.solidArgb)
        design.emoji != null -> {
            val background = srgbRelativeLuminance(design.emoji.colors.backgroundArgb)
            val emoji = srgbRelativeLuminance(design.emoji.colors.emojiArgb)
            background * 0.82f + emoji * 0.18f
        }
        else -> 0.5f
    }
    return WallpaperLuminanceProfile.uniform(luminance)
}

/**
 * 对一张已裁切至屏幕比例的图像做固定网格采样（最多约 24×40 = 960 个点），
 * 单次遍历统计顶部、左侧时间栏与中部三个文字区域的平均相对亮度。
 */
internal fun computeLuminanceProfile(
    width: Int,
    height: Int,
    pixelAt: (x: Int, y: Int) -> Int,
): WallpaperLuminanceProfile {
    if (width <= 0 || height <= 0) {
        return WallpaperLuminanceProfile.uniform(0.5f)
    }
    val sampleCols = minOf(width, SAMPLE_COLS)
    val sampleRows = minOf(height, SAMPLE_ROWS)

    var allSum = 0.0
    var allCount = 0
    var topSum = 0.0
    var topCount = 0
    var leftSum = 0.0
    var leftCount = 0
    var centerSum = 0.0
    var centerCount = 0

    for (row in 0 until sampleRows) {
        val ny = (row + 0.5f) / sampleRows
        val y = (ny * height).toInt().coerceIn(0, height - 1)
        val inTop = ny <= TOP_REGION_MAX_Y
        val inLeftY = ny in LEFT_REGION_MIN_Y..LEFT_REGION_MAX_Y
        val inCenterY = ny in CENTER_REGION_MIN_Y..CENTER_REGION_MAX_Y

        for (col in 0 until sampleCols) {
            val nx = (col + 0.5f) / sampleCols
            val x = (nx * width).toInt().coerceIn(0, width - 1)
            val lum = srgbRelativeLuminance(pixelAt(x, y)).toDouble()

            allSum += lum
            allCount++
            if (inTop) {
                topSum += lum
                topCount++
            }
            if (inLeftY && nx <= LEFT_REGION_MAX_X) {
                leftSum += lum
                leftCount++
            }
            if (inCenterY && nx in CENTER_REGION_MIN_X..CENTER_REGION_MAX_X) {
                centerSum += lum
                centerCount++
            }
        }
    }

    val fallback = if (allCount > 0) (allSum / allCount).toFloat() else 0.5f
    return WallpaperLuminanceProfile(
        top = if (topCount > 0) (topSum / topCount).toFloat() else fallback,
        leftColumn = if (leftCount > 0) (leftSum / leftCount).toFloat() else fallback,
        center = if (centerCount > 0) (centerSum / centerCount).toFloat() else fallback,
    )
}

private const val SAMPLE_COLS = 24
private const val SAMPLE_ROWS = 40
private const val TOP_REGION_MAX_Y = 0.24f
private const val LEFT_REGION_MAX_X = 0.16f
private const val LEFT_REGION_MIN_Y = 0.18f
private const val LEFT_REGION_MAX_Y = 0.95f
private const val CENTER_REGION_MIN_X = 0.15f
private const val CENTER_REGION_MAX_X = 0.85f
private const val CENTER_REGION_MIN_Y = 0.35f
private const val CENTER_REGION_MAX_Y = 0.65f
