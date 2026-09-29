package com.star.schedule.feature.schedule.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.LessonTimeEntity

internal enum class TimetableEmptyState {
    NO_TIMETABLE,
    NO_LESSON_TIMES,
    NO_COURSES,
}

internal fun determineTimetableEmptyState(
    hasTimetable: Boolean,
    lessonTimes: List<LessonTimeEntity>,
    courses: List<CourseEntity>,
    currentWeek: Int,
): TimetableEmptyState? = when {
    !hasTimetable -> TimetableEmptyState.NO_TIMETABLE
    lessonTimes.isEmpty() -> TimetableEmptyState.NO_LESSON_TIMES
    courses.none { currentWeek in it.weeks } -> TimetableEmptyState.NO_COURSES
    else -> null
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun EmptyTimetableState(
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
