package com.star.schedule.feature.wallpaper.domain

import kotlin.math.min

/**
 * 给定源尺寸与目标尺寸求 `BitmapFactory.Options.inSampleSize`：
 * 取 2 的幂里不让解码结果小于目标尺寸的最大值。
 *
 * 语义与 WallpaperCrop 里可见区域的采样一致——只按尺寸算，不做裁切。
 * 目标比源大时返回 1（不放大采样，交给后续缩放）。
 */
internal fun wallpaperSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
): Int {
    if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
        return 1
    }
    val ratio = min(
        sourceWidth.toFloat() / targetWidth,
        sourceHeight.toFloat() / targetHeight,
    )
    var sampleSize = 1
    while (sampleSize * 2 <= ratio) {
        sampleSize *= 2
    }
    return sampleSize
}
