package com.star.schedule.ui.layouts

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.star.schedule.db.CourseEntity
import com.star.schedule.db.LessonTimeEntity
import com.star.schedule.db.ScheduleDao
import com.star.schedule.db.TimetableEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class LessonTime(
    val period: Int,
    val startTime: String,
    val endTime: String
)

data class Course(
    val name: String,
    val location: String,
    val dayOfWeek: Int,
    val periods: List<Int>,
    val weeks: List<Int>
)

data class CourseBlock(
    val course: Course,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int
)


@Suppress("UNUSED_PARAMETER")
@Composable
fun DateRange(context: Activity, dao: ScheduleDao) {
    // 当前课表ID
    val currentTimetableIdPref by dao.getPreferenceFlow("current_timetable")
        .collectAsState(initial = null)
    val timetableId = currentTimetableIdPref?.toLongOrNull()

    // 获取当前课表实体以读取 showWeekend 和 startDate
    val timetable by if (timetableId != null) {
        dao.getTimetableFlow(timetableId).collectAsState(initial = null)
    } else remember { mutableStateOf(null as TimetableEntity?) }

    // 设置：是否显示非本周课程
    val showNonCurrentPref by dao.getPreferenceFlow("show_non_current_week")
        .collectAsState(initial = "false")
    val showNonCurrent = showNonCurrentPref == "true"

    // 当前周的课程或全部课程（根据开关）
    val today = LocalDate.now()
    val courses by if (timetableId != null) {
        val flow = if (showNonCurrent) dao.getCoursesFlow(timetableId)
        else dao.getCoursesForDateFlow(timetableId, today)
        flow.collectAsState(initial = emptyList())
    } else emptyList<CourseEntity>().let { mutableStateOf(it) }

    // 当前课表的作息时间
    val lessonTimes by if (timetableId != null) {
        dao.getLessonTimesFlow(timetableId).collectAsState(initial = emptyList())
    } else emptyList<LessonTimeEntity>().let { mutableStateOf(it) }

    // 计算当前是第几周（基于 timetable.startDate），若失败则为 null
    val currentWeekNumber: Int? = try {
        val startStr = timetable?.startDate
        if (startStr.isNullOrBlank()) null
        else {
            val start = LocalDate.parse(startStr)
            val days = ChronoUnit.DAYS.between(start, today).toInt()
            val week = (days / 7) + 1
            if (week < 1) 1 else week
        }
    } catch (_: Exception) {
        null
    }

    // 基于设置与当前周，显示当前周和未来周
    val visibleEntities: List<CourseEntity> = if (currentWeekNumber != null) {
        if (showNonCurrent) {
            val currentCourses = courses.filter { it.weeks.contains(currentWeekNumber) }
            val occupied = currentCourses.flatMap { ce -> ce.periods.map { p -> ce.dayOfWeek to p } }.toSet()
            val futureCourses = courses.filter { entity ->
                entity.weeks.any { it > currentWeekNumber } && !entity.weeks.contains(currentWeekNumber)
            }.filter { entity ->
                entity.periods.none { p -> (entity.dayOfWeek to p) in occupied }
            }
            currentCourses + futureCourses
        } else {
            courses.filter { entity -> entity.weeks.contains(currentWeekNumber) }
        }
    } else courses

    Box(modifier = Modifier.fillMaxSize()) {
        ScheduleScreen(
            courses = visibleEntities.map { entity ->
                Course(
                    name = entity.name,
                    location = entity.location,
                    dayOfWeek = entity.dayOfWeek,
                    periods = entity.periods,
                    weeks = entity.weeks
                )
            },
            lessonTimes = lessonTimes.map { entity ->
                LessonTime(
                    period = entity.period,
                    startTime = entity.startTime,
                    endTime = entity.endTime
                )
            },
            showWeekend = timetable?.showWeekend ?: true,
            currentWeek = currentWeekNumber,
            showNonCurrentWeekCourses = showNonCurrent
        )
    }
}


fun buildCourseBlocks(courses: List<Course>): List<CourseBlock> {
    val blocks = mutableListOf<CourseBlock>()
    for (course in courses) {
        val sorted = course.periods.sorted()
        var start = sorted.first()
        var prev = start
        for (i in 1 until sorted.size) {
            if (sorted[i] != prev + 1) {
                // 出现断开，生成一个区块
                blocks.add(CourseBlock(course, course.dayOfWeek, start, prev))
                start = sorted[i]
            }
            prev = sorted[i]
        }
        // 最后一个区块
        blocks.add(CourseBlock(course, course.dayOfWeek, start, prev))
    }
    return blocks
}


@Composable
fun ScheduleScreen(
    courses: List<Course>,
    lessonTimes: List<LessonTime>,
    cellHeight: Dp = 60.dp,
    cellPadding: Dp = 2.dp,
    showWeekend: Boolean = true,
    currentWeek: Int? = null,
    showNonCurrentWeekCourses: Boolean = false
) {
    val allDayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    // 可见的星期数字（1=周一 ...）
    val visibleDays = if (showWeekend) (1..7).toList() else (1..5).toList()
    val visibleDayLabels = visibleDays.map { allDayLabels[it - 1] }

    val courseBlocks = buildCourseBlocks(courses)
    val scrollState = rememberScrollState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalWidth = maxWidth
        val leftColumnWidth = 35.dp
        val dayColumnWidth = (totalWidth - leftColumnWidth - 5.dp) / visibleDayLabels.size

        // 使用 remember + mutableStateOf 保存标题高度
        val density = LocalDensity.current
        var headerHeightDp by remember { mutableStateOf(0.dp) }

        // 背景网格和标题
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState) // 整个Box可滚动
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 星期标题行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            headerHeightDp = with(density) { coords.size.height.toDp() }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(leftColumnWidth)
                            .padding(cellPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentWeek != null) {
                            Text(
                                text = "第${currentWeek}周",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                                // 让文本在 Box 内水平居中
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    visibleDayLabels.forEach { day ->
                        Box(
                            modifier = Modifier
                                .width(dayColumnWidth)
                                .padding(cellPadding)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("周$day", textAlign = TextAlign.Center)
                        }
                    }
                }

                // 每节课行
                lessonTimes.forEach { lesson ->
                    Row(
                        modifier = Modifier
                            .height(cellHeight)
                            .fillMaxWidth()
                    ) {
                        // 左侧节次列
                        Column(
                            modifier = Modifier
                                .width(leftColumnWidth)
                                .fillMaxHeight(),  // 让 Column 高度和 Row 一致
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center

                        ) {
                            Text(
                                "${lesson.period}",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "${lesson.startTime}\n${lesson.endTime}",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        // 可见列课程网格占位
                        visibleDays.forEach { day ->
                            val isOccupied = courseBlocks.any { block ->
                                block.dayOfWeek == day &&
                                        block.startPeriod <= lesson.period &&
                                        block.endPeriod >= lesson.period
                            }

                            Box(
                                modifier = Modifier
                                    .width(dayColumnWidth)
                                    .height(cellHeight)
                                    .padding(cellPadding)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isOccupied) Color.Transparent
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                            )
                        }
                    }
                }
            }

            // 课程块 Overlay，覆盖在网格上
            courseBlocks.forEach { block ->
                // 如果该课程的星期不在可见范围内（例如隐藏周末），则跳过
                val dayIndex = visibleDays.indexOf(block.dayOfWeek)
                if (dayIndex == -1) return@forEach

                val span = block.endPeriod - block.startPeriod + 1
                Box(
                    modifier = Modifier
                        .absoluteOffset(
                            x = leftColumnWidth + dayColumnWidth * dayIndex + cellPadding,
                            y = headerHeightDp + cellHeight * (block.startPeriod - 1) + cellPadding
                        )
                        .width(dayColumnWidth - cellPadding * 2)
                        .height(cellHeight * span - cellPadding * 2)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (currentWeek != null && showNonCurrentWeekCourses) {
                                val base = MaterialTheme.colorScheme.secondary
                                if (block.course.weeks.contains(currentWeek)) base
                                else base.copy(alpha = 0.35f) // 未来周课程统一使用较低透明度
                            } else {
                                MaterialTheme.colorScheme.secondary
                            }
                        )
                        .padding(4.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = block.course.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                        Text(
                            text = block.course.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }
        }
    }
}
