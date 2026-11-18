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
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = spec.colors.emojiArgb
        this.typeface = typeface
        textAlign = Paint.Align.CENTER
        // 与官方表情轮廓使用相同的粗体字重。
        fontVariationSettings = EmojiTypefaceLoader.ARG_WEIGHT
    }
    val tiling = layout.tiling
    val atlas = wallpaperGlyphs(spec, paint, layout.placements.maxOfOrNull { it.size } ?: 1f)
    if (tiling != null) {
        // 万花筒：每个变换把基本三角形 F 映射到画布，
        // 沿三角形裁剪后画同一份内容；镜像块由矩阵自然翻转。
        val side = tiling.triangleSide
        val unit = side / 100f
        val clip = Path().apply {
            moveTo(-.5f * unit, -.25f * unit)
            lineTo(side + .5f * unit, -.25f * unit)
            lineTo(side / 2f, tiling.triangleHeight + .75f * unit)
            close()
        }
        val matrix = Matrix()
        tiling.transforms.forEach { t ->
            matrix.setValues(
                floatArrayOf(
                    t.a, t.c, t.tx,
                    t.b, t.d, t.ty,
                    0f, 0f, 1f,
                ),
            )
            canvas.withMatrix(matrix) {
                clipPath(clip)
                layout.placements.forEach { placement ->
                    drawEmojiSprite(this, paint, spec, placement, atlas, unit, 1)
                }
            }
        }
    } else {
        layout.placements.forEach { drawEmojiSprite(canvas, paint, spec, it, atlas) }
    }
}

private data class GlyphKey(val typeface: Typeface?, val size: Float, val emoji: String, val color: Int)

// 共用字形纹理可稳定各档位的轮廓；棱镜还需在基础域整数网格中定位后放大。
private val glyphCache = object : LruCache<GlyphKey, Bitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: GlyphKey, value: Bitmap): Int = value.allocationByteCount
}

private fun wallpaperGlyphs(spec: EmojiWallpaperSpec, paint: Paint, size: Float): Map<String, Bitmap> {
    return spec.emojis.distinct().associateWith { emoji ->
        val key = GlyphKey(paint.typeface, size, emoji, spec.colors.emojiArgb)
        glyphCache.get(key)?.let { return@associateWith it }
        paint.textSize = size
        val bounds = Rect()
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
    unit: Float = 1f,
    offset: Int = 0,
) {
    val emoji = spec.emojis[placement.emojiIndex % spec.emojis.size]
    paint.textSize = placement.size / unit
    val bounds = Rect()
    paint.getTextBounds(emoji, 0, emoji.length, bounds)
    canvas.withSave {
        translate(placement.centerX, placement.centerY)
        rotate(placement.rotationDegrees)
        if (placement.mirrored) scale(-1f, 1f)
        val left = -(((bounds.width() + 1) / 2) + offset) * unit
        val top = -(((bounds.height() + 1) / 2) + offset) * unit
        drawBitmap(
            atlas.getValue(emoji), null,
            RectF(left, top, left + bounds.width() * unit, top + bounds.height() * unit), paint,
        )
    }
}
