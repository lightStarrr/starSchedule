package com.star.schedule.ui.layouts

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarState
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
fun DateRange(context: Activity, dao: ScheduleDao, currentWeekNumber: Int, onCurrentWeekNumberChange : (Int) -> Unit, onWeeksCalculated: (List<Int>) -> Unit = {}, upDateRealCurrentWeek: (Int) -> Unit) {
    // 当前课表ID
    val currentTimetableIdPref by dao.getPreferenceFlow("current_timetable")
        .collectAsState(initial = null)
    val timetableId = currentTimetableIdPref?.toLongOrNull()

    // 获取当前课表实体以读取 showWeekend 和 startDate
    val timetable by if (timetableId != null) {
        dao.getTimetableFlow(timetableId).collectAsState(initial = null)
    } else remember { mutableStateOf(null as TimetableEntity?) }

    // 设置：是否显示非本周课程
    val perTableKey = timetableId?.let { "timetable_${it}_show_non_current_week" }
    val showNonCurrentPerTable by if (perTableKey != null) {
        dao.getPreferenceFlow(perTableKey).collectAsState(initial = null)
    } else remember { mutableStateOf(null as String?) }
    val showNonCurrentGlobal by dao.getPreferenceFlow("show_non_current_week")
        .collectAsState(initial = null)
    val showNonCurrent = when (showNonCurrentPerTable) {
        "true" -> true
        "false" -> false
        else -> showNonCurrentGlobal == "true"
    }

    // 当前周的课程或全部课程（根据开关）
    val today = LocalDate.now()
    val courses by if (timetableId != null) {
        // 总是加载所有课程，后续根据showNonCurrent设置来决定显示哪些课程
        dao.getCoursesFlow(timetableId).collectAsState(initial = emptyList())
    } else emptyList<CourseEntity>().let { mutableStateOf(it) }

    // 当前课表的作息时间
    val lessonTimes by if (timetableId != null) {
        dao.getLessonTimesFlow(timetableId).collectAsState(initial = emptyList())
    } else emptyList<LessonTimeEntity>().let { mutableStateOf(it) }

    // 计算有课的周数
    val weeksWithCourses = courses.flatMap { it.weeks }.distinct().sorted()
    
    // 当有课周数变化时，通知MainActivity
    LaunchedEffect(weeksWithCourses) {
        Log.d("DateRange", "weeksWithCourses: $weeksWithCourses")
        if (weeksWithCourses.isNotEmpty()) {
            onWeeksCalculated(weeksWithCourses)
        }
    }

    // 计算当前是第几周（基于 timetable.startDate），若失败则为 null
    val calculatedWeekNumber: Int? = try {
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

    // 当组件首次加载时，如果计算出的周数不为null且与传入的不同，则更新传入的周数
    LaunchedEffect(calculatedWeekNumber) {
        if (calculatedWeekNumber != null && calculatedWeekNumber != currentWeekNumber) {
            onCurrentWeekNumberChange(calculatedWeekNumber)
        }
        // 无论是否更新了currentWeekNumber，都要更新realCurrentWeek为计算出的实际周数
        if (calculatedWeekNumber != null) {
            upDateRealCurrentWeek(calculatedWeekNumber)
        }
    }

    val visibleEntities: List<CourseEntity> = if (currentWeekNumber > 0) {
        if (showNonCurrent) {
            val currentCourses = courses.filter { it.weeks.contains(currentWeekNumber) }

            // 当前周已占用的节次（dayOfWeek + period）
            val occupied = currentCourses
                .flatMap { ce -> ce.periods.map { p -> ce.dayOfWeek to p } }
                .toMutableSet()

            val futureCourses = mutableListOf<CourseEntity>()

            courses
                .filter { entity ->
                    entity.weeks.any { it > currentWeekNumber } && !entity.weeks.contains(currentWeekNumber)
                }
                .forEach { entity ->
                    val occupiedNow = entity.periods.any { p -> (entity.dayOfWeek to p) in occupied }
                    if (!occupiedNow) {
                        // 不冲突，则加入显示列表
                        futureCourses.add(entity)
                        // 标记这些节次为已占用，防止后续未来课叠加
                        occupied.addAll(entity.periods.map { p -> entity.dayOfWeek to p })
                    }
                }

            currentCourses + futureCourses
        } else {
            // 只显示当前周的课程
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
            currentWeek = currentWeekNumber
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


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScheduleScreen(
    courses: List<Course>,
    lessonTimes: List<LessonTime>,
    cellHeight: Dp = 60.dp,
    cellPadding: Dp = 2.dp,
    showWeekend: Boolean = true,
    currentWeek: Int? = null
) {
    val haptic = LocalHapticFeedback.current
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
                .verticalScroll(scrollState)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(bottom = 32.dp)) {
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
                                text = currentWeek.toString(),
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

            val todayDayOfWeek = LocalDate.now().dayOfWeek.value

            courseBlocks.forEach { block ->
                val dayIndex = visibleDays.indexOf(block.dayOfWeek)
                if (dayIndex == -1) return@forEach

                val span = block.endPeriod - block.startPeriod + 1
                Card(
                    modifier = Modifier
                        .absoluteOffset(
                            x = leftColumnWidth + dayColumnWidth * dayIndex + cellPadding,
                            y = headerHeightDp + cellHeight * (block.startPeriod - 1) + cellPadding
                        )
                        .width(dayColumnWidth - cellPadding * 2)
                        .height(cellHeight * span - cellPadding * 2)
                        .alpha(if (block.course.weeks.contains(currentWeek)) 1f else 0.5f),
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if ((block.dayOfWeek == todayDayOfWeek)&&(block.course.weeks.contains(currentWeek))) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        contentColor = if ((block.dayOfWeek == todayDayOfWeek)&&(block.course.weeks.contains(currentWeek))) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary
                    ),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        val name = if (block.course.weeks.contains(currentWeek)) block.course.name else "[非本周]" + block.course.name
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Start
                        )
                        Text(
                            text = block.course.location,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }
        }
    }
}