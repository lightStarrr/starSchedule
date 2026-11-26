package com.star.schedule.feature.timetable.domain

import com.star.schedule.core.database.entity.CourseEntity
import com.star.schedule.core.database.entity.CourseSessionEntity
import com.star.schedule.core.database.entity.CourseWithSessions
import com.star.schedule.core.database.entity.LessonTimeEntity
import com.star.schedule.feature.timetable.domain.CourseRules.CourseNameError
import com.star.schedule.feature.timetable.domain.CourseRules.CourseValidationError
import com.star.schedule.feature.timetable.domain.CourseRules.WeekParity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseRulesTest {

    @Test
    fun formatCompactRangesMergesConsecutiveNumbers() {
        assertEquals("", CourseRules.formatCompactRanges(emptyList()))
        assertEquals("3", CourseRules.formatCompactRanges(listOf(3)))
        assertEquals("1-2, 5, 7-9", CourseRules.formatCompactRanges(listOf(9, 1, 5, 2, 8, 7, 1)))
    }

    @Test
    fun formatWeeksSummaryRecognizesOddEvenAndMixedWeeks() {
        assertNull(CourseRules.formatWeeksSummary(emptyList()))
        assertNull(CourseRules.formatWeeksSummary(listOf(0, 31)))

        assertEquals(
            CourseRules.WeekRangeSummary("1-15", WeekParity.ODD, 8),
            CourseRules.formatWeeksSummary(listOf(1, 3, 5, 7, 9, 11, 13, 15)),
        )
        assertEquals(
            CourseRules.WeekRangeSummary("2-16", WeekParity.EVEN, 8),
            CourseRules.formatWeeksSummary(listOf(2, 4, 6, 8, 10, 12, 14, 16)),
        )
        assertEquals(
            CourseRules.WeekRangeSummary("1-8, 10-16", WeekParity.ALL, 15),
            CourseRules.formatWeeksSummary((1..8) + (10..16)),
        )
        assertEquals(
            CourseRules.WeekRangeSummary("3", WeekParity.ALL, 1),
            CourseRules.formatWeeksSummary(listOf(3)),
        )
    }

    @Test
    fun resolvePeriodTimeRangeRequiresContiguousConfiguredPeriods() {
        val lessonTimes = listOf(
            LessonTimeEntity(id = 1, timetableId = 1, period = 1, startTime = "08:00", endTime = "08:45"),
            LessonTimeEntity(id = 2, timetableId = 1, period = 2, startTime = "08:55", endTime = "09:40"),
            LessonTimeEntity(id = 3, timetableId = 1, period = 3, startTime = "10:00", endTime = "10:45"),
        )

        assertEquals("08:00" to "09:40", CourseRules.resolvePeriodTimeRange(listOf(2, 1), lessonTimes))
        assertNull(CourseRules.resolvePeriodTimeRange(listOf(1, 3), lessonTimes))
        assertNull(CourseRules.resolvePeriodTimeRange(listOf(1, 4), lessonTimes))
        assertNull(CourseRules.resolvePeriodTimeRange(emptyList(), lessonTimes))
    }

    @Test
    fun buildCourseGroupsKeepsEmptyCoursesComputesConflictsAndFilters() {
        val lessonTimes = listOf(
            LessonTimeEntity(id = 1, timetableId = 1, period = 1, startTime = "08:00", endTime = "08:45"),
            LessonTimeEntity(id = 2, timetableId = 1, period = 2, startTime = "08:55", endTime = "09:40"),
            LessonTimeEntity(id = 3, timetableId = 1, period = 3, startTime = "10:00", endTime = "10:45"),
            LessonTimeEntity(id = 4, timetableId = 1, period = 4, startTime = "10:55", endTime = "11:40"),
        )
        val math = CourseWithSessions(
            course = CourseEntity(id = 1L, timetableId = 1L, name = "高等数学"),
            sessions = listOf(
                CourseSessionEntity(
                    id = 10L,
                    courseId = 1L,
                    teacher = "张老师",
                    location = "教A-201",
                    dayOfWeek = 3,
                    periods = listOf(1, 2),
                    weeks = (1..16).toList(),
                ),
                CourseSessionEntity(
                    id = 11L,
                    courseId = 1L,
                    teacher = "张老师",
                    location = "教A-101",
                    dayOfWeek = 1,
                    periods = listOf(3, 4),
                    weeks = (1..16).toList(),
                ),
            ),
        )
        val physics = CourseWithSessions(
            course = CourseEntity(id = 2L, timetableId = 1L, name = "大学物理"),
            sessions = listOf(
                CourseSessionEntity(
                    id = 12L,
                    courseId = 2L,
                    teacher = "李老师",
                    location = "教B-302",
                    dayOfWeek = 1,
                    periods = listOf(3, 4),
                    weeks = listOf(1, 2, 3),
                ),
            ),
        )
        val emptyCourse = CourseWithSessions(
            course = CourseEntity(id = 3L, timetableId = 1L, name = "体育"),
            sessions = emptyList(),
        )

        val groups = CourseRules.buildCourseGroups(listOf(emptyCourse, math, physics), lessonTimes)
        assertEquals(3, groups.size)
        // 空时段课程排在最后
        assertEquals("体育", groups.last().name)
        assertTrue(groups.last().sessions.isEmpty())

        val mathGroup = groups.first { it.course.id == 1L }
        assertEquals(2, mathGroup.sessions.size)
        assertEquals(1, mathGroup.sessions[0].session.dayOfWeek)
        assertEquals(3, mathGroup.sessions[1].session.dayOfWeek)
        assertEquals(listOf("大学物理"), mathGroup.sessions[0].conflictsWithNames)
        assertTrue(mathGroup.sessions[1].conflictsWithNames.isEmpty())
        assertEquals("张老师", mathGroup.defaultTeacher)
        assertEquals((1..16).toList(), mathGroup.defaultWeeks)

        // 搜索老师名仅展示匹配到的时段，保留跨课程冲突提示
        val filteredByTeacher = CourseRules.buildCourseGroups(
            coursesWithSessions = listOf(emptyCourse, math, physics),
            lessonTimes = lessonTimes,
            query = "张老师",
        )
        assertEquals(1, filteredByTeacher.size)
        assertEquals(listOf("大学物理"), filteredByTeacher.single().sessions.first().conflictsWithNames)

        // 搜索课程名可以搜到 0 时段的空课程
        val filteredEmpty = CourseRules.buildCourseGroups(
            coursesWithSessions = listOf(emptyCourse, math, physics),
            lessonTimes = lessonTimes,
            query = "体育",
        )
        assertEquals(1, filteredEmpty.size)
        assertEquals("体育", filteredEmpty.single().name)
    }

    @Test
    fun findConflictsExcludesSelfAndDetectsOverlaps() {
        val existing = listOf(
            CourseWithSessions(
                course = CourseEntity(id = 1L, timetableId = 1L, name = "离散数学"),
                sessions = listOf(
                    CourseSessionEntity(
                        id = 10L,
                        courseId = 1L,
                        dayOfWeek = 2,
                        periods = listOf(1, 2),
                        weeks = listOf(1, 2, 3, 4),
                    ),
                ),
            ),
            CourseWithSessions(
                course = CourseEntity(id = 2L, timetableId = 1L, name = "数据结构"),
                sessions = listOf(
                    CourseSessionEntity(
                        id = 20L,
                        courseId = 2L,
                        dayOfWeek = 2,
                        periods = listOf(2, 3),
                        weeks = listOf(3, 4, 5),
                    ),
                ),
            ),
        )

        val conflictsOnEditSelf = CourseRules.findConflicts(
            editingSessionId = 10L,
            dayOfWeek = 2,
            periods = listOf(1, 2),
            weeks = listOf(1, 2, 3, 4),
            existingCourses = existing,
        )
        assertEquals(1, conflictsOnEditSelf.size)
        assertEquals("数据结构", conflictsOnEditSelf.single().courseName)
        assertEquals(listOf(2), conflictsOnEditSelf.single().overlapPeriods)
        assertEquals(listOf(3, 4), conflictsOnEditSelf.single().overlapWeeks)

        val noWeekOverlap = CourseRules.findConflicts(
            editingSessionId = null,
            dayOfWeek = 2,
            periods = listOf(1, 2),
            weeks = listOf(6, 7),
            existingCourses = existing,
        )
        assertTrue(noWeekOverlap.isEmpty())
    }

    @Test
    fun validateCourseNameChecksEmptyLengthAndDuplicates() {
        val existing = listOf("高等数学", "大学物理")
        assertEquals(CourseNameError.Empty, CourseRules.validateCourseName("   ", existing))
        assertEquals(CourseNameError.TooLong, CourseRules.validateCourseName("a".repeat(51), existing))
        assertEquals(CourseNameError.Duplicate, CourseRules.validateCourseName("高等数学 ", existing))
        assertNull(CourseRules.validateCourseName("线性代数", existing))
        // 传入 currentName 时允许保持原名
        assertNull(CourseRules.validateCourseName("高等数学", existing, currentName = "高等数学"))
    }

    @Test
    fun validateCourseDraftChecksAllConstraints() {
        val available = setOf(1, 2, 3, 4)
        assertEquals(
            CourseValidationError.EmptyName,
            CourseRules.validateCourseDraft("  ", "", "", listOf(1), listOf(1), available),
        )
        assertEquals(
            CourseValidationError.NameTooLong,
            CourseRules.validateCourseDraft("a".repeat(51), "", "", listOf(1), listOf(1), available),
        )
        assertEquals(
            CourseValidationError.TeacherTooLong,
            CourseRules.validateCourseDraft("高数", "t".repeat(51), "", listOf(1), listOf(1), available),
        )
        assertEquals(
            CourseValidationError.LocationTooLong,
            CourseRules.validateCourseDraft("高数", "", "l".repeat(101), listOf(1), listOf(1), available),
        )
        assertEquals(
            CourseValidationError.EmptyPeriods,
            CourseRules.validateCourseDraft("高数", "", "", emptyList(), listOf(1), available),
        )
        assertEquals(
            CourseValidationError.InvalidPeriod,
            CourseRules.validateCourseDraft("高数", "", "", listOf(5), listOf(1), available),
        )
        assertEquals(
            CourseValidationError.EmptyWeeks,
            CourseRules.validateCourseDraft("高数", "", "", listOf(1), emptyList(), available),
        )
        assertNull(
            CourseRules.validateCourseDraft("高数", "张老师", "教A-101", listOf(1, 2), listOf(1, 2), available),
        )
    }

    @Test
    fun computeRangeDragSelectionSelectsAndDeselectsRangesWithoutTouchingOutside() {
        val allowed = (1..20).toList()
        val base = setOf(1, 15, 16)

        assertEquals(
            setOf(1, 3, 4, 5, 6, 15, 16),
            CourseRules.computeRangeDragSelection(base, anchor = 3, current = 6, selectMode = true, allowedValues = allowed),
        )
        assertEquals(
            setOf(1, 3, 4, 5, 6, 15, 16),
            CourseRules.computeRangeDragSelection(base, anchor = 6, current = 3, selectMode = true, allowedValues = allowed),
        )
        assertEquals(
            setOf(1),
            CourseRules.computeRangeDragSelection(base, anchor = 16, current = 15, selectMode = false, allowedValues = allowed),
        )
        assertEquals(
            base,
            CourseRules.computeRangeDragSelection(base, anchor = 99, current = 3, selectMode = true, allowedValues = allowed),
        )
    }
}
