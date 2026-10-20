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
        val payload = payload(
            timetable = "{}",
            lessonTimes = """[{"node":1,"startTime":"08:00","endTime":"08:45"},{"node":2,"startTime":"08:00","endTime":"08:45"},{"node":3,"startTime":"10:00","endTime":"10:00"}]""",
            config = """{"tableName":"测试课表","showSun":false,"startDate":"2026-9-2"}""",
            courses = """[{"id":7,"courseName":"高等数学"}]""",
            courseDetails = """[{"id":7,"startWeek":1,"endWeek":6,"type":1,"startNode":1,"step":2,"room":"A101","teacher":"张老师","day":2}]""",
        )

        val plan = parser.parse(envelope(payload))

        assertEquals("测试课表", plan.timetableName)
        assertEquals(false, plan.showWeekend)
        assertEquals("2026-09-02", plan.startDate)
        assertEquals(1, plan.lessonTimes.size)
        assertEquals(1, plan.lessonTimes.single().period)
        assertEquals(listOf(1, 2), plan.courses.single().periods)
        assertEquals(listOf(1, 3, 5), plan.courses.single().weeks)
    }

    /** 6.1.70 拿到的就是裸 shareData，没有 status/data 信封。 */
    @Test
    fun parsesRawShareData() {
        val payload = payload(
            timetable = "{}",
            lessonTimes = """[{"node":1,"startTime":"08:00","endTime":"08:50"}]""",
            config = """{"tableName":"2026 秋","showSun":true,"startDate":"2026/8/31"}""",
            courses = """[{"id":1,"courseName":"测试"}]""",
            courseDetails = """[{"id":1,"startWeek":1,"endWeek":20,"type":0,"startNode":1,"step":2,"room":"","teacher":"","day":1}]""",
        )

        val plan = parser.parse(payload)

        assertEquals("2026 秋", plan.timetableName)
        assertEquals("2026-08-31", plan.startDate)
        assertEquals(1, plan.lessonTimes.size)
        assertEquals("08:00", plan.lessonTimes.single().startTime)
        assertEquals(20, plan.courses.single().weeks.size)
    }

    /** 教室和老师经常是空的，不能因为这两个字段就把整门课丢掉。 */
    @Test
    fun keepsCoursesWithoutTeacherOrRoom() {
        val plan = parser.parse(
            payload(
                timetable = "{}",
                lessonTimes = """[]""",
                config = """{"showSun":true}""",
                courses = """[{"id":3,"courseName":"自习"}]""",
                courseDetails = """[{"id":3,"startWeek":2,"endWeek":4,"type":2,"startNode":3,"step":1,"day":5}]""",
            ),
        )

        val course = plan.courses.single()
        assertEquals("", course.location)
        assertEquals("", course.teacher)
        assertEquals(listOf(2, 4), course.weeks)
        assertEquals(listOf(3), course.periods)
    }

    /** 课程被删、详情残留时跳过该条，别让整次导入失败。 */
    @Test
    fun skipsDetailsWhoseCourseIsGone() {
        val plan = parser.parse(
            payload(
                timetable = "{}",
                lessonTimes = """[]""",
                config = """{}""",
                courses = """[{"id":1,"courseName":"存在的课"}]""",
                courseDetails = """[{"id":1,"startWeek":1,"endWeek":2,"type":0,"startNode":1,"step":1,"day":1},{"id":9,"startWeek":1,"endWeek":2,"type":0,"startNode":1,"step":1,"day":1}]""",
            ),
        )

        assertEquals(listOf("存在的课"), plan.courses.map { it.name })
    }

    /** WakeUp 固定回 60 条时间，超出 `uiConfig.nodes` 的是占位，不能导进课表。 */
    @Test
    fun truncatesNodesBeyondTheConfiguredCount() {
        val plan = parser.parse(
            payload(
                timetable = "{}",
                lessonTimes = """[{"node":1,"startTime":"08:00","endTime":"08:50"},{"node":21,"startTime":"23:00","endTime":"23:50"},{"node":30,"startTime":"00:00","endTime":"00:00"}]""",
                config = """{"nodes":20}""",
                courses = """[]""",
                courseDetails = """[]""",
            ),
        )

        assertEquals(listOf(1), plan.lessonTimes.map { it.period })
    }

    /** `nodes` 也可能是节点数组，这时按最大节点号截断。 */
    @Test
    fun truncatesByNodeListBoundary() {
        val plan = parser.parse(
            payload(
                timetable = "{}",
                lessonTimes = """[{"node":1,"startTime":"08:00","endTime":"08:50"},{"node":4,"startTime":"11:00","endTime":"11:50"}]""",
                config = """{"nodes":[1,2,3]}""",
                courses = """[]""",
                courseDetails = """[]""",
            ),
        )

        assertEquals(listOf(1), plan.lessonTimes.map { it.period })
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

    private fun payload(
        timetable: String,
        lessonTimes: String,
        config: String,
        courses: String,
        courseDetails: String,
    ): String = listOf(timetable, lessonTimes, config, courses, courseDetails).joinToString("\n")

    private fun envelope(payload: String): String = buildJsonObject {
        put("status", 1)
        put("data", payload)
    }.toString()
}
