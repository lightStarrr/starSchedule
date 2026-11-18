package com.star.schedule.feature.emoji.domain

import java.util.Random
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min

private fun Random.between(a: Double, b: Double): Double = minOf(a, b) + nextDouble() * abs(b - a)

private fun Random.emojiIndex(count: Int, previous: Int): Int {
    val index = (nextDouble() * count).toInt()
    val alternate = nextDouble()
    return if (count <= 3 || index != previous) index else (index + ceil(alternate * (count - 1)).toInt()) % count
}

/** 从左到右填充占用网格，尝试 2、3、4 格方块，受边界和已占用格约束缩小。 */
internal fun mosaicEmojiLayout(width: Int, height: Int, count: Int, t: Float, seed: Long): List<EmojiPlacement> {
    val cell = 45 + (305 * t).toInt()
    val occupied = Array(ceil(width.toFloat() / cell).toInt() + 2) {
        BooleanArray(ceil(height.toFloat() / cell).toInt() + 2)
    }
    val random = Random(seed)
    var previous = 0
    return buildList {
        for (x in occupied.indices) {
            for (y in occupied[x].indices) {
                var span = random.nextInt(3) + 2
                previous = random.emojiIndex(count, previous)
                fun fits(): Boolean = x + span <= occupied.size && y + span <= occupied[x].size &&
                    (x until x + span).all { cx -> (y until y + span).all { cy -> !occupied[cx][cy] } }
                while (span > 0 && !fits()) span--
                if (span == 0) continue
                for (cx in x until x + span) for (cy in y until y + span) occupied[cx][cy] = true
                val inset = cell * span * .8f / 2
                add(EmojiPlacement(previous, (x - .5f) * cell + inset, (y + span * .8f) * cell - inset, (cell * span * .8).toInt().toFloat()))
            }
        }
    }
}

/** 大小混排并保持空隙；尝试失败也消耗随机序列，避免切档后排列无规律地跳动。 */
internal fun scatterEmojiLayout(width: Int, height: Int, count: Int, t: Float, seed: Long): List<EmojiPlacement> {
    val base = 50 + (300 * t).toInt()
    val target = 18 - (base - 50) / 300.0 * 7
    val random = Random(seed)
    var previous = 0
    val result = mutableListOf<EmojiPlacement>()
    repeat(1000) {
        if (result.size >= target) return result
        val x = random.between(base * .15, width - base * 1.5).toFloat()
        val y = random.between(base * 1.5, height - base * 1.5).toFloat()
        previous = random.emojiIndex(count, previous)
        var rotation = random.between(-30.0, 30.0).toInt()
        if (result.isNotEmpty() && result.last().rotationDegrees < 0 && random.nextDouble() > .2) rotation = abs(rotation)
        val size = random.between(base * .5 + 4, base * 3.0).toInt().toFloat()
        val central = x > width * .2 && x < width * .8 && y > height * .2 && y < height * .8
        val hasCentral = result.any { it.centerX > width * .4 && it.centerX < width * .6 && it.centerY > height * .4 && it.centerY < height * .6 }
        if ((central || hasCentral) && result.none { hypot((it.centerX - x).toDouble(), (it.centerY - y).toDouble()) <= (it.size + size) * .85 }) {
            result += EmojiPlacement(previous, x, y, size, rotation.toFloat())
        }
    }
    return result
}

/** 棱镜的基础域使用逐点圆形装填，镜面负责产生被裁切的成对表情。 */
internal fun prismContents(count: Int, t: Float, seed: Long): List<EmojiPlacement> {
    val base = 5 + (55 * t).toInt()
    val random = Random(seed)
    val radii = mutableListOf<Double>()
    val result = mutableListOf<EmojiPlacement>()
    repeat(50) { index ->
        val x = (if (index == 0) .5f else random.nextFloat()) * 160
        val y = (if (index == 0) .5f else random.nextFloat()) * 100
        var available = minOf(x, 160 - x, y, 100 - y) * 1.25
        for (i in result.indices) {
            available = min(available, hypot((x - result[i].centerX).toDouble(), (y - result[i].centerY).toDouble()) - radii[i] - .35 * base / 4)
            if (available < 5) break
        }
        if (available >= 5) {
            val radius = min(base * .875, available)
            random.nextDouble()
            result += EmojiPlacement(index % count, x, y, (radius * 1.75).toInt().toFloat())
            radii += radius
        }
    }
    return result
}
