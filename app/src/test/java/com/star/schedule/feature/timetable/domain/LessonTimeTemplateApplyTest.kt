package com.star.schedule.feature.timetable.domain

import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.LessonTimeTemplateItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 应用模板的规则：重编号与「哪些课会缺时间」。
 *
 * 这里锁两件事：写进课表的节次恒为 1..n（不然 AutoSort 会改坏课程引用）；
 * 越界课程只被报告、不被改动。
 */
class LessonTimeTemplateApplyTest {
    private fun item(period: Int, start: String, end: String) = LessonTimeTemplateItemEntity(
        id = 0,
        templateId = 1,
        period = period,
        startTime = start,
        endTime = end,
    )

    private fun course(name: String, periods: List<Int>) = CourseEntity(
        id = 0,
        timetableId = 1,
        name = name,
        teacher = "",
        location = "",
        dayOfWeek = 1,
        periods = periods,
        weeks = listOf(1),
    )

    @Test
    fun `normalize renumbers periods by start time`() {
        val periods = LessonTimeTemplateApply.normalize(
            listOf(
                item(period = 3, start = "09:00", end = "09:45"),
                item(period = 1, start = "08:00", end = "08:45"),
            ),
        )

        assertEquals(listOf(1, 2), periods.map { it.period })
        assertEquals(listOf("08:00", "09:00"), periods.map { it.startTime })
    }

    @Test
    fun `normalize is idempotent for already ordered items`() {
        val items = listOf(
            item(period = 1, start = "08:00", end = "08:45"),
            item(period = 2, start = "09:00", end = "09:45"),
        )

        assertEquals(
            LessonTimeTemplateApply.normalize(items),
            LessonTimeTemplateApply.normalize(items),
        )
        // 已经是 1..n 的模板不该被重排打乱。
        assertEquals(
            listOf("08:00", "09:00"),
            LessonTimeTemplateApply.normalize(items).map { it.startTime },
        )
    }

    @Test
    fun `normalize pushes a malformed start time to the end`() {
        val periods = LessonTimeTemplateApply.normalize(
            listOf(
                item(period = 1, start = "八点", end = "08:45"),
                item(period = 2, start = "09:00", end = "09:45"),
            ),
        )

        assertEquals(listOf("09:00", "八点"), periods.map { it.startTime })
        assertEquals(listOf(1, 2), periods.map { it.period })
    }

    @Test
    fun `a course entirely beyond the template is reported with all its periods`() {
        val periods = LessonTimeTemplateApply.normalize(
            listOf(
                item(1, "08:00", "08:45"),
                item(2, "09:00", "09:45"),
                item(3, "10:00", "10:45"),
                item(4, "11:00", "11:45"),
            ),
        )

        val missing = LessonTimeTemplateApply.missingLessonTimes(
            periods,
            listOf(course("晚自习", listOf(5, 6))),
        )

        assertEquals(listOf("晚自习"), missing.map { it.courseName })
        assertEquals(listOf(5, 6), missing.single().periods)
    }

    @Test
    fun `only the periods beyond the template are reported`() {
        val periods = LessonTimeTemplateApply.normalize(
            (1..4).map { item(it, "0${it + 7}:00", "0${it + 7}:45") },
        )

        val missing = LessonTimeTemplateApply.missingLessonTimes(
            periods,
            listOf(course("高等数学", listOf(3, 4, 5))),
        )

        assertEquals(listOf(5), missing.single().periods)
    }

    @Test
    fun `a course inside the template range is not reported`() {
        val periods = LessonTimeTemplateApply.normalize(
            (1..4).map { item(it, "0${it + 7}:00", "0${it + 7}:45") },
        )

        val missing = LessonTimeTemplateApply.missingLessonTimes(
            periods,
            listOf(course("大学物理", listOf(1, 2))),
        )

        assertTrue(missing.isEmpty())
    }

    @Test
    fun `preview returns null for a template without periods`() {
        assertNull(
            LessonTimeTemplateApply.preview(
                templateName = "空模板",
                timetableName = "2026 秋季学期",
                items = emptyList(),
                courses = listOf(course("大学物理", listOf(1))),
            ),
        )
    }

    @Test
    fun `preview carries the numbers the dialog shows`() {
        val preview = LessonTimeTemplateApply.preview(
            templateName = "早八作息",
            timetableName = "2026 秋季学期",
            items = listOf(
                item(1, "08:00", "08:45"),
                item(2, "09:00", "09:45"),
            ),
            courses = listOf(
                course("大学物理", listOf(1, 2)),
                course("晚自习", listOf(5)),
            ),
        )

        val value = requireNotNull(preview)
        assertEquals("早八作息", value.templateName)
        assertEquals("2026 秋季学期", value.timetableName)
        assertEquals(2, value.periodCount)
        assertTrue(value.hasMissing)
        assertEquals(listOf("晚自习"), value.missing.map { it.courseName })
    }

    @Test
    fun `a preview without missing courses says so`() {
        val preview = LessonTimeTemplateApply.preview(
            templateName = "早八作息",
            timetableName = "2026 秋季学期",
            items = listOf(item(1, "08:00", "08:45")),
            courses = listOf(course("大学物理", listOf(1))),
        )

        assertFalse(requireNotNull(preview).hasMissing)
    }
}
