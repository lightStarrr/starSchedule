package com.star.schedule.provider

import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.LessonTimeEntity
import com.star.schedule.core.database.TimetableEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WakeUpScheduleMapperTest {
    private val date = LocalDate.of(2026, 9, 16)
    private val timetable = TimetableEntity(
        id = 1,
        name = "测试课表",
        showWeekend = true,
        startDate = "2026-09-14"
    )
    private val lessonTimes = listOf(
        lesson(1, "08:00", "08:45"),
        lesson(2, "08:55", "09:40"),
        lesson(3, "10:00", "10:45"),
        lesson(4, "11:00", "11:45")
    )

    @Test
    fun exportsActiveCoursesInTimeOrderAndSplitsGaps() {
        val courses = listOf(
            course(id = 5, name = "第二门", periods = listOf(3)),
            course(id = 10, name = "拆分课程", periods = listOf(4, 2, 1)),
            course(id = 20, name = "其他周", periods = listOf(1), weeks = listOf(2)),
            course(id = 30, name = "其他天", periods = listOf(1), dayOfWeek = 4)
        )

        val result = Json.parseToJsonElement(
            WakeUpScheduleMapper.toJson(
                date,
                timetable,
                courses,
                lessonTimes,
                ZoneId.of("Asia/Shanghai")
            )
        ).jsonArray

        assertEquals(3, result.size)
        assertEquals(listOf(10001L, 5003L, 10004L), result.map {
            it.jsonObject.getValue("id").jsonPrimitive.content.toLong()
        })
        assertEquals("08:00", result.first().jsonObject.getValue("startTime").jsonPrimitive.content)
        assertEquals("09:40", result.first().jsonObject.getValue("endTime").jsonPrimitive.content)
        assertEquals("#ff3f8cff", result.first().jsonObject.getValue("color").jsonPrimitive.content)
        assertEquals(
            date.atTime(LocalTime.of(8, 0)).atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond(),
            result.first().jsonObject.getValue("startTimestamp").jsonPrimitive.content.toLong()
        )
        assertEquals(
            date.atTime(LocalTime.of(9, 40)).atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond(),
            result.first().jsonObject.getValue("endTimestamp").jsonPrimitive.content.toLong()
        )
    }

    @Test
    fun returnsEmptyOutsideSemesterOrWithoutTimetable() {
        assertEquals(
            "[]",
            WakeUpScheduleMapper.toJson(date.minusDays(3), timetable, listOf(course()), lessonTimes)
        )
        assertEquals("[]", WakeUpScheduleMapper.toJson(date, null, listOf(course()), lessonTimes))
    }

    @Test
    fun skipsCourseWhenItsLessonTimeIsMissing() {
        assertEquals(
            "[]",
            WakeUpScheduleMapper.toJson(date, timetable, listOf(course(periods = listOf(1, 2))), lessonTimes.take(1))
        )
    }

    private fun lesson(period: Int, start: String, end: String) = LessonTimeEntity(
        timetableId = 1,
        period = period,
        startTime = start,
        endTime = end
    )

    private fun course(
        id: Long = 1,
        name: String = "课程",
        periods: List<Int> = listOf(1),
        weeks: List<Int> = listOf(1),
        dayOfWeek: Int = 3
    ) = CourseEntity(
        id = id,
        timetableId = 1,
        name = name,
        teacher = "教师",
        location = "教室",
        dayOfWeek = dayOfWeek,
        periods = periods,
        weeks = weeks
    )
}
