package com.star.schedule.feature.importing.wakeup.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val wakeUpLabelledKeyPattern =
    "分享口令(?:为)?\\s*[「“\"]?\\s*([A-Za-z0-9_-]{1,200})".toRegex()

private val wakeUpQuotedKeyPattern =
    "[「“\"]\\s*([A-Za-z0-9_-]{8,200})\\s*[」”\"]".toRegex()

/** 最后兜底：文案里的任意一长串候选，取最后一个（口令通常在句末）。 */
private val wakeUpBareKeyPattern = "[A-Za-z0-9_-]{8,200}".toRegex()

/** 6.1.70 起口令不再保证是纯十六进制，字母、数字、下划线、短横线都可能是。 */
private val wakeUpKeyShape = "^[A-Za-z0-9_-]{8,200}$".toRegex()

/**
 * 从分享文案里取口令。
 *
 * 三档依次退让：先认「分享口令为「xxx」」，再认任意引号包裹，最后取文案里最后一个长候选。
 * 用户经常连整段分享文案一起粘进来，逐档兜底比死磕某一种写法更稳。
 */
fun extractKeyFromShareText(text: String): String {
    val candidates = listOfNotNull(
        wakeUpLabelledKeyPattern.find(text)?.groupValues?.get(1),
        wakeUpQuotedKeyPattern.find(text)?.groupValues?.get(1),
        wakeUpBareKeyPattern.findAll(text).lastOrNull()?.value,
    )
    return candidates.firstOrNull { it.matches(wakeUpKeyShape) }.orEmpty()
}

/**
 * WakeUp 的日期字段历史上出现过 `2026-9-2` 和 `2026/9/2` 两种写法。
 */
fun parseWakeUpDate(date: String): String {
    val parts = date.trim().replace('/', '-').split("-")
    require(parts.size == 3) { "Invalid date format: $date" }

    val year = parts[0].padStart(4, '0')
    val month = parts[1].padStart(2, '0')
    val day = parts[2].padStart(2, '0')
    return LocalDate.parse("$year-$month-$day", DateTimeFormatter.ISO_LOCAL_DATE).toString()
}
