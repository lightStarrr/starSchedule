package com.star.schedule.feature.schedule.presentation

import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.LessonTimeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimetableEmptyStateTest {
    private val lessonTime = LessonTimeEntity(
        timetableId = 1,
        period = 1,
        startTime = "08:00",
        endTime = "08:45",
    )

    @Test
    fun `missing timetable shows timetable empty state`() {
        assertEquals(
            TimetableEmptyState.NO_TIMETABLE,
            determineTimetableEmptyState(
                hasTimetable = false,
                lessonTimes = emptyList(),
                courses = emptyList(),
                currentWeek = 1,
            ),
        )
    }

    @Test
    fun `missing lesson times shows lesson time empty state`() {
        assertEquals(
            TimetableEmptyState.NO_LESSON_TIMES,
            determineTimetableEmptyState(
                hasTimetable = true,
                lessonTimes = emptyList(),
                courses = emptyList(),
                currentWeek = 1,
            ),
        )
    }

    @Test
    fun `missing courses in current week shows course empty state`() {
        val course = CourseEntity(
            timetableId = 1,
            name = "数学",
            teacher = "老师",
            location = "教学楼",
            dayOfWeek = 1,
            periods = listOf(1),
            weeks = listOf(2),
        )

        assertEquals(
            TimetableEmptyState.NO_COURSES,
            determineTimetableEmptyState(
                hasTimetable = true,
                lessonTimes = listOf(lessonTime),
                courses = listOf(course),
                currentWeek = 1,
            ),
        )
    }

    @Test
    fun `course in current week renders timetable`() {
        val course = CourseEntity(
            timetableId = 1,
            name = "数学",
            teacher = "老师",
            location = "教学楼",
            dayOfWeek = 1,
            periods = listOf(1),
            weeks = listOf(1),
        )

        assertNull(
            determineTimetableEmptyState(
                hasTimetable = true,
                lessonTimes = listOf(lessonTime),
                courses = listOf(course),
                currentWeek = 1,
            ),
        )
    }
}
