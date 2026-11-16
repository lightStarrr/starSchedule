package com.star.schedule.feature.importing.sleepdown.domain

/** SleepDown 2.0 短口令字符集：Crockford Base32，不含 I / L / O / U。 */
private const val SLEEPDOWN_CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

private const val SLEEPDOWN_CODE_LENGTH = 10

// 字符集先放得宽，让 O / I / L 这种易混字符也能进 normalize 归一；
// 归一后仍不在字母表里的（比如 U）判无效。
private const val CODE_CHARS = "0-9A-Za-z"

private val embeddedCodePattern =
    "(?i)\\bSD[-\\s]?([$CODE_CHARS]{5})[-\\s]?([$CODE_CHARS]{5})\\b".toRegex()

private val labeledCodePattern =
    "(?i)短口令[：:\\s]*([$CODE_CHARS]{5})[-\\s]?([$CODE_CHARS]{5})\\b".toRegex()

private val bareCodePattern =
    "(?i)^(?:SD[-\\s]?)?([$CODE_CHARS]{5})[-\\s]?([$CODE_CHARS]{5})$".toRegex()

private val codeShape = "^[$SLEEPDOWN_CODE_ALPHABET]{$SLEEPDOWN_CODE_LENGTH}$".toRegex()

/**
 * 从分享文案里取 SleepDown 2.0 短口令（`SD-XXXXX-XXXXX` 或裸 10 位）。
 *
 * 顺序与上游一致：先整串匹配，再找 `SD-` 前缀的内嵌码，最后找「短口令：」标记。
 * 找不到返回空串。
 */
fun extractSleepDownCode(text: String): String {
    val input = text.trim()

    bareCodePattern.matchEntire(input)?.let { match ->
        normalizeSleepDownCode(match.groupValues[1] + match.groupValues[2])
            ?.let { return it }
    }
    embeddedCodePattern.find(input)?.let { match ->
        normalizeSleepDownCode(match.groupValues[1] + match.groupValues[2])
            ?.let { return it }
    }
    labeledCodePattern.find(input)?.let { match ->
        normalizeSleepDownCode(match.groupValues[1] + match.groupValues[2])
            ?.let { return it }
    }
    return ""
}

/**
 * 转大写、去掉分隔符，O→0、I/L→1，剩下的必须正好 10 位且全在字母表内。
 * 12 位且以「SD」开头的去掉前缀再校验（与上游 normalize 同一规则）。
 */
private fun normalizeSleepDownCode(candidate: String): String? {
    val normalized = buildString(candidate.length) {
        candidate.forEach { char ->
            when {
                char == '-' || char == ' ' || char == '\t' || char == '\n' || char == '\r' -> Unit
                char == 'O' || char == 'o' -> append('0')
                char == 'I' || char == 'i' || char == 'L' || char == 'l' -> append('1')
                else -> append(char.uppercaseChar())
            }
        }
    }
    val code = if (normalized.length == SLEEPDOWN_CODE_LENGTH + 2 &&
        normalized.startsWith("SD")
    ) {
        normalized.drop(2)
    } else {
        normalized
    }
    return code.takeIf { it.matches(codeShape) }
}
