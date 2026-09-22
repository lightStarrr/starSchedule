package com.star.schedule.feature.importing.wakeup.data

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WakeUpShareParserTest {
    private val parser = WakeUpShareParser()

    @Test
    fun parsesScheduleAndPreservesWakeUpRules() {
        val response = responseWithData(
            timetable = "{}",
            lessonTimes = """[{"node":1,"startTime":"08:00","endTime":"08:45"},{"node":2,"startTime":"08:00","endTime":"08:45"},{"node":3,"startTime":"10:00","endTime":"10:00"}]""",
            config = """{"tableName":"测试课表","showSun":false,"startDate":"2026-9-2"}""",
            courses = """[{"id":7,"courseName":"高等数学"}]""",
            courseDetails = """[{"id":7,"startWeek":1,"endWeek":6,"type":1,"startNode":1,"step":2,"room":"A101","teacher":"张老师","day":2}]""",
        )

        val plan = parser.parse(response)

        assertEquals("测试课表", plan.timetableName)
        assertEquals(false, plan.showWeekend)
        assertEquals("2026-09-02", plan.startDate)
        assertEquals(1, plan.lessonTimes.size)
        assertEquals(1, plan.lessonTimes.single().period)
        assertEquals(listOf(1, 2), plan.courses.single().periods)
        assertEquals(listOf(1, 3, 5), plan.courses.single().weeks)
    }

    @Test
    fun rejectsErrorStatusAndIncompletePayload() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(buildJsonObject { put("status", 0) }.toString())
        }
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse(
                buildJsonObject {
                    put("status", 1)
                    put("data", "{}\n[]\n{}\n[]")
                }.toString(),
            )
        }
    }

    private fun responseWithData(
        timetable: String,
        lessonTimes: String,
        config: String,
        courses: String,
        courseDetails: String,
    ): String = buildJsonObject {
        put("status", 1)
        put(
            "data",
            listOf(timetable, lessonTimes, config, courses, courseDetails).joinToString("\n"),
        )
    }.toString()
}
