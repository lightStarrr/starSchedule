package com.star.schedule.feature.holiday.data

import com.star.schedule.feature.holiday.domain.HolidayDay
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 解析法定节假日年度数据，兼容两种格式：
 *
 * - holiday-cn：`{"year": 2025, "days": [{"name", "date": "yyyy-MM-dd", "isOffDay"}]}`
 * - jiejiariapi：`{"2025-01-01": {"date", "name", "isOffDay"}, ...}`
 *
 * 写库按「整年替换」，所以这里必须拒绝混入其它年份的日期，
 * 否则一份脏数据会把范围删除语义变成覆盖别人的数据。
 */
object HolidayCnParser {

    private val json = Json { ignoreUnknownKeys = true }
    private val dateKey = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    class MalformedHolidayData(message: String) : Exception(message)

    fun parseYear(body: String, expectedYear: Int): List<HolidayDay> {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (error: Exception) {
            throw MalformedHolidayData("invalid JSON: ${error.message}")
        }

        return when {
            root["days"] is JsonArray -> parseHolidayCnFormat(root, expectedYear)
            root.keys.isNotEmpty() && root.keys.all { it.matches(dateKey) } ->
                parseDateMapFormat(root, expectedYear)

            else -> throw MalformedHolidayData("unrecognized format")
        }
    }

    private fun parseHolidayCnFormat(root: JsonObject, expectedYear: Int): List<HolidayDay> {
        val year = root["year"]?.jsonPrimitive?.intOrNull
            ?: throw MalformedHolidayData("missing year")
        if (year != expectedYear) {
            throw MalformedHolidayData("year mismatch: expected $expectedYear, got $year")
        }

        val days = root["days"] as JsonArray
        if (days.isEmpty()) {
            throw MalformedHolidayData("empty days")
        }
        return days.map { element ->
            val day = element as? JsonObject
                ?: throw MalformedHolidayData("day entry is not an object")
            parseDayEntry(day, expectedYear)
        }
    }

    /**
     * jiejiariapi 会附带小年、除夕这类非法定节日的信息条目，
     * 它们标 `isOffDay=false` 但其实只是普通工作日，不是调休。
     * 统一成 holiday-cn 语义：只保留放假日和周末调休日。
     */
    private fun parseDateMapFormat(root: JsonObject, expectedYear: Int): List<HolidayDay> =
        root.entries.map { (_, element) ->
            val day = element as? JsonObject
                ?: throw MalformedHolidayData("day entry is not an object")
            parseDayEntry(day, expectedYear)
        }.filter { day ->
            day.isOffDay || day.date.dayOfWeek >= java.time.DayOfWeek.SATURDAY
        }.ifEmpty {
            throw MalformedHolidayData("empty days")
        }

    private fun parseDayEntry(day: JsonObject, expectedYear: Int): HolidayDay {
        val name = day["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: throw MalformedHolidayData("day entry missing name")
        val dateText = day["date"]?.jsonPrimitive?.content
            ?: throw MalformedHolidayData("day entry missing date")
        val date = try {
            LocalDate.parse(dateText)
        } catch (error: Exception) {
            throw MalformedHolidayData("bad date: $dateText")
        }
        if (date.year != expectedYear) {
            throw MalformedHolidayData("date outside year $expectedYear: $dateText")
        }
        val isOffDay = day["isOffDay"]?.jsonPrimitive?.booleanOrNull
            ?: throw MalformedHolidayData("day entry missing isOffDay")
        return HolidayDay(date = date, name = name, isOffDay = isOffDay)
    }
}
