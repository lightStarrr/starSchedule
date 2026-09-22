package com.star.schedule.feature.importing.wakeup.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val wakeUpShareKeyPattern = "分享口令为「([a-f0-9]+)」".toRegex()

fun extractKeyFromShareText(text: String): String =
    wakeUpShareKeyPattern.find(text)?.groupValues?.get(1).orEmpty()

fun parseWakeUpDate(date: String): String {
    val parts = date.split("-")
    require(parts.size == 3) { "Invalid date format: $date" }

    val year = parts[0].padStart(4, '0')
    val month = parts[1].padStart(2, '0')
    val day = parts[2].padStart(2, '0')
    return LocalDate.parse("$year-$month-$day", DateTimeFormatter.ISO_LOCAL_DATE).toString()
}
