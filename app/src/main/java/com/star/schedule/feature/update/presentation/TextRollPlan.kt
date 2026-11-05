package com.star.schedule.feature.update.presentation

internal data class TextRollPlan(
    val prefixLength: Int,
    val suffixLength: Int,
    val wholeChange: Boolean,
)

internal fun String.rollGlyphs(): List<String> =
    codePoints().toArray().map { String(Character.toChars(it)) }

internal fun planTextRoll(old: List<String>, new: List<String>): TextRollPlan {
    val minSize = minOf(old.size, new.size)
    var prefix = 0
    while (prefix < minSize && old[prefix] == new[prefix]) prefix++
    var suffix = 0
    while (suffix < minSize - prefix &&
        old[old.size - 1 - suffix] == new[new.size - 1 - suffix]
    ) {
        suffix++
    }
    if (prefix + suffix > 0 && (prefix + suffix) * 2 >= minSize) {
        return TextRollPlan(prefix, suffix, wholeChange = false)
    }
    return TextRollPlan(0, 0, wholeChange = true)
}

internal fun rollStaggerDelays(
    count: Int,
    maxIntervalMillis: Long = 30,
    maxSpreadMillis: Long = 200,
): List<Long> {
    if (count <= 0) return emptyList()
    val interval = if (count == 1) {
        0L
    } else {
        minOf(maxIntervalMillis, maxSpreadMillis / (count - 1))
    }
    return List(count) { it * interval }
}
