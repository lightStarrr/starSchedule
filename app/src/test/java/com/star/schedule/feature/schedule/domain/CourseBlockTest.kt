package com.star.schedule.feature.schedule.domain

import com.star.schedule.core.database.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CourseBlockTest {
    @Test
    fun `continuous periods are rendered as one block`() {
        val course = course(periods = listOf(1, 2, 3))

        assertEquals(
            listOf(CourseBlock(course, dayOfWeek = 1, startPeriod = 1, endPeriod = 3)),
            buildCourseBlocks(listOf(course)),
        )
    }

    @Test
    fun `gaps create separate blocks and duplicate periods are ignored`() {
        val course = course(periods = listOf(1, 2, 2, 4, 6))

        assertEquals(
            listOf(
                CourseBlock(course, 1, 1, 2),
                CourseBlock(course, 1, 4, 4),
                CourseBlock(course, 1, 6, 6),
            ),
            buildCourseBlocks(listOf(course)),
        )
    }

    @Test
    fun `blocks are indexed for every teaching week`() {
        val course = course(periods = listOf(1, 2)).copy(weeks = listOf(1, 2, 2))

        assertEquals(
            listOf(CourseBlock(course, 1, 1, 2)),
            buildCourseBlocksByWeek(listOf(course))[2],
        )
    }

    private fun course(periods: List<Int>) = CourseEntity(
        timetableId = 1,
        name = "数学",
        teacher = "老师",
        location = "教学楼",
        dayOfWeek = 1,
        periods = periods,
        weeks = listOf(1),
    )
}
