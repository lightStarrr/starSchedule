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
import com.star.schedule.feature.emoji.domain.emojiWallpaperLayout
import kotlin.math.roundToInt

private class RendererScratch {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
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

internal data class EmojiWallpaperBitmapKey(
    val spec: EmojiWallpaperSpec,
    val width: Int,
    val height: Int,
    val typeface: Typeface,
)

// 进程级离屏位图缓存：编辑器、预览页、主页、行高页与壁纸条缩略图共享，
// 避免在页面切换、宽度动画或实时模糊拖动时于主线程重复绘制数百个表情图元。
// 位图交由 Compose Image 后由 RenderThread 异步读取，不主动 recycle，交由 LRU 与 GC 回收。
private val emojiWallpaperCache =
    object : LruCache<EmojiWallpaperBitmapKey, Bitmap>(36 * 1024 * 1024) {
        override fun sizeOf(key: EmojiWallpaperBitmapKey, value: Bitmap): Int =
            value.allocationByteCount
    }

internal fun peekCachedEmojiWallpaper(
    spec: EmojiWallpaperSpec,
    width: Int,
    height: Int,
    typeface: Typeface,
): Bitmap? {
    if (width <= 0 || height <= 0) return null
    return emojiWallpaperCache.get(EmojiWallpaperBitmapKey(spec, width, height, typeface))
}

internal fun renderOrGetEmojiWallpaper(
    spec: EmojiWallpaperSpec,
    width: Int,
    height: Int,
    typeface: Typeface,
): Bitmap? {
    if (width <= 0 || height <= 0) return null
    val key = EmojiWallpaperBitmapKey(spec, width, height, typeface)
    emojiWallpaperCache.get(key)?.let { return it }
    val layout = emojiWallpaperLayout(
        pattern = spec.pattern,
        emojiCount = spec.emojis.size,
        scale = spec.scale,
        seed = spec.seed,
        width = width.toFloat(),
        height = height.toFloat(),
    )
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    drawEmojiWallpaper(
        canvas = Canvas(bitmap),
        width = width,
        height = height,
        spec = spec,
        layout = layout,
        typeface = typeface,
    )
    emojiWallpaperCache.put(key, bitmap)
    return bitmap
}

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
    val glyphs = wallpaperGlyphs(
        spec = spec,
        paint = paint,
        bounds = scratch.bounds,
        maxSize = layout.placements.maxOfOrNull { it.size } ?: 1f,
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
                        placement = placement,
                        glyphs = glyphs,
                        destRect = destRect,
                        unit = unit,
                        offset = 1,
                    )
                }
            }
        }
    } else {
        val destRect = scratch.destRect
        layout.placements.forEach {
            drawEmojiSprite(canvas, paint, it, glyphs, destRect)
        }
    }
}

private data class GlyphKey(
    val typeface: Typeface?,
    val textureSize: Int,
    val emoji: String,
    val color: Int,
)

private class GlyphEntry(
    val bitmap: Bitmap,
    val widthRatio: Float,
    val heightRatio: Float,
)

// 字形纹理按 2 的幂分桶（64..512）缓存，同时缓存字形宽高比，
// 避免缩放档位变化时频繁重建大图，并彻底消除每个图元的 getTextBounds JNI 开销。
private val glyphCache = object : LruCache<GlyphKey, GlyphEntry>(16 * 1024 * 1024) {
    override fun sizeOf(key: GlyphKey, value: GlyphEntry): Int = value.bitmap.allocationByteCount
}

private fun textureSizeFor(size: Float): Int {
    val raw = size.roundToInt().coerceAtLeast(1)
    val powerOfTwo = Integer.highestOneBit(raw - 1).coerceAtLeast(1).times(2)
    return powerOfTwo.coerceIn(64, 512)
}

private fun wallpaperGlyphs(
    spec: EmojiWallpaperSpec,
    paint: Paint,
    bounds: Rect,
    maxSize: Float,
): Array<GlyphEntry> {
    val textureSize = textureSizeFor(maxSize)
    val color = spec.colors.emojiArgb
    val typeface = paint.typeface
    return Array(spec.emojis.size) { index ->
        val emoji = spec.emojis[index]
        val key = GlyphKey(typeface, textureSize, emoji, color)
        glyphCache.get(key) ?: run {
            paint.textSize = textureSize.toFloat()
            paint.getTextBounds(emoji, 0, emoji.length, bounds)
            val source = Bitmap.createBitmap(
                (bounds.width() + 1).coerceAtLeast(1),
                (bounds.height() + 1).coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            Canvas(source).drawText(
                emoji,
                source.width / 2f,
                source.height / 2f - bounds.centerY(),
                paint,
            )
            val bitmap = if (source.width == textureSize && source.height == textureSize) {
                source
            } else {
                Bitmap.createScaledBitmap(source, textureSize, textureSize, true).also {
                    if (it !== source) source.recycle()
                }
            }
            GlyphEntry(
                bitmap = bitmap,
                widthRatio = bounds.width().toFloat() / textureSize.toFloat(),
                heightRatio = bounds.height().toFloat() / textureSize.toFloat(),
            ).also { glyphCache.put(key, it) }
        }
    }
}

private fun drawEmojiSprite(
    canvas: Canvas,
    paint: Paint,
    placement: EmojiPlacement,
    glyphs: Array<GlyphEntry>,
    destRect: RectF,
    unit: Float = 1f,
    offset: Int = 0,
) {
    val glyph = glyphs[placement.emojiIndex % glyphs.size]
    val textSize = placement.size / unit
    val bw = (glyph.widthRatio * textSize).roundToInt()
    val bh = (glyph.heightRatio * textSize).roundToInt()
    canvas.withSave {
        translate(placement.centerX, placement.centerY)
        rotate(placement.rotationDegrees)
        if (placement.mirrored) scale(-1f, 1f)
        val left = -(((bw + 1) / 2) + offset) * unit
        val top = -(((bh + 1) / 2) + offset) * unit
        destRect.set(left, top, left + bw * unit, top + bh * unit)
        drawBitmap(glyph.bitmap, null, destRect, paint)
    }
}
