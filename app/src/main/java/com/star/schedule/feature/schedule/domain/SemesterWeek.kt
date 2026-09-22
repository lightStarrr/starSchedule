package com.star.schedule.feature.schedule.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun LocalDate.getWeekOfSemester(startDate: LocalDate): Int {
    val days = ChronoUnit.DAYS.between(startDate, this)
    return (days / 7 + 1).toInt()
}
