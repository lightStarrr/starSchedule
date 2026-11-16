package com.star.schedule.feature.importing.sleepdown.data

import com.star.schedule.feature.importing.sharecode.domain.ImportedCourse
import com.star.schedule.feature.importing.sharecode.domain.ImportedLessonTime
import com.star.schedule.feature.importing.sharecode.domain.ImportedTimetablePlan
import com.star.schedule.feature.importing.sleepdown.domain.SleepDownImportParser
import com.star.schedule.feature.importing.sleepdown.domain.SleepDownUnsupportedPayloadException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime

@Serializable
private data class SleepDownTimeSlot(
    val number: Int = 0,
    val startTime: String = "",
    val endTime: String = "",
    val alias: String? = null,
)

@Serializable
private data class SleepDownConfig(
    val semesterStartDate: String? = null,
    val semesterTotalWeeks: Int = 20,
    val defaultClassDuration: Int = 45,
    val defaultBreakDuration: Int = 10,
    val firstDayOfWeek: Int = 1,
)

@Serializable
private data class SleepDownCourseMeta(
    val groupId: String = "",
    val mode: String = "",
    val periods: List<Int> = emptyList(),
    val timetableId: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
)

@Serializable
private data class SleepDownCourse(
    val name: String = "",
    val teacher: String = "",
    val position: String = "",
    val day: Int = 0,
    val startSection: Int? = null,
    val endSection: Int? = null,
    val weeks: List<Int> = emptyList(),
    val isCustomTime: Boolean = false,
    val customStartTime: String? = null,
    val customEndTime: String? = null,
    val color: Int? = null,
    val remark: String? = null,
    val credit: Float? = null,
    val sleepdown: SleepDownCourseMeta? = null,
)

@Serializable
private data class SleepDownPayload(
    val courses: List<SleepDownCourse> = emptyList(),
    val timeSlots: List<SleepDownTimeSlot> = emptyList(),
    val config: SleepDownConfig? = null,
)

/**
 * 把短口令换回的 canonical 课表 JSON 映射成可写库的 [ImportedTimetablePlan]。
 *
 * 映射是宽容的：节次时间格式不对、星期越界、周次为空、排不出节次的课程
 * 一律丢弃而不是整单失败；全部丢完才说明这张课表不是我们认得的形态。
 */
class SleepDownShareParser(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    },
) : SleepDownImportParser {
    override fun parse(payload: String): ImportedTimetablePlan {
        val root = json.decodeFromString<SleepDownPayload>(payload)
        val slots = parseSlots(root.timeSlots)
        val courses = parseCourses(root.courses, slots)
        if (courses.isEmpty()) {
            throw SleepDownUnsupportedPayloadException("SleepDown payload maps to no courses")
        }
        return ImportedTimetablePlan(
            timetableName = null,
            showWeekend = courses.any { it.dayOfWeek >= SATURDAY },
            startDate = root.config?.semesterStartDate?.let(::parseDateOrNull),
            lessonTimes = slots.map { (number, start, end) ->
                ImportedLessonTime(
                    period = number,
                    startTime = start.toString(),
                    endTime = end.toString(),
                )
            },
            courses = courses,
        )
    }

    private fun parseSlots(slots: List<SleepDownTimeSlot>): List<ParsedSlot> =
        slots.mapNotNull { slot ->
            val start = parseClock(slot.startTime) ?: return@mapNotNull null
            val end = parseClock(slot.endTime) ?: return@mapNotNull null
            ParsedSlot(slot.number, start, end)
        }.sortedBy { it.number }.distinctBy { it.number }

    private fun parseCourses(
        courses: List<SleepDownCourse>,
        slots: List<ParsedSlot>,
    ): List<ImportedCourse> {
        val seenGroups = mutableSetOf<String>()
        return courses.mapNotNull { course ->
            val meta = course.sleepdown
            if (!meta?.groupId.isNullOrEmpty() && !seenGroups.add(meta!!.groupId)) {
                return@mapNotNull null
            }
            val weeks = course.weeks.filter { it > 0 }.distinct().sorted()
            if (weeks.isEmpty() || course.day !in 1..DAYS_PER_WEEK) return@mapNotNull null

            val periods = when {
                !meta?.periods.isNullOrEmpty() -> meta!!.periods.distinct().sorted()
                course.startSection != null && course.endSection != null ->
                    (course.startSection!!..course.endSection!!).toList()
                course.isCustomTime -> overlapPeriods(course, slots)
                else -> emptyList()
            }
            if (periods.isEmpty()) return@mapNotNull null

            ImportedCourse(
                name = course.name,
                location = course.position,
                dayOfWeek = course.day,
                periods = periods,
                weeks = weeks,
                teacher = course.teacher.trim(),
            )
        }
    }

    /** 自定义时间课：把 [customStartTime, customEndTime) 覆盖到的节次号都算作这门课的节次。 */
    private fun overlapPeriods(course: SleepDownCourse, slots: List<ParsedSlot>): List<Int> {
        val start = course.customStartTime?.let(::parseClock) ?: return emptyList()
        val end = course.customEndTime?.let(::parseClock) ?: return emptyList()
        return slots.filter { it.start < end && it.end > start }.map { it.number }
    }

    private fun parseDateOrNull(value: String): String? =
        runCatching { LocalDate.parse(value).toString() }.getOrNull()

    private fun parseClock(value: String): LocalTime? {
        val match = CLOCK_PATTERN.matchEntire(value.trim()) ?: return null
        return runCatching {
            LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }.getOrNull()
    }

    private data class ParsedSlot(val number: Int, val start: LocalTime, val end: LocalTime)

    private companion object {
        val CLOCK_PATTERN = "^(\\d{1,2}):(\\d{2})$".toRegex()
        const val SATURDAY = 6
        const val DAYS_PER_WEEK = 7
    }
}
