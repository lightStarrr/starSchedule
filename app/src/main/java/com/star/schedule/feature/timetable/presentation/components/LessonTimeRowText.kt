package com.star.schedule.feature.timetable.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.star.schedule.R
import com.star.schedule.feature.timetable.domain.LessonTimeRules
import com.star.schedule.feature.timetable.presentation.LessonTimeRow

/** 相邻两节空档超过这个分钟数就在副标题里提示，正常的课间不提示。 */
private const val LONG_GAP_MINUTES = 15

/**
 * 节次行的副标题：第几节；和上一节之间空档超过 [LONG_GAP_MINUTES] 分钟时再补一句间隔，
 * 正常课间不写，免得每行都多一句废话。
 *
 * 节次时间页和模板编辑页共用，两处的说法保持一致。
 */
@Composable
fun lessonTimeSupportingText(
    row: LessonTimeRow,
    previous: LessonTimeRow?,
): String {
    val previousEnd = previous?.spanMinutes?.last
    val start = row.spanMinutes?.first
    val gap = if (previousEnd != null && start != null) {
        LessonTimeRules.gapMinutes(previousEnd, start)
    } else {
        null
    }
    return if (gap != null && gap > LONG_GAP_MINUTES) {
        stringResource(R.string.lesson_time_period_with_gap, row.period, gap)
    } else {
        stringResource(R.string.lesson_time_period, row.period)
    }
}
