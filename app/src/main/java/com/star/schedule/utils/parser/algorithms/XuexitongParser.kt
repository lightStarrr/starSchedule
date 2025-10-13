package com.star.schedule.utils.parser.algorithms

import android.util.Log
import com.star.schedule.utils.parser.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.CellType
import java.io.InputStream

class XuexitongParser : TimetableParser {
    override val name = "学习通算法1"

    override suspend fun parse(inputStream: InputStream): ParseResult? = withContext(Dispatchers.IO) {
        Log.d("XuexitongParser", "Before try block, stream.available = ${inputStream.available()}")
        try {
            Log.d("XuexitongParser", "Parsing timetable...")
            val workbook = HSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)

            data class TimeSlot(val row: Int, val start: String, val end: String)
            data class WeekInfo(val col: Int, val weekDay: Int)

            val timeListRegex = Regex("""\d{1,2}:\d{2}""")
            val weekRegex = Regex("""星期\s*([一二三四五六日])""")

            val timeList = mutableListOf<TimeSlot>()
            val weekList = mutableListOf<WeekInfo>()
            val courses = mutableListOf<ParsedCourse>()

            val evaluator = workbook.creationHelper.createFormulaEvaluator()

            for (rowIndex in 0..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue
                val cell = row.getCell(4) ?: continue
                val text = cell.toString().trim()
                if (text.isEmpty()) continue

                val lines =
                    text.split("\n", "\r\n").map { it.trim() }.filter { it.matches(timeListRegex) }

                if (lines.size == 2) {
                    timeList.add(TimeSlot(row = rowIndex + 1, start = lines[0], end = lines[1]))
                }
            }

            val weekRow = sheet.getRow(5)
            if (weekRow != null) {
                for (colIndex in 0 until weekRow.lastCellNum) {
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
                        weekList.add(WeekInfo(col = colIndex + 1, weekDay = weekNum))
                    }
                }
            }

            fun parseWeeks(weekStr: String): List<Int> {
                val result = mutableListOf<Int>()
                val regex = Regex("""(\d+)(?:-(\d+))?""")
                regex.findAll(weekStr).forEach {
                    val start = it.groupValues[1].toInt()
                    val end = it.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: start
                    for (w in start..end) result.add(w)
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
                for ((i, time) in timeList.withIndex()) {
                    val row = sheet.getRow(time.row - 1) ?: continue
                    val cell = row.getCell(week.col - 1) ?: continue
                    val cellText = cell.toString().trim()
                    if (cellText.isEmpty()) continue

                    val records = cellText.split("………………").map { it.trim() }.filter { it.isNotEmpty() }
                    for (record in records) {
                        val lines = record.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        if (lines.size < 3) continue

                        val name = lines[0]
                        val teacherLine = lines[1]
                        val teacherMatch = Regex("""(.+?)【(.+)】""").find(teacherLine)
                        val teacher = teacherMatch?.groupValues?.get(1)?.trim() ?: ""
                        val weeks =
                            teacherMatch?.groupValues?.get(2)?.let { parseWeeks(it) } ?: emptyList()
                        val location = lines[2]

                        val newCourse = ParsedCourse(
                            name = name,
                            teacher = teacher,
                            weeks = weeks,
                            location = location,
                            timeSlots = mutableListOf(),
                            weekDay = week.weekDay
                        )

                        mergeOrCreateCourse(newCourse, i + 1)
                    }
                }
            }

            fun <T> List<T>.hasNoData(): Boolean = this.isEmpty() || this.all {
                when (it) {
                    is TimeSlot -> it.start.isEmpty() || it.end.isEmpty()
                    is WeekInfo -> false
                    is ParsedCourse -> it.name.isEmpty() || it.teacher.isEmpty() || it.weeks.isEmpty() || it.location.isEmpty() || it.timeSlots.isEmpty()
                    else -> false
                }
            }

            if (timeList.hasNoData() || weekList.hasNoData() || courses.hasNoData()) {
                Log.e("XuexitongImport", "解析失败: timeList, weekList 或 courses 内部数据无效")
                workbook.close()
                inputStream.close()
                return@withContext null
            }

            workbook.close()
            if (timeList.isEmpty() || weekList.isEmpty() || courses.isEmpty()) {
                return@withContext null
            }
            workbook.close()
            inputStream.close()

            ParseResult(
                timeSlots = timeList.map { ParsedTimeSlot(it.start, it.end) },
                courses = courses
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
