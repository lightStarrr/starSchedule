package com.star.schedule.feature.importing.wakeup.data

import com.star.schedule.feature.importing.wakeup.domain.WakeUpCourse
import com.star.schedule.feature.importing.wakeup.domain.WakeUpImportParser
import com.star.schedule.feature.importing.wakeup.domain.WakeUpImportPlan
import com.star.schedule.feature.importing.wakeup.domain.WakeUpLessonTime
import com.star.schedule.feature.importing.wakeup.domain.parseWakeUpDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WakeUpShareParser(
    private val json: Json = Json,
) : WakeUpImportParser {
    override fun parse(responseBody: String): WakeUpImportPlan {
        val root = json.parseToJsonElement(responseBody).jsonObject
        require(root["status"]?.jsonPrimitive?.int == 1) { "WakeUp API returned an error status" }

        val data = requireNotNull(root["data"]?.jsonPrimitive?.content) {
            "WakeUp API returned no schedule data"
        }
        val segments = data.split("\n")
        require(segments.size >= REQUIRED_SEGMENT_COUNT) {
            "WakeUp schedule data has ${segments.size} segments"
        }

        json.decodeFromString<JsonObject>(segments[TIMETABLE_SEGMENT])
        val lessonTimes = json.decodeFromString<JsonArray>(segments[LESSON_TIME_SEGMENT])
        val config = json.decodeFromString<JsonObject>(segments[CONFIG_SEGMENT])
        val courses = json.decodeFromString<JsonArray>(segments[COURSE_SEGMENT])
        val courseDetails = json.decodeFromString<JsonArray>(segments[COURSE_DETAIL_SEGMENT])

        return WakeUpImportPlan(
            timetableName = config["tableName"]?.jsonPrimitive?.content,
            showWeekend = config["showSun"]?.jsonPrimitive?.boolean ?: true,
            startDate = config["startDate"]?.jsonPrimitive?.content?.let(::parseWakeUpDate),
            lessonTimes = parseLessonTimes(lessonTimes),
            courses = parseCourses(courses, courseDetails),
        )
    }

    private fun parseLessonTimes(lessonTimes: JsonArray): List<WakeUpLessonTime> {
        val processedTimes = mutableSetOf<String>()
        return buildList {
            lessonTimes.forEach { element ->
                val lesson = element.jsonObject
                val period = lesson["node"]?.jsonPrimitive?.int ?: 1
                val startTime = lesson["startTime"]?.jsonPrimitive?.content ?: return@forEach
                val endTime = lesson["endTime"]?.jsonPrimitive?.content ?: return@forEach
                if (startTime == endTime) return@forEach

                val timeKey = "${startTime}_${endTime}"
                if (!processedTimes.add(timeKey)) return@forEach

                add(
                    WakeUpLessonTime(
                        period = period,
                        startTime = startTime,
                        endTime = endTime,
                    ),
                )
            }
        }
    }

    private fun parseCourses(
        courses: JsonArray,
        courseDetails: JsonArray,
    ): List<WakeUpCourse> = buildList {
        courseDetails.forEach { element ->
            val detail = element.jsonObject
            val startWeek = detail["startWeek"]?.jsonPrimitive?.int ?: return@forEach
            val endWeek = detail["endWeek"]?.jsonPrimitive?.int ?: return@forEach
            val type = detail["type"]?.jsonPrimitive?.int ?: return@forEach
            val weeks = when (type) {
                ODD_WEEK_TYPE -> (startWeek..endWeek).filter { it and 1 == 1 }
                EVEN_WEEK_TYPE -> (startWeek..endWeek).filter { it and 1 == 0 }
                else -> (startWeek..endWeek).toList()
            }

            val startPeriod = detail["startNode"]?.jsonPrimitive?.int ?: return@forEach
            val step = detail["step"]?.jsonPrimitive?.int ?: return@forEach
            val periods = (startPeriod until startPeriod + step).toList()
            val location = detail["room"]?.jsonPrimitive?.content ?: return@forEach
            val courseId = detail["id"]?.jsonPrimitive?.int ?: return@forEach
            val teacher = detail["teacher"]?.jsonPrimitive?.content ?: return@forEach
            val course = courses.firstOrNull {
                it.jsonObject["id"]?.jsonPrimitive?.int == courseId
            } ?: error("WakeUp course $courseId is missing")
            val courseName = course.jsonObject["courseName"]?.jsonPrimitive?.content ?: return@forEach
            val dayOfWeek = detail["day"]?.jsonPrimitive?.int ?: return@forEach

            add(
                WakeUpCourse(
                    name = courseName,
                    location = location,
                    dayOfWeek = dayOfWeek,
                    periods = periods,
                    weeks = weeks,
                    teacher = teacher,
                ),
            )
        }
    }

    private companion object {
        const val TIMETABLE_SEGMENT = 0
        const val LESSON_TIME_SEGMENT = 1
        const val CONFIG_SEGMENT = 2
        const val COURSE_SEGMENT = 3
        const val COURSE_DETAIL_SEGMENT = 4
        const val REQUIRED_SEGMENT_COUNT = 5
        const val ODD_WEEK_TYPE = 1
        const val EVEN_WEEK_TYPE = 2
    }
}
