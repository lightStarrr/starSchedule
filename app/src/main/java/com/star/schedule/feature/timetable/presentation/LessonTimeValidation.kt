package com.star.schedule.feature.timetable.presentation

import com.star.schedule.feature.timetable.domain.LessonTimeRules

/**
 * 一节时间的校验：节次时间页和模板编辑页共用同一套规则，避免两边说法不一致。
 *
 * [others] 是同一组里其它节的分钟区间（不含正在编辑的这一节）。
 * 返回 null 表示可以保存。
 */
internal fun validateLessonTimeRange(
    startText: String,
    endText: String,
    others: List<IntRange>,
): LessonTimeError? {
    val start = LessonTimeRules.parseMinutes(startText)
    val end = LessonTimeRules.parseMinutes(endText)
    return when {
        start == null || end == null -> LessonTimeError.InvalidTime
        end <= start -> LessonTimeError.EndNotAfterStart
        LessonTimeRules.overlaps(start, end, others) -> LessonTimeError.Overlap
        else -> null
    }
}
