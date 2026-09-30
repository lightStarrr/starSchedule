package com.star.schedule.feature.schedule.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.star.schedule.R
import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.LessonTimeEntity
import com.star.schedule.feature.schedule.domain.CourseBlock
import com.star.schedule.feature.schedule.domain.buildCourseBlocksByWeek
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun TimetableGrid(
    lessonTimes: List<LessonTimeEntity>,
    courses: List<CourseEntity>,
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
    val courseBlocksByWeek = remember(courses) { buildCourseBlocksByWeek(courses) }
    val courseBlocks = courseBlocksByWeek[currentWeek].orEmpty()
    val courseBlocksByDay = remember(courseBlocks) {
        courseBlocks.groupBy { it.dayOfWeek }
    }
    val minimumTimeCellHeight = with(LocalDensity.current) {
        // Three text lines plus the cell's vertical padding must fit even when
        // the user selects a row height smaller than the time column needs.
        (20.sp.toPx() + 16.sp.toPx() * 2 + 16.dp.toPx()).toDp()
    }.coerceAtLeast(72.dp)
    val effectiveRowHeight = maxOf(rowHeight, minimumTimeCellHeight)
    val timeColumnWidth = 58.dp

    Box(
        modifier = modifier,
    ) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Column(
                    modifier = Modifier.width(timeColumnWidth),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    sortedLessonTimes.forEach { lessonTime ->
                        GridTimeCell(
                            lessonTime = lessonTime,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(effectiveRowHeight),
                        )
                    }
                }
                visibleDays.forEach { day ->
                    CourseDayColumn(
                        lessonTimes = sortedLessonTimes,
                        courseBlocks = courseBlocksByDay[day].orEmpty(),
                        currentWeek = currentWeek,
                        rowHeight = effectiveRowHeight,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CourseDayColumn(
    lessonTimes: List<LessonTimeEntity>,
    courseBlocks: List<CourseBlock>,
    currentWeek: Int,
    rowHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val rowGap = 2.dp
    val totalHeight = rowHeight * lessonTimes.size + rowGap * (lessonTimes.size - 1).coerceAtLeast(0)
    val periodIndexes = remember(lessonTimes) {
        lessonTimes.mapIndexed { index, lessonTime -> lessonTime.period to index }.toMap()
    }

    Box(modifier = modifier.height(totalHeight)) {
        Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
            lessonTimes.forEach {
                Box(modifier = Modifier.fillMaxWidth().height(rowHeight))
            }
        }
        courseBlocks.forEachIndexed { index, block ->
            val startIndex = periodIndexes[block.startPeriod]
            val endIndex = periodIndexes[block.endPeriod]
            if (startIndex != null && endIndex != null && endIndex >= startIndex) {
                val span = endIndex - startIndex + 1
                key(currentWeek, block.course.id, block.startPeriod, block.endPeriod) {
                    GridCourseCell(
                        course = block.course,
                        animationDelayMillis = (index * 35L).coerceAtMost(210L),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight * span + rowGap * (span - 1))
                            .offset(y = (rowHeight + rowGap) * startIndex),
                    )
                }
            }
        }
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
    animationDelayMillis: Long,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(20f) }

    LaunchedEffect(Unit) {
        delay(animationDelayMillis)
        coroutineScope {
            launch {
                alpha.animateTo(1f, animationSpec = tween(durationMillis = 320))
            }
            launch {
                offsetY.animateTo(0f, animationSpec = tween(durationMillis = 320))
            }
        }
    }

    Box(
        modifier = modifier
            .padding(2.dp),
    ) {
        if (course != null) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.alpha = alpha.value
                        translationY = with(density) { offsetY.value.dp.toPx() }
                    },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
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
