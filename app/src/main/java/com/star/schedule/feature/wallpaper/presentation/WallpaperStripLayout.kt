package com.star.schedule.feature.wallpaper.presentation

import kotlin.math.floor
import kotlin.math.min

/**
 * 壁纸卡片上半排的布局结果：左侧当前壁纸是方形，右侧最近壁纸按统一宽度平分剩余空间。
 * 尺寸单位随调用方约定（界面里传 dp），本文件只做纯计算。
 */
internal data class WallpaperStripLayout(
    val squareSize: Float,
    val recentCount: Int,
    val recentWidth: Float,
)

/**
 * 排布规则（参考系统「壁纸与风格」的一排缩略图）：
 * 当前壁纸方块占 [squareFraction]、最大 [maxSquareSize]；
 * 剩余宽度按「最小宽度 + 间距」先塞尽可能多的最近壁纸槽位，再把余量平均分给它们。
 * 槽位只是占位：有没有壁纸显示都由调用方决定，空槽位保持透明。
 */
internal fun wallpaperStripLayout(
    availableWidth: Float,
    squareFraction: Float = 0.38f,
    maxSquareSize: Float = 200f,
    minRecentWidth: Float = 52f,
    gap: Float = 8f,
): WallpaperStripLayout {
    if (availableWidth <= 0f) return WallpaperStripLayout(0f, 0, 0f)
    val square = min(availableWidth * squareFraction, maxSquareSize)
    val remaining = availableWidth - square
    val count = floor(remaining / (minRecentWidth + gap)).toInt().coerceAtLeast(0)
    val recentWidth = if (count == 0) 0f else (remaining - count * gap) / count
    return WallpaperStripLayout(square, count, recentWidth)
}

/**
 * 缩略图的同心圆角：内圆角 = 外圆角 − 缩略图离卡片边缘的距离，下限 [minRadius]。
 * [inset] 取水平、垂直内边距的较小值（调用方负责）——统一半径下较小边距更贴近同心观感。
 */
internal fun wallpaperThumbnailCornerRadius(
    outerRadius: Float,
    inset: Float,
    minRadius: Float = 4f,
): Float = (outerRadius - inset).coerceAtLeast(minRadius)
