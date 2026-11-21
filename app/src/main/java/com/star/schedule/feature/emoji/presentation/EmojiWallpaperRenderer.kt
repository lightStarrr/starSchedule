package com.star.schedule.feature.emoji.presentation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache
import androidx.core.graphics.withMatrix
import androidx.core.graphics.withSave
import com.star.schedule.feature.emoji.data.EmojiTypefaceLoader
import com.star.schedule.feature.emoji.domain.EmojiPlacement
import com.star.schedule.feature.emoji.domain.EmojiWallpaperLayout
import com.star.schedule.feature.emoji.domain.EmojiWallpaperSpec

private class RendererScratch {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        // 与官方表情轮廓使用相同的粗体字重。
        fontVariationSettings = EmojiTypefaceLoader.ARG_WEIGHT
    }
    val clipPath = Path()
    val matrix = Matrix()
    val matrixValues = FloatArray(9).apply { this[8] = 1f }
    val bounds = Rect()
    val destRect = RectF()
}

private val rendererScratch = ThreadLocal.withInitial { RendererScratch() }

/**
 * 把表情壁纸画到 [Canvas]：先铺背景色，再按布局逐点画表情。
 * 预览（Compose Canvas）与导出（Bitmap Canvas）共用同一函数。
 */
fun drawEmojiWallpaper(
    canvas: Canvas,
    width: Int,
    height: Int,
    spec: EmojiWallpaperSpec,
    layout: EmojiWallpaperLayout,
    typeface: Typeface?,
) {
    canvas.drawColor(spec.colors.backgroundArgb)
    if (spec.emojis.isEmpty()) return
    val scratch = rendererScratch.get()!!
    val paint = scratch.paint
    paint.color = spec.colors.emojiArgb
    paint.typeface = typeface

    val tiling = layout.tiling
    val atlas = wallpaperGlyphs(
        spec = spec,
        paint = paint,
        bounds = scratch.bounds,
        size = layout.placements.maxOfOrNull { it.size } ?: 1f,
    )
    if (tiling != null) {
        // 万花筒：每个变换把基本三角形 F 映射到画布，
        // 沿三角形裁剪后画同一份内容；镜像块由矩阵自然翻转。
        val side = tiling.triangleSide
        val unit = side / 100f
        val clip = scratch.clipPath
        clip.rewind()
        clip.moveTo(-.5f * unit, -.25f * unit)
        clip.lineTo(side + .5f * unit, -.25f * unit)
        clip.lineTo(side / 2f, tiling.triangleHeight + .75f * unit)
        clip.close()

        val matrix = scratch.matrix
        val values = scratch.matrixValues
        val bounds = scratch.bounds
        val destRect = scratch.destRect
        tiling.transforms.forEach { t ->
            values[0] = t.a
            values[1] = t.c
            values[2] = t.tx
            values[3] = t.b
            values[4] = t.d
            values[5] = t.ty
            values[6] = 0f
            values[7] = 0f
            values[8] = 1f
            matrix.setValues(values)
            canvas.withMatrix(matrix) {
                clipPath(clip)
                layout.placements.forEach { placement ->
                    drawEmojiSprite(
                        canvas = this,
                        paint = paint,
                        spec = spec,
                        placement = placement,
                        atlas = atlas,
                        bounds = bounds,
                        destRect = destRect,
                        unit = unit,
                        offset = 1,
                    )
                }
            }
        }
    } else {
        val bounds = scratch.bounds
        val destRect = scratch.destRect
        layout.placements.forEach {
            drawEmojiSprite(canvas, paint, spec, it, atlas, bounds, destRect)
        }
    }
}

private data class GlyphKey(val typeface: Typeface?, val size: Float, val emoji: String, val color: Int)

// 共用字形纹理可稳定各档位的轮廓；棱镜还需在基础域整数网格中定位后放大。
private val glyphCache = object : LruCache<GlyphKey, Bitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: GlyphKey, value: Bitmap): Int = value.allocationByteCount
}

private fun wallpaperGlyphs(
    spec: EmojiWallpaperSpec,
    paint: Paint,
    bounds: Rect,
    size: Float,
): Map<String, Bitmap> {
    return spec.emojis.distinct().associateWith { emoji ->
        val key = GlyphKey(paint.typeface, size, emoji, spec.colors.emojiArgb)
        glyphCache.get(key)?.let { return@associateWith it }
        paint.textSize = size
        paint.getTextBounds(emoji, 0, emoji.length, bounds)
        val source = Bitmap.createBitmap(
            (bounds.width() + 1).coerceAtLeast(1),
            (bounds.height() + 1).coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        Canvas(source).drawText(emoji, source.width / 2f, source.height / 2f - bounds.centerY(), paint)
        val largest = maxOf(bounds.width(), bounds.height(), 1)
        val textureSize = Integer.highestOneBit(largest - 1).coerceAtLeast(1).times(2).coerceAtMost(1024)
        Bitmap.createScaledBitmap(source, textureSize, textureSize, true).also {
            if (it !== source) source.recycle()
            glyphCache.put(key, it)
        }
    }
}

private fun drawEmojiSprite(
    canvas: Canvas,
    paint: Paint,
    spec: EmojiWallpaperSpec,
    placement: EmojiPlacement,
    atlas: Map<String, Bitmap>,
    bounds: Rect,
    destRect: RectF,
    unit: Float = 1f,
    offset: Int = 0,
) {
    val emoji = spec.emojis[placement.emojiIndex % spec.emojis.size]
    paint.textSize = placement.size / unit
    paint.getTextBounds(emoji, 0, emoji.length, bounds)
    canvas.withSave {
        translate(placement.centerX, placement.centerY)
        rotate(placement.rotationDegrees)
        if (placement.mirrored) scale(-1f, 1f)
        val left = -(((bounds.width() + 1) / 2) + offset) * unit
        val top = -(((bounds.height() + 1) / 2) + offset) * unit
        destRect.set(left, top, left + bounds.width() * unit, top + bounds.height() * unit)
        drawBitmap(atlas.getValue(emoji), null, destRect, paint)
    }
}
