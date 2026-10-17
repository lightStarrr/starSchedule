package com.star.schedule.feature.wallpaper.presentation

import kotlin.math.roundToInt

/**
 * 壁纸的解码区域与采样率。
 *
 * 区域是原图坐标，[sampleSize] 是 [android.graphics.BitmapFactory.Options.inSampleSize]。
 */
internal data class WallpaperCropSpec(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val sampleSize: Int,
)

/**
 * 按屏幕比例计算壁纸需要解码的区域与采样率，使解码结果与屏幕接近 1:1。
 *
 * 壁纸以 Cover 方式铺满，可见部分只是原图的一个居中裁切窗口。这里直接在原图坐标系
 * 里算出这个窗口，让解码器只读它，而不是先整图解码再缩放到屏幕上：
 * 前者内存只跟屏幕大小有关，后者会让超大图片把整幅像素读进内存。
 *
 * [sampleSize] 取 2 的幂且保证解码后的尺寸不小于屏幕，避免放大带来的模糊；
 * 原图本身比屏幕小时不裁切，也不放大采样，直接使用原尺寸。
 */
internal fun calculateWallpaperCrop(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int,
): WallpaperCropSpec {
    val fullImage = WallpaperCropSpec(
        left = 0,
        top = 0,
        width = sourceWidth.coerceAtLeast(0),
        height = sourceHeight.coerceAtLeast(0),
        sampleSize = 1,
    )
    if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
        return fullImage
    }

    val coverScale = maxOf(
        targetWidth.toFloat() / sourceWidth,
        targetHeight.toFloat() / sourceHeight,
    )
    // 原图不足以铺满屏幕，裁切只会让可见区域更小，直接用原尺寸交给 Image 放大。
    if (coverScale >= 1f) return fullImage

    val visibleWidth = (targetWidth / coverScale).roundToInt().coerceIn(1, sourceWidth)
    val visibleHeight = (targetHeight / coverScale).roundToInt().coerceIn(1, sourceHeight)

    var sampleSize = 1
    val downsampleRatio = visibleWidth.toFloat() / targetWidth
    while (sampleSize * 2 <= downsampleRatio) {
        sampleSize *= 2
    }

    return WallpaperCropSpec(
        left = (sourceWidth - visibleWidth) / 2,
        top = (sourceHeight - visibleHeight) / 2,
        width = visibleWidth,
        height = visibleHeight,
        sampleSize = sampleSize,
    )
}
