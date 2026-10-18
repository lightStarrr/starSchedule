package com.star.schedule.feature.settings.domain

import androidx.compose.runtime.Immutable

/**
 * 课表设置页顶部预览用的数据：一张课表 + 它的课程块。
 *
 * 预览是缩略图，不需要教师、地点这类细节，只要知道「周几、第几节开始、连上几节」。
 */
@Immutable
data class TimetablePreview(
    val id: Long,
    val name: String,
    val hasWeekendCourses: Boolean,
    val rowHeight: Int,
    val startDate: String?,
    val blocks: List<TimetablePreviewBlock>
)

@Immutable
data class TimetablePreviewBlock(
    val dayOfWeek: Int,
    val startPeriod: Int,
    val periodCount: Int
)
