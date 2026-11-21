package com.star.schedule.feature.emoji.domain

import kotlin.math.min
import kotlin.math.roundToInt

enum class EmojiPattern { MOSAIC, LOTUS, STACK, SCATTER, PRISM }

@kotlinx.serialization.Serializable
data class EmojiWallpaperSpec(
    val emojis: List<String>,
    val pattern: EmojiPattern,
    val scale: Float,
    val colors: EmojiWallpaperColors,
    val seed: Long,
)

data class EmojiPlacement(
    val emojiIndex: Int,
    val centerX: Float,
    val centerY: Float,
    val size: Float,
    val rotationDegrees: Float = 0f,
    val mirrored: Boolean = false,
)

data class EmojiTransform(
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val tx: Float,
    val ty: Float,
)

data class EmojiTiling(
    val triangleSide: Float,
    val transforms: List<EmojiTransform>,
    val triangleHeight: Float = triangleSide * 0.8660254f,
)

data class EmojiWallpaperLayout(
    val placements: List<EmojiPlacement>,
    val tiling: EmojiTiling? = null,
)

const val EMOJI_SCALE_INTERVALS = 24
const val EMOJI_LAYOUT_SEED = 10L

fun emojiScaleStep(scale: Float): Int =
    (scale.takeIf(Float::isFinite)?.coerceIn(0f, 1f)?.times(EMOJI_SCALE_INTERVALS))
        ?.roundToInt() ?: 0

fun quantizedEmojiScale(scale: Float): Float = emojiScaleStep(scale).toFloat() / EMOJI_SCALE_INTERVALS

// 官方滑杆共有 25 个停靠点；相同的增量在后几档会产生更大的尺寸变化。
internal val EMOJI_SIZE_CURVE = floatArrayOf(
    0f, .011f, .022f, .033f, .045f, .056f, .069f, .082f, .096f,
    .111f, .128f, .146f, .166f, .188f, .212f, .24f, .272f,
    .307f, .348f, .397f, .454f, .525f, .617f, .742f, .995f,
)

/** 在 1080px 参考画布计算，保留视差两侧内容，再取中间一屏供预览和导出共用。 */
fun emojiWallpaperLayout(
    pattern: EmojiPattern,
    emojiCount: Int,
    scale: Float,
    seed: Long,
    width: Float,
    height: Float,
): EmojiWallpaperLayout {
    if (emojiCount < 1 || !width.isFinite() || !height.isFinite() || width <= 0 || height <= 0) {
        return EmojiWallpaperLayout(emptyList())
    }
    val factor = min(width, height) / 1080f
    val w = (width / factor).roundToInt()
    val h = (height / factor).roundToInt()
    val t = EMOJI_SIZE_CURVE[emojiScaleStep(scale)]
    if (pattern == EmojiPattern.PRISM) return prismEmojiLayout(emojiCount, t, seed, width, height)
    val pan = if (pattern == EmojiPattern.STACK && stackColumns(w, h, t) <= 2) 0 else (w * .8f).toInt()
    val canvasWidth = w + pan * 2
    val points = when (pattern) {
        EmojiPattern.STACK -> stackEmojiLayout(canvasWidth, h, emojiCount, t)
        EmojiPattern.LOTUS -> lotusEmojiLayout(canvasWidth, h, emojiCount, t)
        EmojiPattern.MOSAIC -> mosaicEmojiLayout(canvasWidth, h, emojiCount, t, seed)
        EmojiPattern.SCATTER -> scatterEmojiLayout(canvasWidth, h, emojiCount, t, seed)
        EmojiPattern.PRISM -> error("Handled above")
    }
    return EmojiWallpaperLayout(points.map {
        it.copy(centerX = (it.centerX - pan) * factor, centerY = it.centerY * factor, size = it.size * factor)
    }.filter {
        it.centerX + it.size >= 0 && it.centerX - it.size <= width &&
            it.centerY + it.size >= 0 && it.centerY - it.size <= height
    })
}
