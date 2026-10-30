package com.star.schedule.feature.timetable.domain

import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.LessonTimeTemplateItemEntity

/** 应用模板时真正写进 `lesson_time` 的一节。 */
data class AppliedPeriod(
    val period: Int,
    val startTime: String,
    val endTime: String,
)

/**
 * 某门课在应用模板后会「没有时间」的节次。
 *
 * 课程本身不动——节次号超出模板节数时，课表网格、提醒和小组件都会跳过它，
 * 结果是这几节课照旧在课表里，但看不到上下课时间。
 */
data class CourseMissingPeriods(
    val courseName: String,
    val periods: List<Int>,
)

/** 确认弹窗要展示的内容；[missing] 为空表示模板覆盖了所有课程用到的节次。 */
data class LessonTimeApplyPreview(
    val templateName: String,
    val timetableName: String,
    val periodCount: Int,
    val missing: List<CourseMissingPeriods>,
) {
    val hasMissing: Boolean get() = missing.isNotEmpty()
}

/**
 * 把模板套到课表上的规则。
 *
 * 这几条规则同时给「确认弹窗的预览」和「真正写库」用，保证弹窗说的节数就是写进去的节数。
 */
object LessonTimeTemplateApply {
    /**
     * 模板节次 → 落库节次：按开始时间排序后重新编号 1..n。
     *
     * 必须重排：模板 item 的 period 不是永远单调的（「撤销删除」会按原 id 插回），
     * 而 `lesson_time.period` 一旦不是 1..n，下一次在节次时间页增删改任意一节，
     * AutoSort 就会按时间重排编号，把课程引用的节次号悄悄改掉。
     * 时间解析失败的排到最后，同一开始时间用原 period 兜底，保证顺序稳定。
     *
     * 只影响写进课表的那一份，模板本身不回写。
     */
    fun normalize(items: List<LessonTimeTemplateItemEntity>): List<AppliedPeriod> = items
        .sortedWith(
            compareBy(
                { LessonTimeRules.parseMinutes(it.startTime) ?: Int.MAX_VALUE },
                { it.period },
            ),
        )
        .mapIndexed { index, item ->
            AppliedPeriod(
                period = index + 1,
                startTime = item.startTime,
                endTime = item.endTime,
            )
        }

    /** 哪些课用到了模板之外的节次。课程只读，不做任何裁剪。 */
    fun missingLessonTimes(
        periods: List<AppliedPeriod>,
        courses: List<CourseEntity>,
    ): List<CourseMissingPeriods> {
        val periodCount = periods.size
        return courses.mapNotNull { course ->
            val uncovered = course.periods.filter { it > periodCount }
            if (uncovered.isEmpty()) null else CourseMissingPeriods(course.name, uncovered)
        }
    }

    /** 弹窗内容；模板没有节次时返回 null（界面据此不弹窗、直接提示）。 */
    fun preview(
        templateName: String,
        timetableName: String,
        items: List<LessonTimeTemplateItemEntity>,
        courses: List<CourseEntity>,
    ): LessonTimeApplyPreview? {
        if (items.isEmpty()) return null
        val periods = normalize(items)
        return LessonTimeApplyPreview(
            templateName = templateName,
            timetableName = timetableName,
            periodCount = periods.size,
            missing = missingLessonTimes(periods, courses),
        )
    }
}
