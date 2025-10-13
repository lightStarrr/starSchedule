package com.star.schedule.utils.parser.algorithms

import android.util.Log
import com.star.schedule.utils.parser.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.CellType
import java.io.InputStream

class XuexitongParser2 : TimetableParser {
    override val name = "学习通算法2"

    override suspend fun parse(inputStream: InputStream): ParseResult? = withContext(Dispatchers.IO) {
        Log.d("XuexitongParser", "Before try block, stream.available = ${inputStream.available()}")
        try {
            val workbook = HSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)

            data class WeekInfo(val col: Int, val weekDay: Int)

            val weekRegex = Regex("""星期\s*([一二三四五六日])""")
            val weekList = mutableListOf<WeekInfo>()
            val courses = mutableListOf<ParsedCourse>()

            val evaluator = workbook.creationHelper.createFormulaEvaluator()

            val weekRow = sheet.getRow(3)
            if (weekRow != null) {
                for (colIndex in 1..7) {
                    val cell = weekRow.getCell(colIndex) ?: continue

                    val cellValue = when (cell.cellTypeEnum) {
                        CellType.FORMULA -> evaluator.evaluate(cell).formatAsString()
                        CellType.STRING -> cell.stringCellValue
                        CellType.NUMERIC -> cell.numericCellValue.toInt().toString()
                        else -> cell.toString()
                    }.replace("\n", "").replace("\r", "").trim()

                    val match = weekRegex.find(cellValue)
                    if (match != null) {
                        val weekStr = match.groupValues[1]
                        val weekNum = when (weekStr) {
                            "一" -> 1
                            "二" -> 2
                            "三" -> 3
                            "四" -> 4
                            "五" -> 5
                            "六" -> 6
                            "日" -> 7
                            else -> return@withContext null
                        }
                        weekList.add(WeekInfo(col = colIndex, weekDay = weekNum))
                    }
                }
            }

            fun parseWeeks(weekStr: String): List<Int> {
                val result = mutableListOf<Int>()
                val parts = weekStr.replace("周", "").split(",")
                for (part in parts) {
                    val rangeMatch = Regex("""(\d+)-(\d+)""").find(part)
                    if (rangeMatch != null) {
                        val start = rangeMatch.groupValues[1].toInt()
                        val end = rangeMatch.groupValues[2].toInt()
                        for (w in start..end) result.add(w)
                    } else {
                        val single = part.toIntOrNull()
                        if (single != null) result.add(single)
                    }
                }
                return result
            }

            fun mergeOrCreateCourse(newCourse: ParsedCourse, slot: Int) {
                val existing = courses.find {
                    it.name == newCourse.name &&
                            it.teacher == newCourse.teacher &&
                            it.weeks == newCourse.weeks &&
                            it.location == newCourse.location &&
                            it.weekDay == newCourse.weekDay
                }
                if (existing != null) {
                    val mutableTimeSlots = existing.timeSlots.toMutableList()
                    mutableTimeSlots.add(slot)
                    val updatedCourse = ParsedCourse(
                        name = existing.name,
                        teacher = existing.teacher,
                        weeks = existing.weeks,
                        location = existing.location,
                        timeSlots = mutableTimeSlots,
                        weekDay = existing.weekDay
                    )
                    courses.remove(existing)
                    courses.add(updatedCourse)
                } else {
                    val courseWithSlot = ParsedCourse(
                        name = newCourse.name,
                        teacher = newCourse.teacher,
                        weeks = newCourse.weeks,
                        location = newCourse.location,
                        timeSlots = listOf(slot),
                        weekDay = newCourse.weekDay
                    )
                    courses.add(courseWithSlot)
                }
            }

            for (week in weekList) {
                for (rowIndex in 4..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex) ?: continue
                    val cell = row.getCell(week.col) ?: continue
                    val cellText = cell.toString().trim()
                    if (cellText.isEmpty()) continue

                    val cellLines = cellText.lines().map { it.trim() }.filter { it.isNotEmpty() }

                    for (i in cellLines.indices step 3) {
                        if (i + 2 >= cellLines.size) break

                        val name = cellLines[i]
                        val teacherLine = cellLines[i + 1]
                        val location = cellLines[i + 2]

                        val teacherMatch = Regex("""(.+?)【(.+)】""").find(teacherLine)
                        val teacher = teacherMatch?.groupValues?.get(1)?.trim() ?: ""
                        val weeks = teacherMatch?.groupValues?.get(2)?.let { parseWeeks(it) } ?: emptyList()

                        val newCourse = ParsedCourse(
                            name = name,
                            teacher = teacher,
                            weeks = weeks,
                            location = location,
                            timeSlots = mutableListOf(),
                            weekDay = week.weekDay
                        )

                        mergeOrCreateCourse(newCourse, rowIndex - 3)
                    }

                }
            }

            if (weekList.isEmpty() || courses.isEmpty()) {
                workbook.close()
                inputStream.close()
                return@withContext null
            }

            workbook.close()
            inputStream.close()

            ParseResult(
                timeSlots = emptyList(),
                courses = courses
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
