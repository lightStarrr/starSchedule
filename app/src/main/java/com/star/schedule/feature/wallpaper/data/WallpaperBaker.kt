package com.star.schedule.feature.wallpaper.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.star.schedule.feature.wallpaper.domain.WallpaperBlurSpec
import com.star.schedule.feature.wallpaper.domain.gaussianBlurArgb
import com.star.schedule.feature.wallpaper.domain.wallpaperBakeSize
import com.star.schedule.feature.wallpaper.domain.wallpaperSampleSize
import java.io.File

/**
 * 把模糊烘焙进壁纸文件：整张原图放大到屏幕像素 1:1、按像素半径模糊、编码 JPEG。
 *
 * 与预览页 `Modifier.blur` 的对应关系：显示时壁纸按 Cover 铺满窗口（缩放
 * s = max(winW/srcW, winH/srcH)），烘焙把整图放大同样的倍数再模糊，
 * 烘出来的文件按同一 Cover 显示时就是预览看到的效果——区别只在烘的是整图，
 * 屏幕外的那部分也带模糊，窗口尺寸变化时只是重新裁切，不会失真。
 */
internal object WallpaperBaker {

    /**
     * 把 [source] 按 [spec] 烘焙写入 [target]（JPEG，质量 95）。
     * radiusPx <= 0 时只做缩放重编码，不做模糊。
     */
    fun bake(source: File, spec: WallpaperBlurSpec, target: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        val (outWidth, outHeight) = wallpaperBakeSize(
            sourceWidth = bounds.outWidth,
            sourceHeight = bounds.outHeight,
            windowWidth = spec.windowWidthPx,
            windowHeight = spec.windowHeightPx,
        )

        // 原图可能是几千万像素：先按 inSampleSize 粗采到不小于目标尺寸，
        // 再精确缩放，避免把全分辨率位图读进内存。
        val decoded = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = wallpaperSampleSize(
                    bounds.outWidth,
                    bounds.outHeight,
                    outWidth,
                    outHeight,
                )
            },
        ) ?: error("Wallpaper is not a supported image")
        val scaled = Bitmap.createScaledBitmap(decoded, outWidth, outHeight, true)
        if (scaled !== decoded) decoded.recycle()

        val output = if (spec.radiusPx > 0f) {
            val pixels = IntArray(outWidth * outHeight)
            scaled.getPixels(pixels, 0, outWidth, 0, 0, outWidth, outHeight)
            val blurred = gaussianBlurArgb(pixels, outWidth, outHeight, spec.radiusPx)
            scaled.setPixels(blurred, 0, outWidth, 0, 0, outWidth, outHeight)
            scaled
        } else {
            scaled
        }

        target.outputStream().use { stream ->
            check(output.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                "Unable to encode baked wallpaper"
            }
        }
        if (output !== scaled) output.recycle()
        scaled.recycle()
    }
}
