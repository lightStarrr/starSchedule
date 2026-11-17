@file:SuppressLint("RestrictedApi")

package com.star.schedule.feature.wallpaper.data

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.Score
import com.star.schedule.feature.wallpaper.domain.wallpaperSampleSize

/**
 * 壁纸取色：与系统壁纸取色同一套官方流程——量化后按 MCU Score 打分选种子色。
 *
 * color.utilities 包内类标了 @RestrictTo(LIBRARY_GROUP)：官方 MCU 的 Java 实现
 * 只随 MDC 发行，调用集中在取色与 [com.star.schedule.core.designsystem.theme.seedColorScheme]
 * 两处；升级 com.google.android.material:material 时检查这两处即可。
 */
internal object WallpaperSeedColorExtractor {
    const val VERSION = 2

    fun extract(file: java.io.File): Int? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // 与 WallpaperColors 取样规模一致：约 112×112 像素面积，保持宽高比。
        val area = 112 * 112
        val scale = Math.sqrt(area.toDouble() / (bounds.outWidth.toDouble() * bounds.outHeight))
        val sampleWidth = (bounds.outWidth * scale).toInt().coerceAtLeast(1)
        val sampleHeight = (bounds.outHeight * scale).toInt().coerceAtLeast(1)

        // 先按 inSampleSize 粗采到不小于缩略图，再精确缩放：
        // 原图可能是几千万像素，直接全分辨率解码会撑爆内存。
        val source = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = wallpaperSampleSize(
                    bounds.outWidth,
                    bounds.outHeight,
                    sampleWidth,
                    sampleHeight,
                )
            },
        ) ?: return null
        val sample = Bitmap.createScaledBitmap(source, sampleWidth, sampleHeight, true)
        if (sample !== source) source.recycle()

        val pixels = IntArray(sample.width * sample.height)
        sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
        sample.recycle()

        val quantized = QuantizerCelebi.quantize(pixels, 128)
        // Score 没有合适的候选色时会返回官方回退色，直接沿用。
        return Score.score(quantized).firstOrNull()
    }
}
