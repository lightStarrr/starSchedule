package com.star.schedule.feature.schedule.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.LessonTimeEntity
import java.time.LocalDate

@Composable
fun TimetableGrid(
    lessonTimes: List<LessonTimeEntity>,
    courses: List<CourseEntity>,
    hasTimetable: Boolean,
    currentWeek: Int,
    weekStartDate: LocalDate,
    showWeekend: Boolean,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val visibleDays = remember(showWeekend) {
        if (showWeekend) (1..7).toList() else (1..5).toList()
    }
    val dayLabels = listOf(
        stringResource(R.string.weekday_short_monday),
        stringResource(R.string.weekday_short_tuesday),
        stringResource(R.string.weekday_short_wednesday),
        stringResource(R.string.weekday_short_thursday),
        stringResource(R.string.weekday_short_friday),
        stringResource(R.string.weekday_short_saturday),
        stringResource(R.string.weekday_short_sunday),
    )
    val verticalScrollState = rememberScrollState()
    val sortedLessonTimes = remember(lessonTimes) { lessonTimes.sortedBy { it.period } }
    val visibleCourses = remember(courses, currentWeek) {
        courses.filter { currentWeek in it.weeks }
    }
    val coursesByCell = remember(visibleCourses) {
        visibleCourses
            .flatMap { course ->
                course.periods.map { period -> (course.dayOfWeek to period) to course }
            }
            .toMap()
    }
    val timeColumnWidth = 58.dp

    Box(
        modifier = modifier,
    ) {
        val emptyState = when {
            !hasTimetable -> TimetableEmptyState.NO_TIMETABLE
            sortedLessonTimes.isEmpty() -> TimetableEmptyState.NO_LESSON_TIMES
            visibleCourses.isEmpty() -> TimetableEmptyState.NO_COURSES
            else -> null
        }
        if (emptyState != null) {
            EmptyTimetableState(
                state = emptyState,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScrollState),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    GridTimeHeader(modifier = Modifier.width(timeColumnWidth))
                    visibleDays.forEach { day ->
                        val date = weekStartDate.plusDays((day - 1).toLong())
                        GridDayHeader(
                            dayLabel = dayLabels[day - 1],
                            date = date,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                sortedLessonTimes.forEach { lessonTime ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = rowHeight)
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        GridTimeCell(
                            lessonTime = lessonTime,
                            modifier = Modifier
                                .width(timeColumnWidth)
                                .fillMaxHeight(),
                        )
                        visibleDays.forEach { day ->
                            GridCourseCell(
                                course = coursesByCell[day to lessonTime.period],
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

private enum class TimetableEmptyState {
    NO_TIMETABLE,
    NO_LESSON_TIMES,
    NO_COURSES,
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun EmptyTimetableState(
    state: TimetableEmptyState,
    modifier: Modifier = Modifier,
) {
    val shape = when (state) {
        TimetableEmptyState.NO_TIMETABLE -> MaterialShapes.Cookie4Sided.toShape()
        TimetableEmptyState.NO_LESSON_TIMES -> MaterialShapes.Cookie4Sided.toShape()
        TimetableEmptyState.NO_COURSES -> MaterialShapes.Cookie7Sided.toShape()
    }
    val icon = when (state) {
        TimetableEmptyState.NO_TIMETABLE -> Icons.Rounded.CalendarMonth
        TimetableEmptyState.NO_LESSON_TIMES -> Icons.Rounded.AccessTime
        TimetableEmptyState.NO_COURSES -> Icons.Rounded.EventBusy
    }
    val title = when (state) {
        TimetableEmptyState.NO_TIMETABLE -> R.string.timetable_empty_title
        TimetableEmptyState.NO_LESSON_TIMES -> R.string.label_no_lesson_time
        TimetableEmptyState.NO_COURSES -> R.string.label_no_course
    }

    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(132.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.width(64.dp).height(64.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun GridTimeHeader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {}
}

@Composable
private fun GridDayHeader(
    dayLabel: String,
    date: LocalDate,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.weekday_column_label, dayLabel),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = "${date.monthValue}/${date.dayOfMonth}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun GridTimeCell(
    lessonTime: LessonTimeEntity,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = lessonTime.period.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = lessonTime.startTime,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        Text(
            text = lessonTime.endTime,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun GridCourseCell(
    course: CourseEntity?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(2.dp),
    ) {
        if (course != null) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = course.name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (course.location.isNotBlank()) {
                        Text(
                            text = course.location,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
