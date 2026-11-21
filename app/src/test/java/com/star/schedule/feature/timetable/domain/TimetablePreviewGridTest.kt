package com.star.schedule.feature.timetable.domain

import com.star.schedule.core.database.entity.CourseEntity
import com.star.schedule.core.database.entity.LessonTimeEntity
import com.star.schedule.core.database.entity.TimetableEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 行高页、壁纸预览页共用的整页课表数据。 */
class TimetablePreviewGridTest {
    private val timetable = TimetableEntity(
        id = 1,
        name = "测试课表",
        showWeekend = true,
        startDate = "2025-09-01", // 周一
        rowHeight = 72,
    )
    private val lessonTimes = listOf(
        LessonTimeEntity(timetableId = 1, period = 2, startTime = "10:00", endTime = "10:45"),
        LessonTimeEntity(timetableId = 1, period = 1, startTime = "09:00", endTime = "09:45"),
    )
    private val sample = RowHeightPreviewSample.create()

    private fun course(dayOfWeek: Int, weeks: List<Int>) = CourseEntity(
        timetableId = 1,
        name = "课",
        teacher = "",
        location = "",
        dayOfWeek = dayOfWeek,
        periods = listOf(1, 2),
        weeks = weeks,
    )

    @Test
    fun `picks the busiest week`() {
        val grid = timetablePreviewGrid(
            timetable = timetable,
            courses = listOf(course(1, listOf(1)), course(2, listOf(2)), course(3, listOf(2))),
            lessonTimes = lessonTimes,
            sample = sample,
            fallbackRowHeight = 60,
        )

        assertEquals(2, grid.week)
        assertEquals(LocalDate.of(2025, 9, 8), grid.weekStartDate)
        assertEquals(72, grid.rowHeight)
        // 时间列按节次排序，与页面一致。
        assertEquals(listOf(1, 2), grid.lessonTimes.map { it.period })
    }

    @Test
    fun `falls back to the sample when the timetable has no lesson times`() {
        val grid = timetablePreviewGrid(
            timetable = timetable,
            courses = emptyList(),
            lessonTimes = emptyList(),
            sample = sample,
            fallbackRowHeight = 60,
        )

        assertEquals(sample.lessonTimes, grid.lessonTimes)
        assertEquals(sample.courses, grid.courses)
    }

    @Test
    fun `weekend columns appear only when a weekend course exists`() {
        val withWeekend = timetablePreviewGrid(
            timetable = timetable,
            courses = listOf(course(6, listOf(1))),
            lessonTimes = lessonTimes,
            sample = sample,
            fallbackRowHeight = 60,
        )
        val weekdaysOnly = timetablePreviewGrid(
            timetable = timetable,
            courses = listOf(course(5, listOf(1))),
            lessonTimes = lessonTimes,
            sample = sample,
            fallbackRowHeight = 60,
        )

        assertTrue(withWeekend.showWeekend)
        assertFalse(weekdaysOnly.showWeekend)
    }

    @Test
    fun `missing timetable uses fallbacks`() {
        val today = LocalDate.of(2025, 10, 15) // 周三
        val grid = timetablePreviewGrid(
            timetable = null,
            courses = emptyList(),
            lessonTimes = lessonTimes,
            sample = sample,
            fallbackRowHeight = 60,
            today = today,
        )

        assertEquals(60, grid.rowHeight)
        assertEquals(1, grid.week)
        assertEquals(LocalDate.of(2025, 10, 13), grid.weekStartDate)
    }
}
