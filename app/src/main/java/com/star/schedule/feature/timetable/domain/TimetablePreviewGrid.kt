package com.star.schedule.feature.timetable.domain

import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.LessonTimeEntity
import com.star.schedule.core.database.TimetableEntity
import com.star.schedule.feature.schedule.domain.buildCourseBlocksByWeek
import java.time.DayOfWeek
import java.time.LocalDate

/** 行高页、壁纸预览页共用的整页课表数据。 */
data class TimetablePreviewGrid(
    // 课最多的一周：预览内容最满，行高、壁纸效果都最容易看出来。
    val week: Int,
    val weekStartDate: LocalDate,
    val lessonTimes: List<LessonTimeEntity>,
    val courses: List<CourseEntity>,
    val showWeekend: Boolean,
    val rowHeight: Int,
)

/**
 * 从课表数据算出预览网格：
 * - 还没排节次时间时用 [sample] 示例课表撑页面，否则只剩一排空白表头；
 * - 显示「课最多的一周」；周末列只在真的有周末课时出现。
 *
 * [today] 只在课表没有学期开始日期时用来对齐本周一，测试可以固定住。
 */
fun timetablePreviewGrid(
    timetable: TimetableEntity?,
    courses: List<CourseEntity>,
    lessonTimes: List<LessonTimeEntity>,
    sample: RowHeightPreviewTimetable,
    fallbackRowHeight: Int,
    today: LocalDate = LocalDate.now(),
): TimetablePreviewGrid {
    val hasLessonTimes = lessonTimes.isNotEmpty()
    val gridLessonTimes = if (hasLessonTimes) lessonTimes else sample.lessonTimes
    val gridCourses = if (hasLessonTimes) courses else sample.courses
    val busiestWeek = buildCourseBlocksByWeek(gridCourses)
        .maxByOrNull { (_, blocks) -> blocks.size }
        ?.key
        ?: 1
    val semesterStart = timetable?.startDate
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: today.with(DayOfWeek.MONDAY)
    return TimetablePreviewGrid(
        week = busiestWeek,
        weekStartDate = semesterStart.plusWeeks((busiestWeek - 1).toLong()),
        lessonTimes = gridLessonTimes.sortedBy { it.period },
        courses = gridCourses,
        showWeekend = gridCourses.any { it.dayOfWeek > 5 },
        rowHeight = timetable?.rowHeight ?: fallbackRowHeight,
    )
}
