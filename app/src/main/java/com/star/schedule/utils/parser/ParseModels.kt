package com.star.schedule.utils.parser

data class ParsedTimeSlot(
    val start: String,
    val end: String
)

data class ParsedCourse(
    val name: String,
    val teacher: String,
    val weeks: List<Int>,
    val location: String,
    val timeSlots: List<Int>,
    val weekDay: Int
)

data class ParseResult(
    val timeSlots: List<ParsedTimeSlot>,
    val courses: List<ParsedCourse>
)
