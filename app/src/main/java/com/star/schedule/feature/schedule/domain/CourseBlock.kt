package com.star.schedule.feature.schedule.domain

import com.star.schedule.core.database.CourseEntity

data class CourseBlock(
    val course: CourseEntity,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int,
)

fun buildCourseBlocks(courses: List<CourseEntity>): List<CourseBlock> =
    courses.flatMap { course ->
        val sortedPeriods = course.periods.distinct().sorted()
        if (sortedPeriods.isEmpty()) {
            emptyList()
        } else {
            buildList {
                var startPeriod = sortedPeriods.first()
                var previousPeriod = startPeriod
                sortedPeriods.drop(1).forEach { period ->
                    if (period != previousPeriod + 1) {
                        add(CourseBlock(course, course.dayOfWeek, startPeriod, previousPeriod))
                        startPeriod = period
                    }
                    previousPeriod = period
                }
                add(CourseBlock(course, course.dayOfWeek, startPeriod, previousPeriod))
            }
        }
    }
