package com.star.schedule.feature.importing.wakeup.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val wakeUpShareKeyPattern = "分享口令为「([a-f0-9]+)」".toRegex()

/** 只在完整文案里找不到口令时才用：单独一串十六进制。 */
private val bareWakeUpKeyPattern = "\\b[a-f0-9]{12,}\\b".toRegex()

/**
 * 从分享文案里取口令。
 *
 * 优先认「分享口令为「xxx」」这句；文案改版或用户直接粘贴口令时，退回到裸的十六进制串，
 * 免得用户看着输入框里的内容却一直提示"未找到有效口令"。
 */
fun extractKeyFromShareText(text: String): String {
    wakeUpShareKeyPattern.find(text)?.let { return it.groupValues[1] }
    return bareWakeUpKeyPattern.find(text.lowercase())?.value.orEmpty()
}

fun parseWakeUpDate(date: String): String {
    val parts = date.split("-")
    require(parts.size == 3) { "Invalid date format: $date" }

    val year = parts[0].padStart(4, '0')
    val month = parts[1].padStart(2, '0')
    val day = parts[2].padStart(2, '0')
    return LocalDate.parse("$year-$month-$day", DateTimeFormatter.ISO_LOCAL_DATE).toString()
}
