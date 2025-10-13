package com.star.schedule.utils.parser

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

object TimetableParserManager {
    private val parsers = mutableListOf<TimetableParser>()

    fun register(parser: TimetableParser) {
        parsers.add(parser)
    }

    suspend fun tryParse(inputStream: InputStream): ParseResult? = withContext(Dispatchers.IO) {
        val bytes = inputStream.readBytes()
        inputStream.close()

        if (parsers.isEmpty()) {
            Log.e("TimetableParserManager", "No parser registered!")
            return@withContext null
        }

        for (parser in parsers) {
            try {
                Log.d("TimetableParserManager", "Trying parser: ${parser.name}")
                val copyStream = bytes.inputStream()
                val result = parser.parse(copyStream)
                copyStream.close()

                if (result != null) {
                    Log.d("TimetableParserManager", "Parsed successfully with ${parser.name}")
                    return@withContext result
                }
            } catch (e: Exception) {
                Log.e("TimetableParserManager", "Parser ${parser.name} failed", e)
            }
        }

        Log.e("TimetableParserManager", "No parser could handle the file")
        null
    }

}
