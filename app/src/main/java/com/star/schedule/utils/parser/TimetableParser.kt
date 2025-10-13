package com.star.schedule.utils.parser

import java.io.InputStream

interface TimetableParser {
    val name: String

    suspend fun parse(inputStream: InputStream): ParseResult?
}
