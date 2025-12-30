package com.star.schedule.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import com.star.schedule.db.CourseEntity
import com.star.schedule.db.LessonTimeEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailBottomSheet(
    course: CourseEntity,
    lessonTimes: List<LessonTimeEntity>,
    onDismiss: () -> Unit,
    sheetState: SheetState
) {
    OptimizedBottomSheet(
        onDismiss = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // 标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.course_detail_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // 课程名称
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Class,
                        contentDescription = stringResource(R.string.label_course_name),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.label_course_name),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = course.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 上课时间区间
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = stringResource(R.string.course_detail_time_label),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.course_detail_time_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )

                        // 解析上课时间
                        val timeInfo = getCourseTimeInfo(course, lessonTimes)
                        Text(
                            text = timeInfo,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 地点
            if (course.location.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.LocationOn,
                            contentDescription = stringResource(R.string.label_location_input),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.label_location_input),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = course.location,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 老师名字
            if (course.teacher.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Person,
                            contentDescription = stringResource(R.string.course_detail_teacher_label),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.course_detail_teacher_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = course.teacher,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 课程周次信息
            if (course.weeks.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.CalendarToday,
                            contentDescription = stringResource(R.string.course_detail_weeks_label),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.course_detail_weeks_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = formatWeeks(course.weeks),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun getCourseTimeInfo(course: CourseEntity, lessonTimes: List<LessonTimeEntity>): String {
    val sortedPeriods = course.periods.sorted()
    if (sortedPeriods.isEmpty()) {
        return stringResource(R.string.course_detail_periods_not_set)
    }

    val startPeriod = sortedPeriods.first()
    val endPeriod = sortedPeriods.last()

    val startLessonTime = lessonTimes.find { it.period == startPeriod }
    val endLessonTime = lessonTimes.find { it.period == endPeriod }

    val startTime = startLessonTime?.startTime.orEmpty()
    val endTime = endLessonTime?.endTime.orEmpty()

    val dayOfWeekText = when (course.dayOfWeek) {
        1 -> stringResource(R.string.weekday_short_monday)
        2 -> stringResource(R.string.weekday_short_tuesday)
        3 -> stringResource(R.string.weekday_short_wednesday)
        4 -> stringResource(R.string.weekday_short_thursday)
        5 -> stringResource(R.string.weekday_short_friday)
        6 -> stringResource(R.string.weekday_short_saturday)
        7 -> stringResource(R.string.weekday_short_sunday)
        else -> stringResource(R.string.course_detail_day_unknown)
    }

    return if (startTime.isNotBlank() && endTime.isNotBlank()) {
        stringResource(
            R.string.course_detail_day_period_with_time,
            dayOfWeekText,
            startPeriod,
            endPeriod,
            startTime,
            endTime
        )
    } else {
        stringResource(
            R.string.course_detail_day_period,
            dayOfWeekText,
            startPeriod,
            endPeriod
        )
    }
}

@Composable
private fun formatWeeks(weeks: List<Int>): String {
    if (weeks.isEmpty()) return stringResource(R.string.course_detail_weeks_none)

    val sortedWeeks = weeks.sorted()
    val ranges = mutableListOf<String>()
    var start = sortedWeeks[0]
    var end = sortedWeeks[0]

    for (i in 1 until sortedWeeks.size) {
        if (sortedWeeks[i] == end + 1) {
            end = sortedWeeks[i]
        } else {
            ranges.add(if (start == end) start.toString() else "$start-$end")
            start = sortedWeeks[i]
            end = sortedWeeks[i]
        }
    }
    ranges.add(if (start == end) start.toString() else "$start-$end")

    return stringResource(R.string.course_detail_weeks_format, ranges.joinToString(", "))
}
