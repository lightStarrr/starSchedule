package com.star.schedule.feature.emoji.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private fun stackSize(t: Float): Int {
    val size = 35 + (73 * t).toInt()
    val mapped = 1.0 + (size - 8) / 52.0 * 13.0
    return (mapped * mapped).toInt()
}

internal fun stackColumns(width: Int, height: Int, t: Float): Int {
    val size = stackSize(t)
    val spacing = (size * 3 / 2) / 2f + size
    val extent = max(width, height).toFloat()
    val coarse = (extent / 2.5f / spacing).roundToInt().coerceAtLeast(1)
    return if (coarse > 2) (extent / spacing).roundToInt().coerceAtLeast(1) else coarse
}

internal fun stackEmojiLayout(width: Int, height: Int, count: Int, t: Float): List<EmojiPlacement> {
    val size = stackSize(t)
    val extent = max(width, height)
    val columns = stackColumns(width, height, t)
    val rowSize = size * if (columns >= 3) 1f else if (height > width) 1.1f else 1.145f
    val rows = (extent / (rowSize.toInt() + (size * 3 / 2 / 3) / 2f)).roundToInt().coerceAtLeast(3)
    val gapX: Int
    val gapY: Int
    if (columns < 3) {
        gapX = ((extent / 3.5 - (columns - 1) * size) / 2).toInt()
        gapY = 16
    } else {
        val available = extent - size / 2
        gapX = available / ((columns - 1) * size) * size
        gapY = available / ((rows - 1) * size) * size
    }
    val originX = (extent - (columns * size + (columns - 1) * gapX)) / 2 + size / 2
    val originY = ((extent - (rows * rowSize + (rows - 1) * gapY).toInt()) / 2f + rowSize / 2).toInt()
    return buildList {
        repeat(rows) { row ->
            val stagger = row % 2 == 1 && columns > 2
            repeat(columns - if (stagger) 1 else 0) { col ->
                val x = originX + (gapX + size) * (col + if (stagger) .5f else 0f) - abs(height - width) / 2f
                val y = originY + (gapY + rowSize) * row
                add(EmojiPlacement(
                    emojiIndex = (row + col) % count,
                    centerX = if (height > width) x else y,
                    centerY = if (height > width) y else height - x,
                    size = size.toFloat(),
                ))
            }
        }
    }
}

/** 黄金角向外展开，保留莲花的旋臂与连续档位变化。 */
internal fun lotusEmojiLayout(width: Int, height: Int, count: Int, t: Float): List<EmojiPlacement> {
    val size = 45 + (240 * t).toInt()
    val goldenAngle = (3 - sqrt(5.0)) * PI
    val limit = hypot(width * 2.0, height * 2.0)
    return buildList {
        for (index in 0 until 10_000) {
            val radius = 1.1 * size * sqrt(index.toDouble())
            if (radius > limit) break
            val angle = index * goldenAngle - .000001
            val x = (width / 2.0 + cos(angle) * radius + if (index == 0) size / 2.0 else 0.0).toFloat()
            val y = (height / 2.0 + sin(angle) * radius).toFloat()
            if (x >= -size && x <= width * 2.5 + size && y >= -size && y <= height * 1.5 + size) {
                add(EmojiPlacement(index % count, x, y, size.toFloat()))
            }
        }
    }
}
