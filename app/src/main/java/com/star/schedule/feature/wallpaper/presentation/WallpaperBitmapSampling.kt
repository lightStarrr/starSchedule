package com.star.schedule.feature.wallpaper.presentation

/**
 * 计算把图片解码到目标长边所需的 [android.graphics.BitmapFactory.Options.inSampleSize]。
 *
 * 解码器按 2 的幂处理采样率，因此这里只返回 2 的幂。返回值保证解码后的长边不超过
 * [targetLongEdge]，内存占用因此随屏幕尺寸收敛，不会因为原图分辨率而失控；
 * 图片本身不超过目标尺寸时返回 1，避免无意义的采样。
 */
internal fun calculateInSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    targetLongEdge: Int,
): Int {
    if (sourceWidth <= 0 || sourceHeight <= 0 || targetLongEdge <= 0) return 1

    val longEdge = maxOf(sourceWidth, sourceHeight)
    var sampleSize = 1
    while (longEdge / sampleSize > targetLongEdge) {
        sampleSize *= 2
    }
    return sampleSize
}
