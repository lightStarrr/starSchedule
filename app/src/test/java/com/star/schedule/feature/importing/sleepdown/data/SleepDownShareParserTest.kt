package com.star.schedule.feature.importing.sleepdown.data

import com.star.schedule.feature.importing.sleepdown.domain.SleepDownUnsupportedPayloadException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepDownShareParserTest {
    private val parser = SleepDownShareParser()

    private fun payload(
        courses: String,
        timeSlots: String = DEFAULT_SLOTS,
        config: String? = null,
    ): String = buildString {
        append("""{"courses":""")
        append(courses)
        append(""","timeSlots":""")
        append(timeSlots)
        config?.let { append(""","config":""").append(it) }
        append("}")
    }

    @Test
    fun `standard section course maps directly`() {
        val plan = parser.parse(
            payload(
                courses = """[{"name":"高数","teacher":"张老师 ","position":"A101","day":1,"startSection":1,"endSection":2,"weeks":[1,2,3]}]""",
            ),
        )
        assertEquals(6, plan.lessonTimes.size)
        assertEquals(1, plan.courses.size)
        val course = plan.courses[0]
        assertEquals("高数", course.name)
        assertEquals("张老师", course.teacher)
        assertEquals("A101", course.location)
        assertEquals(1, course.dayOfWeek)
        assertEquals(listOf(1, 2), course.periods)
        assertEquals(listOf(1, 2, 3), course.weeks)
        assertEquals(false, plan.showWeekend)
    }

    @Test
    fun `same groupId keeps only first row and uses extension periods`() {
        val plan = parser.parse(
            payload(
                courses = """[
                    {"name":"实验","day":2,"startSection":3,"endSection":4,"weeks":[1,2],
                     "sleepdown":{"groupId":"g1","mode":"FOLLOW_TABLE","periods":[5,3]}},
                    {"name":"实验","day":2,"startSection":5,"endSection":6,"weeks":[1,2],
                     "sleepdown":{"groupId":"g1","mode":"FOLLOW_TABLE","periods":[5,6]}}
                ]""",
            ),
        )
        assertEquals(1, plan.courses.size)
        assertEquals(listOf(3, 5), plan.courses[0].periods)
    }

    @Test
    fun `custom time course maps to overlapping slots`() {
        val plan = parser.parse(
            payload(
                courses = """[{"name":"自习","day":3,"weeks":[2],"isCustomTime":true,"customStartTime":"08:30","customEndTime":"09:30"}]""",
            ),
        )
        assertEquals(listOf(1, 2), plan.courses[0].periods)
    }

    @Test
    fun `unmappable and invalid rows are dropped`() {
        val plan = parser.parse(
            payload(
                courses = """[
                    {"name":"有效","day":1,"startSection":1,"endSection":1,"weeks":[1]},
                    {"name":"无节次","day":1,"weeks":[1]},
                    {"name":"空周次","day":1,"startSection":1,"endSection":1,"weeks":[]},
                    {"name":"星期越界","day":8,"startSection":1,"endSection":1,"weeks":[1]}
                ]""",
            ),
        )
        assertEquals(1, plan.courses.size)
        assertEquals("有效", plan.courses[0].name)
    }

    @Test
    fun `weekend courses enable showWeekend`() {
        val plan = parser.parse(
            payload(
                courses = """[{"name":"周末课","day":7,"startSection":1,"endSection":1,"weeks":[1]}]""",
            ),
        )
        assertTrue(plan.showWeekend)
    }

    @Test
    fun `weeks are deduplicated and sorted, non-positive dropped`() {
        val plan = parser.parse(
            payload(
                courses = """[{"name":"课","day":1,"startSection":1,"endSection":1,"weeks":[3,0,-1,1,3]}]""",
            ),
        )
        assertEquals(listOf(1, 3), plan.courses[0].weeks)
    }

    @Test
    fun `valid semester start date parses, invalid becomes null`() {
        val valid = parser.parse(
            payload(
                courses = """[{"name":"课","day":1,"startSection":1,"endSection":1,"weeks":[1]}]""",
                config = """{"semesterStartDate":"2026-09-01","semesterTotalWeeks":20}""",
            ),
        )
        assertEquals("2026-09-01", valid.startDate)

        val invalid = parser.parse(
            payload(
                courses = """[{"name":"课","day":1,"startSection":1,"endSection":1,"weeks":[1]}]""",
                config = """{"semesterStartDate":"明年九月"}""",
            ),
        )
        assertNull(invalid.startDate)
    }

    @Test
    fun `slot times normalize to HHmm and sort by number`() {
        val plan = parser.parse(
            payload(
                courses = """[{"name":"课","day":1,"startSection":1,"endSection":1,"weeks":[1]}]""",
                timeSlots = """[
                    {"number":2,"startTime":"8:55","endTime":"09:40"},
                    {"number":1,"startTime":"8:00","endTime":"8:45"},
                    {"number":1,"startTime":"10:00","endTime":"10:45"}
                ]""",
            ),
        )
        assertEquals(2, plan.lessonTimes.size)
        assertEquals(1, plan.lessonTimes[0].period)
        assertEquals("08:00", plan.lessonTimes[0].startTime)
        assertEquals("08:45", plan.lessonTimes[0].endTime)
        assertEquals(2, plan.lessonTimes[1].period)
        assertEquals("08:55", plan.lessonTimes[1].startTime)
    }

    @Test
    fun `unparseable slot time is dropped`() {
        val plan = parser.parse(
            payload(
                courses = """[{"name":"课","day":1,"startSection":1,"endSection":1,"weeks":[1]}]""",
                timeSlots = """[
                    {"number":1,"startTime":"08:00","endTime":"08:45"},
                    {"number":2,"startTime":"八点","endTime":"09:40"}
                ]""",
            ),
        )
        assertEquals(1, plan.lessonTimes.size)
    }

    @Test(expected = SleepDownUnsupportedPayloadException::class)
    fun `all courses dropped throws unsupported`() {
        parser.parse(payload(courses = """[]"""))
    }

    @Test(expected = SleepDownUnsupportedPayloadException::class)
    fun `course list with only invalid rows throws unsupported`() {
        parser.parse(payload(courses = """[{"name":"无节次","day":1,"weeks":[1]}]"""))
    }

    @Test(expected = Exception::class)
    fun `malformed json throws serialization error`() {
        parser.parse("not json at all")
    }

    private companion object {
        const val DEFAULT_SLOTS = """[
            {"number":1,"startTime":"08:00","endTime":"08:45"},
            {"number":2,"startTime":"08:55","endTime":"09:40"},
            {"number":3,"startTime":"10:00","endTime":"10:45"},
            {"number":4,"startTime":"10:55","endTime":"11:40"},
            {"number":5,"startTime":"14:00","endTime":"14:45"},
            {"number":6,"startTime":"14:55","endTime":"15:40"}
        ]"""
    }
}
