package com.star.schedule.utils.parser.algorithms

import android.util.Log
import com.star.schedule.utils.parser.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.CellType
import java.io.InputStream

class YinghuaParser1 : TimetableParser {
    override val name = "Yinghua算法1"

    override suspend fun parse(inputStream: InputStream): ParseResult? = withContext(Dispatchers.IO) {
        try {
            val workbook = HSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)
            val evaluator = workbook.creationHelper.createFormulaEvaluator()

            data class WeekInfo(val col: Int, val weekDay: Int)
            val weekRegex = Regex("""星期\s*([一二三四五六日])""")
            val weekList = mutableListOf<WeekInfo>()

            val weekRow = sheet.getRow(2)
            if (weekRow != null) {
                for (colIndex in 1..7) { // B列开始
                    val cell = weekRow.getCell(colIndex) ?: continue
                    val cellValue = when (cell.cellTypeEnum) {
                        CellType.FORMULA -> evaluator.evaluate(cell).formatAsString()
                        CellType.STRING -> cell.stringCellValue
                        CellType.NUMERIC -> cell.numericCellValue.toInt().toString()
                        else -> cell.toString()
                    }.trim()
                    val match = weekRegex.find(cellValue)
                    if (match != null) {
                        val weekDay = when (match.groupValues[1]) {
                            "一" -> 1
                            "二" -> 2
                            "三" -> 3
                            "四" -> 4
                            "五" -> 5
                            "六" -> 6
                            "日" -> 7
                            else -> continue
                        }
                        weekList.add(WeekInfo(colIndex, weekDay))
                    }
                }
            }

            val courses = mutableListOf<ParsedCourse>()

            fun parseWeeks(weekStr: String): List<Int> {
                val range = Regex("""(\d+)-(\d+)""").find(weekStr)
                return if (range != null) {
                    (range.groupValues[1].toInt()..range.groupValues[2].toInt()).toList()
                } else {
                    weekStr.filter { it.isDigit() }.map { it.toString().toInt() }
                }
            }

            fun parseTimeSlots(cellText: String): List<Int> {
                val slotRegex = Regex("""\[(\d{2}(?:-\d{2})*)节]""")
                val match = slotRegex.find(cellText)
                return match?.groupValues?.get(1)?.split("-")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
            }

            // 合并或新增课程
            fun mergeOrCreateCourse(newCourse: ParsedCourse) {
                val existing = courses.find {
                    it.name == newCourse.name &&
                            it.teacher == newCourse.teacher &&
                            it.location == newCourse.location &&
                            it.weekDay == newCourse.weekDay &&
                            it.weeks == newCourse.weeks
                }
                if (existing != null) {
                    val mergedSlots = (existing.timeSlots + newCourse.timeSlots).distinct().sorted()
                    courses.remove(existing)
                    courses.add(existing.copy(timeSlots = mergedSlots))
                } else {
                    courses.add(newCourse)
                }
            }

            for (week in weekList) {
                for (rowIndex in 3..sheet.lastRowNum) {
                    val row = sheet.getRow(rowIndex) ?: continue
                    val cell = row.getCell(week.col) ?: continue
                    val cellText = cell.toString().trim()
                    if (cellText.isEmpty()) continue

                    val blocks = cellText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                    for (block in blocks) {
                        val parts = block.split("◇").map { it.trim() }
                        if (parts.size < 5) continue

                        val name = parts[0].substringBefore("[")
                        val teacher = parts[1]
//                        val clazz = parts[2]
                        val weekInfo = parts[3]
                        val location = parts.last()

                        val weeks = parseWeeks(weekInfo)
                        val timeSlots = parseTimeSlots(block)

                        val newCourse = ParsedCourse(
                            name = name,
                            teacher = teacher,
                            weeks = weeks,
                            location = location,
                            timeSlots = timeSlots,
                            weekDay = week.weekDay
                        )
                        mergeOrCreateCourse(newCourse)
                    }
                }
            }

            workbook.close()
            inputStream.close()

            if (courses.isEmpty()) {
                Log.e("YinghuaParser1", "未解析到课程数据")
                return@withContext null
            }

            Log.d("YinghuaParser1", "成功解析 ${courses.size} 门课程")
            ParseResult(
                timeSlots = emptyList(),
                courses = courses
            )

        } catch (e: Exception) {
            Log.e("YinghuaParser1", "解析失败: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}
