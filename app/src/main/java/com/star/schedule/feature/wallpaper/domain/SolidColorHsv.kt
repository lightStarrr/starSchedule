package com.star.schedule.feature.wallpaper.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 纯色壁纸调色盘的 HSV 色彩模型。
 *
 * 独立于 Android 平台类型，方便在 JVM 单元测试直接校验色相保留与边界收敛行为。
 */
data class SolidColorHsv(
    val hue: Float,
    val saturation: Float,
    val value: Float,
) {
    fun normalized(): SolidColorHsv = SolidColorHsv(
        hue = ((hue % 360f) + 360f) % 360f,
        saturation = saturation.coerceIn(0f, 1f),
        value = value.coerceIn(0f, 1f),
    )

    /** 转为不透明 ARGB（alpha 固定为 0xFF）。 */
    fun toOpaqueArgb(): Int {
        val norm = normalized()
        val c = norm.value * norm.saturation
        val x = c * (1f - abs((norm.hue / 60f) % 2f - 1f))
        val m = norm.value - c
        val (r1, g1, b1) = when {
            norm.hue < 60f -> Triple(c, x, 0f)
            norm.hue < 120f -> Triple(x, c, 0f)
            norm.hue < 180f -> Triple(0f, c, x)
            norm.hue < 240f -> Triple(0f, x, c)
            norm.hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    companion object {
        /**
         * 从 ARGB 还原 HSV；遇到灰/白/黑（delta == 0）时保留 [fallbackHue]，
         * 避免用户在画板上滑到纯白或纯黑时色相条跳回 0° 红色。
         */
        fun fromArgb(argb: Int, fallbackHue: Float = 0f): SolidColorHsv {
            val r = ((argb ushr 16) and 0xFF) / 255f
            val g = ((argb ushr 8) and 0xFF) / 255f
            val b = (argb and 0xFF) / 255f
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val delta = max - min

            val hue = if (delta == 0f) {
                ((fallbackHue % 360f) + 360f) % 360f
            } else {
                val raw = when (max) {
                    r -> 60f * (((g - b) / delta) % 6f)
                    g -> 60f * (((b - r) / delta) + 2f)
                    else -> 60f * (((r - g) / delta) + 4f)
                }
                ((raw % 360f) + 360f) % 360f
            }
            val saturation = if (max == 0f) 0f else delta / max
            return SolidColorHsv(hue = hue, saturation = saturation, value = max)
        }
    }
}

/** 格式化为 6 位大写十六进制色值（如 `#D3E3FD`）。 */
fun formatSolidColorHex(argb: Int): String {
    val rgb = argb and 0x00FFFFFF
    return "#%06X".format(rgb)
}
