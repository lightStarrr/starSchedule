package com.star.schedule.feature.importing.wakeup.data

import com.star.schedule.feature.importing.wakeup.domain.WakeUpCourse
import com.star.schedule.feature.importing.wakeup.domain.WakeUpImportParser
import com.star.schedule.feature.importing.wakeup.domain.WakeUpImportPlan
import com.star.schedule.feature.importing.wakeup.domain.WakeUpLessonTime
import com.star.schedule.feature.importing.wakeup.domain.parseWakeUpDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 解析 WakeUp 的 `shareData`：五段以换行分隔的 JSON
 * （baseConfig / timeSlots / uiConfig / courses / courseDetails）。
 *
 * 入参既可以是裸 shareData，也兼容旧版 `{"status":1,"data":"..."}` 信封，
 * 方便灰度期间两套取数链路并存。
 */
class WakeUpShareParser(
    private val json: Json = Json,
) : WakeUpImportParser {
    override fun parse(responseBody: String): WakeUpImportPlan {
        val segments = extractShareData(responseBody).split("\n")
        require(segments.size >= REQUIRED_SEGMENT_COUNT) {
            "WakeUp schedule data has ${segments.size} segments"
        }

        json.decodeFromString<JsonObject>(segments[TIMETABLE_SEGMENT])
        val lessonTimes = json.decodeFromString<JsonArray>(segments[LESSON_TIME_SEGMENT])
        val config = json.decodeFromString<JsonObject>(segments[CONFIG_SEGMENT])
        val courses = json.decodeFromString<JsonArray>(segments[COURSE_SEGMENT])
        val courseDetails = json.decodeFromString<JsonArray>(segments[COURSE_DETAIL_SEGMENT])

        return WakeUpImportPlan(
            timetableName = config["tableName"]?.jsonPrimitive?.contentOrNull,
            showWeekend = config["showSun"]?.jsonPrimitive?.boolean ?: true,
            startDate = config["startDate"]?.jsonPrimitive?.contentOrNull?.let(::parseWakeUpDate),
            lessonTimes = parseLessonTimes(lessonTimes, visibleNodeLimit(config["nodes"])),
            courses = parseCourses(courses, courseDetails),
        )
    }

    /**
     * `uiConfig.nodes` 是课表实际显示的节次数：有的版本是数字，有的是节点数组。
     * WakeUp 固定回 60 条时间（超出部分是 `00:00`），不按它截断就会凭空多出一堆夜里的节次。
     */
    private fun visibleNodeLimit(nodes: JsonElement?): Int? = when (nodes) {
        is JsonPrimitive -> nodes.intOrNull
        is JsonArray -> nodes.mapNotNull { it.jsonPrimitive.intOrNull }.maxOrNull()
        else -> null
    }?.takeIf { it > 0 }

    private fun extractShareData(body: String): String {
        val trimmed = body.trim()
        val element = runCatching { json.parseToJsonElement(trimmed) }.getOrNull()
        if (element is JsonObject) {
            element["status"]?.jsonPrimitive?.int?.let { status ->
                require(status == 1) { "WakeUp API returned an error status" }
            }
            element["data"]?.jsonPrimitive?.contentOrNull?.let { return it }
            element["shareData"]?.jsonPrimitive?.contentOrNull?.let { return it }
        }
        return trimmed
    }

    /**
     * 课表时间按时间重合去重：写入端的 `insertOrUpdateLessonTimeAutoSort` 会按开始时间
     * 重排并重编号 1..n，两个节次若共用同一时段，保留哪个都一样，留着反而会多出一节。
     *
     * 这里按 `node` 上限截断而不是按节点集合过滤：写入端会重编号，集合里有空洞的话
     * 后面的节次会整体前移，课程引用的节次号就对不上了。
     */
    private fun parseLessonTimes(
        lessonTimes: JsonArray,
        nodeLimit: Int?,
    ): List<WakeUpLessonTime> {
        val processedTimes = mutableSetOf<String>()
        return buildList {
            lessonTimes.forEach { element ->
                val lesson = element.jsonObject
                val period = lesson["node"]?.jsonPrimitive?.int ?: 1
                if (nodeLimit != null && period > nodeLimit) return@forEach

                val startTime = lesson["startTime"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val endTime = lesson["endTime"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                // 00:00 是占位；start == end 是零长度，两种都不该进课表。
                if (startTime == UNUSED_TIME || endTime == UNUSED_TIME) return@forEach
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
        val courseById = courses.associateBy {
            it.jsonObject["id"]?.jsonPrimitive?.int
        }

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
            val dayOfWeek = detail["day"]?.jsonPrimitive?.int ?: return@forEach
            val courseId = detail["id"]?.jsonPrimitive?.int ?: return@forEach
            // 课程被删掉、详情残留的情况在 WakeUp 里存在，跳过就好，不能让整次导入失败。
            val course = courseById[courseId] ?: return@forEach
            val courseName = course.jsonObject["courseName"]?.jsonPrimitive?.contentOrNull
                ?: return@forEach

            add(
                WakeUpCourse(
                    name = courseName,
                    // 教室和老师都可能没填，空着也要把这门课导进来。
                    location = detail["room"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    dayOfWeek = dayOfWeek,
                    periods = (startPeriod until startPeriod + step).toList(),
                    weeks = weeks,
                    teacher = detail["teacher"]?.jsonPrimitive?.contentOrNull.orEmpty(),
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
        const val UNUSED_TIME = "00:00"
    }
}
