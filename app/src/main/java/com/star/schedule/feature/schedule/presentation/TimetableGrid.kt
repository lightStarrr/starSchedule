package com.star.schedule.feature.schedule.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
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
    bottomContentPadding: Dp = 8.dp,
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
    // 时间栏宽度按实际时间文本量出来：写死 40dp 时「08:00」比栏还宽，
    // 居中后左右各溢出一截，屏幕边缘那侧会被裁掉。
    val timeTextMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val timeTextStyle = MaterialTheme.typography.labelSmall
    val timeColumnWidth = remember(sortedLessonTimes, density, timeTextStyle) {
        val widest = sortedLessonTimes.maxOfOrNull { lessonTime ->
            maxOf(
                timeTextMeasurer.measure(AnnotatedString(lessonTime.startTime), timeTextStyle)
                    .size.width,
                timeTextMeasurer.measure(AnnotatedString(lessonTime.endTime), timeTextStyle)
                    .size.width,
            )
        } ?: 0
        with(density) { widest.toDp() + GridTimeColumnSidePadding * 2 }.coerceAtLeast(40.dp)
    }

    BoxWithConstraints(
        modifier = modifier,
    ) {
        // 一列的可用宽度决定字号：7 列挤在 360dp 的机器上时，「周一」和「10/12」都会被截断，
        // 课程名甚至只能一行一个字。这里按需把整套字号降一档，直到表头和两个字都放得下。
        val columnWidth = (
            (maxWidth - timeColumnWidth - GridColumnGap * visibleDays.size) / visibleDays.size
            ).coerceAtLeast(24.dp)
        val fontScale = rememberGridFontScale(
            columnWidth = columnWidth,
            dayLabelTexts = visibleDays.map { day ->
                stringResource(R.string.weekday_column_label, dayLabels[day - 1])
            },
            dateTexts = visibleDays.map { day ->
                val date = weekStartDate.plusDays((day - 1).toLong())
                "${date.monthValue}/${date.dayOfMonth}"
            },
        )
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
                horizontalArrangement = Arrangement.spacedBy(GridColumnGap),
            ) {
                GridTimeHeader(modifier = Modifier.width(timeColumnWidth))
                visibleDays.forEach { day ->
                    val date = weekStartDate.plusDays((day - 1).toLong())
                    GridDayHeader(
                        dayLabel = dayLabels[day - 1],
                        date = date,
                        fontScale = fontScale,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GridColumnGap),
            ) {
                Column(
                    modifier = Modifier.width(timeColumnWidth),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    sortedLessonTimes.forEach { lessonTime ->
                        GridTimeCell(
                            lessonTime = lessonTime,
                            fontScale = fontScale,
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
                        fontScale = fontScale,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // Keep the clearance inside the scroll content so courses can pass behind
            // the floating controls and still scroll fully above them at the end.
            Spacer(modifier = Modifier.height(bottomContentPadding))
        }
    }
}

@Composable
private fun CourseDayColumn(
    lessonTimes: List<LessonTimeEntity>,
    courseBlocks: List<CourseBlock>,
    currentWeek: Int,
    rowHeight: Dp,
    fontScale: Float,
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
                val blockHeight = rowHeight * span + rowGap * (span - 1)
                key(currentWeek, block.course.id, block.startPeriod, block.endPeriod) {
                    GridCourseCell(
                        course = block.course,
                        animationDelayMillis = (index * 35L).coerceAtMost(210L),
                        cellHeight = blockHeight,
                        fontScale = fontScale,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(blockHeight)
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
    fontScale: Float,
    modifier: Modifier = Modifier,
) {
    val labelStyle = MaterialTheme.typography.titleSmall.scaledFont(fontScale)
    val dateStyle = MaterialTheme.typography.labelSmall.scaledFont(fontScale)
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(
                start = GridHeaderHorizontalPadding,
                end = GridHeaderHorizontalPadding,
                bottom = 8.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = stringResource(R.string.weekday_column_label, dayLabel),
            style = labelStyle,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = "${date.monthValue}/${date.dayOfMonth}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = dateStyle,
            maxLines = 1,
        )
    }
}

@Composable
private fun GridTimeCell(
    lessonTime: LessonTimeEntity,
    fontScale: Float,
    modifier: Modifier = Modifier,
) {
    val periodStyle = MaterialTheme.typography.titleSmall.scaledFont(fontScale)
    val timeStyle = MaterialTheme.typography.labelSmall.scaledFont(fontScale)
    Column(
        // 不带左右内边距：和右侧课程列的分隔交给格子之间那 2dp 的缝。
        modifier = modifier
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = lessonTime.period.toString(),
            style = periodStyle,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = lessonTime.startTime,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = timeStyle,
            maxLines = 1,
        )
        Text(
            text = lessonTime.endTime,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = timeStyle,
            maxLines = 1,
        )
    }
}

@Composable
private fun GridCourseCell(
    course: CourseEntity?,
    animationDelayMillis: Long,
    cellHeight: Dp,
    fontScale: Float,
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

    val titleStyle = MaterialTheme.typography.labelLarge.scaledFont(fontScale)
    val locationStyle = MaterialTheme.typography.labelSmall.scaledFont(fontScale)
    val titleLineHeight = with(density) { titleStyle.lineHeight.toDp() }
    val locationLineHeight = with(density) { locationStyle.lineHeight.toDp() }
    val contentHeight = (cellHeight - 16.dp).coerceAtLeast(0.dp)
    val lineLimits = remember(contentHeight, course?.location.isNullOrBlank()) {
        calculateCourseTextLineLimits(
            contentHeight = contentHeight,
            titleLineHeight = titleLineHeight,
            locationLineHeight = locationLineHeight,
            hasLocation = !course?.location.isNullOrBlank(),
        )
    }

    Box(
        modifier = modifier
            .padding(GridCourseCellOuterPadding),
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
                        .padding(
                            horizontal = courseContentPadding(fontScale),
                            vertical = 6.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = course.name,
                        style = titleStyle,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = lineLimits.title,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (course.location.isNotBlank()) {
                        Text(
                            text = course.location,
                            style = locationStyle,
                            maxLines = lineLimits.location,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private val GridColumnGap = 2.dp

/** 时间栏里文字与两侧的留白。 */
private val GridTimeColumnSidePadding = 3.dp

/** 表头文字到列边缘的留白（GridDayHeader 的左右 padding）。 */
private val GridHeaderHorizontalPadding = 8.dp

/** 课程卡片外层的贴边留白与卡片内的左右 padding（后者随字号一起收）。 */
private val GridCourseCellOuterPadding = 2.dp
private val GridCourseCellContentPadding = 8.dp
private val GridCourseCellMinContentPadding = 4.dp

private fun courseContentPadding(fontScale: Float): Dp =
    (GridCourseCellContentPadding * fontScale).coerceAtLeast(GridCourseCellMinContentPadding)

private const val MaxFontScale = 1f
private const val MinFontScale = 0.6f
private const val FontScaleStep = 0.05f

/** 判定「一行放得下两个汉字」用的样本。 */
private const val CourseTitleMinSample = "课程"

/** 测量结果和真实排版之间留一点余量，避免刚好卡在临界值时仍然断行。 */
private val FontFitSafetyMargin = 1.dp

/**
 * 按列宽挑一档字号：表头（周一 / 10/12）要完整显示，课程名至少能一行两个汉字。
 * 逐档往下试，都不满足时取最小值，保证内容不溢出。
 */
@Composable
private fun rememberGridFontScale(
    columnWidth: Dp,
    dayLabelTexts: List<String>,
    dateTexts: List<String>,
): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = MaterialTheme.typography.titleSmall
    val dateStyle = MaterialTheme.typography.labelSmall
    val titleStyle = MaterialTheme.typography.labelLarge

    return remember(columnWidth, dayLabelTexts, dateTexts, labelStyle, dateStyle, titleStyle) {
        val safetyMargin = with(density) { FontFitSafetyMargin.toPx() }
        val headerAvailable = with(density) {
            (columnWidth - GridHeaderHorizontalPadding * 2).toPx()
        } - safetyMargin

        val candidates = generateSequence(MaxFontScale) { previous ->
            (previous - FontScaleStep).takeIf { it >= MinFontScale - 1e-4f }
        }
        candidates.firstOrNull { scale ->
            val courseAvailable = with(density) {
                (
                    columnWidth -
                        GridCourseCellOuterPadding * 2 -
                        courseContentPadding(scale) * 2
                    ).toPx()
            } - safetyMargin
            maxLineWidth(dayLabelTexts, labelStyle.scaledFont(scale), measurer) <= headerAvailable &&
                maxLineWidth(dateTexts, dateStyle.scaledFont(scale), measurer) <= headerAvailable &&
                maxLineWidth(
                    listOf(CourseTitleMinSample),
                    titleStyle.scaledFont(scale),
                    measurer,
                ) <= courseAvailable
        } ?: MinFontScale
    }
}

private fun maxLineWidth(
    texts: List<String>,
    style: TextStyle,
    measurer: TextMeasurer,
): Float = texts.maxOfOrNull { text ->
    measurer.measure(
        text = AnnotatedString(text),
        style = style,
        maxLines = 1,
        softWrap = false,
    ).size.width
}?.toFloat() ?: 0f

private fun TextStyle.scaledFont(scale: Float): TextStyle {
    if (scale == MaxFontScale) return this
    return copy(
        fontSize = if (fontSize.isSpecified) fontSize * scale else fontSize,
        lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight,
    )
}

private data class CourseTextLineLimits(
    val title: Int,
    val location: Int,
)

private fun calculateCourseTextLineLimits(
    contentHeight: Dp,
    titleLineHeight: Dp,
    locationLineHeight: Dp,
    hasLocation: Boolean,
): CourseTextLineLimits {
    if (!hasLocation) {
        return CourseTextLineLimits(
            title = (contentHeight.value / titleLineHeight.value).toInt().coerceAtLeast(1),
            location = 0,
        )
    }

    var titleLines = (contentHeight.value * 0.6f / titleLineHeight.value)
        .toInt()
        .coerceAtLeast(1)
    var locationLines = (contentHeight.value * 0.4f / locationLineHeight.value)
        .toInt()
        .coerceAtLeast(1)

    fun usedHeight(): Float =
        titleLines * titleLineHeight.value +
            locationLines * locationLineHeight.value +
            2.dp.value

    while (usedHeight() > contentHeight.value && (titleLines > 1 || locationLines > 1)) {
        if (titleLines > 1 && (titleLines * titleLineHeight.value) >=
            (locationLines * locationLineHeight.value)
        ) {
            titleLines--
        } else if (locationLines > 1) {
            locationLines--
        } else {
            break
        }
    }

    return CourseTextLineLimits(title = titleLines, location = locationLines)
}
