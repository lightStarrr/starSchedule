package com.star.schedule.feature.timetable.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RowHeightPreviewSampleTest {

    @Test
    fun alwaysCreatesFullPeriodList() {
        val lessonTimes = RowHeightPreviewSample.lessonTimes()

        assertEquals(RowHeightPreviewSample.PERIOD_COUNT, lessonTimes.size)
        lessonTimes.forEachIndexed { index, lessonTime ->
            assertEquals(index + 1, lessonTime.period)
            assertEquals("08:00", lessonTimes.first().startTime)
        }
    }

    @Test
    fun periodsAreAscendingAndNonOverlapping() {
        val lessonTimes = RowHeightPreviewSample.lessonTimes()

        lessonTimes.zipWithNext().forEach { (current, next) ->
            assertTrue("${current.endTime} 应早于 ${next.startTime}", current.endTime < next.startTime)
        }
    }

    @Test
    fun sampleCoursesHaveBlankText() {
        val sample = RowHeightPreviewSample.create(Random(7))

        assertTrue(sample.courses.isNotEmpty())
        sample.courses.forEach { course ->
            assertEquals(" ", course.name)
            assertEquals(" ", course.location)
            assertEquals("", course.teacher)
        }
    }

    @Test
    fun sampleCoursesFitInsideGrid() {
        val sample = RowHeightPreviewSample.create(Random(11))

        sample.courses.forEach { course ->
            assertTrue(course.dayOfWeek in 1..5)
            assertEquals(listOf(1), course.weeks)
            assertTrue(course.periods.isNotEmpty())
            course.periods.forEach { period ->
                assertTrue(period in 1..RowHeightPreviewSample.PERIOD_COUNT)
            }
        }
    }

    @Test
    fun coursesDoNotOverlapOnTheSameDay() {
        val sample = RowHeightPreviewSample.create(Random(2026))

        sample.courses.groupBy { it.dayOfWeek }.forEach { (day, courses) ->
            val used = courses.flatMap { it.periods }
            assertEquals("第 $day 天有重叠的课块", used.size, used.toSet().size)
        }
    }

    @Test
    fun sameSeedProducesSameLayout() {
        val first = RowHeightPreviewSample.create(Random(99))
        val second = RowHeightPreviewSample.create(Random(99))

        assertEquals(first, second)
    }
}
