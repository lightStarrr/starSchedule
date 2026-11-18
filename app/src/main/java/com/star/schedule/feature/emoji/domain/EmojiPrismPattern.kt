package com.star.schedule.feature.emoji.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private data class Affine(
    val a: Float = 1f, val b: Float = 0f, val c: Float = 0f,
    val d: Float = 1f, val x: Float = 0f, val y: Float = 0f,
) {
    operator fun times(other: Affine) = Affine(
        a * other.a + c * other.b, b * other.a + d * other.b,
        a * other.c + c * other.d, b * other.c + d * other.d,
        a * other.x + c * other.y + x, b * other.x + d * other.y + y,
    )
}

private fun translate(x: Float, y: Float) = Affine(x = x, y = y)

private fun rotate(degrees: Float, x: Float = 0f, y: Float = 0f): Affine {
    val angle = degrees * PI.toFloat() / 180f
    return translate(x, y) * Affine(cos(angle), sin(angle), -sin(angle), cos(angle)) * translate(-x, -y)
}

private fun mirror(sx: Float, sy: Float, x: Float, y: Float) =
    translate(x, y) * Affine(a = sx, d = sy) * translate(-x, -y)

/** 以屏幕中心为原点的三角镜面；格子尺寸固定，档位改变其中的表情内容。 */
internal fun prismEmojiLayout(count: Int, t: Float, seed: Long, width: Float, height: Float): EmojiWallpaperLayout {
    val unit = max(width, height) / 400f
    val triangleHeight = 86.60254f
    val contents = prismContents(count, t, seed).map {
        it.copy(centerX = (it.centerX - 30) * unit, centerY = it.centerY * unit, size = it.size * unit)
    }
    val transforms = buildList {
        repeat(6) { row ->
            repeat(3) { col ->
                val sign = if ((row + col) % 2 == 0) 1f else -1f
                val base = translate(width / 2, height / 2) * Affine(a = unit, d = unit) *
                    translate(col * 150f - 150, (row - 3) * triangleHeight)
                val tiles = listOf(
                    mirror(1f, sign, 0f, triangleHeight / 2) * rotate(120f),
                    mirror(1f, sign, 50f, triangleHeight / 2),
                    mirror(1f, sign, 0f, triangleHeight / 2) * rotate(-60f, 50f, triangleHeight) * mirror(-1f, 1f, 50f, 0f),
                )
                tiles.forEach { tile ->
                    val m = base * tile * Affine(a = 1 / unit, d = 1 / unit)
                    add(EmojiTransform(m.a, m.b, m.c, m.d, m.x, m.y))
                }
            }
        }
    }
    return EmojiWallpaperLayout(contents, EmojiTiling(100 * unit, transforms, triangleHeight * unit))
}
