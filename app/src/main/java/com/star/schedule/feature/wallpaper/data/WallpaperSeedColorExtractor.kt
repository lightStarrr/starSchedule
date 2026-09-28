package com.star.schedule.feature.wallpaper.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory

internal object WallpaperSeedColorExtractor {
    const val VERSION = 1

    fun extract(file: java.io.File): Int? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val source = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = maxOf(bounds.outWidth, bounds.outHeight)
                    .coerceAtLeast(32) / 32
            },
        ) ?: return null
        val sample = Bitmap.createScaledBitmap(source, 32, 32, true)
        if (sample !== source) source.recycle()

        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        for (y in 0 until sample.height) {
            for (x in 0 until sample.width) {
                val pixel = sample.getPixel(x, y)
                val alpha = pixel ushr 24 and 0xff
                if (alpha < 180) continue
                red += pixel ushr 16 and 0xff
                green += pixel ushr 8 and 0xff
                blue += pixel and 0xff
                count++
            }
        }
        sample.recycle()
        if (count == 0L) return null

        return android.graphics.Color.rgb(
            (red / count).toInt(),
            (green / count).toInt(),
            (blue / count).toInt(),
        )
    }
}
