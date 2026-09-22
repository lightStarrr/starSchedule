package com.star.schedule.feature.importing.parser

import java.io.InputStream

interface TimetableParser {
    val name: String

    suspend fun parse(inputStream: InputStream): ParseResult?
}
