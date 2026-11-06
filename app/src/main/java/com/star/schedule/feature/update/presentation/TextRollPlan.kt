package com.star.schedule.feature.update.presentation

// kept: (旧索引, 新索引)，两列都严格递增
internal data class TextRollPlan(val kept: List<Pair<Int, Int>>)

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
    if (!(prefix + suffix > 0 && (prefix + suffix) * 2 >= minSize)) {
        prefix = 0
        suffix = 0
    }

    val oldSepIdx = (prefix until old.size - suffix)
        .filter { old[it].isRollSeparator() }
    val newSepIdx = (prefix until new.size - suffix)
        .filter { new[it].isRollSeparator() }
    val middlePairs = lcsPairs(old, new, oldSepIdx, newSepIdx)

    val kept = buildList {
        for (i in 0 until prefix) add(i to i)
        addAll(middlePairs)
        for (i in 0 until suffix) {
            add(old.size - suffix + i to new.size - suffix + i)
        }
    }
    return TextRollPlan(kept)
}

// 分隔符不随内容变化而滚动，只在中间段里做 LCS 配对
private fun String.isRollSeparator(): Boolean {
    if (length != 1) return false
    val c = this[0]
    return c.code < 128 && !c.isLetterOrDigit()
}

private fun lcsPairs(
    old: List<String>,
    new: List<String>,
    oldIdx: List<Int>,
    newIdx: List<Int>,
): List<Pair<Int, Int>> {
    val m = oldIdx.size
    val n = newIdx.size
    if (m == 0 || n == 0) return emptyList()
    val dp = Array(m + 1) { IntArray(n + 1) }
    for (i in 1..m) {
        for (j in 1..n) {
            dp[i][j] = if (old[oldIdx[i - 1]] == new[newIdx[j - 1]]) {
                dp[i - 1][j - 1] + 1
            } else {
                maxOf(dp[i - 1][j], dp[i][j - 1])
            }
        }
    }
    val pairs = ArrayList<Pair<Int, Int>>()
    var i = m
    var j = n
    while (i > 0 && j > 0) {
        when {
            old[oldIdx[i - 1]] == new[newIdx[j - 1]] -> {
                pairs += oldIdx[i - 1] to newIdx[j - 1]
                i--
                j--
            }
            dp[i - 1][j] >= dp[i][j - 1] -> i--
            else -> j--
        }
    }
    return pairs.reversed()
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
