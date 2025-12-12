package com.star.schedule.ui.layouts

import android.app.Activity
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.star.schedule.Constants
import com.star.schedule.db.CourseEntity
import com.star.schedule.db.DayNoteEntity
import com.star.schedule.db.LessonTimeEntity
import com.star.schedule.db.ScheduleDao
import com.star.schedule.db.TimetableEntity
import com.star.schedule.ui.components.CourseDetailBottomSheet
import com.star.schedule.ui.components.OptimizedBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRange(
    context: Activity,
    dao: ScheduleDao,
    currentWeekNumber: Int,
    onCurrentWeekNumberChange: (Int) -> Unit,
    onWeeksCalculated: (List<Int>) -> Unit = {},
    upDateRealCurrentWeek: (Int) -> Unit
) {
    // 当前课表ID
    val currentTimetableIdPref by dao.getPreferenceFlow(Constants.PREF_CURRENT_TIMETABLE)
        .collectAsState(initial = null)
    val timetableId = currentTimetableIdPref?.toLongOrNull()

    // 获取当前课表实体以读取 showWeekend 和 startDate
    val timetable by if (timetableId != null) {
        dao.getTimetableFlow(timetableId).collectAsState(initial = null)
    } else remember { mutableStateOf(null as TimetableEntity?) }

    // 当前周的课程或全部课程（根据开关）
    val today = LocalDate.now()
    val courses by if (timetableId != null) {
        // 总是加载所有课程，后续根据showNonCurrent设置来决定显示哪些课程
        dao.getCoursesFlow(timetableId).collectAsState(initial = emptyList())
    } else remember { mutableStateOf(emptyList<CourseEntity>()) }

    // 当前课表的作息时间
    val lessonTimes by if (timetableId != null) {
        dao.getLessonTimesFlow(timetableId).collectAsState(initial = emptyList())
    } else remember { mutableStateOf(emptyList<LessonTimeEntity>()) }

    // 当天便签
    val dayNotes by if (timetableId != null) {
        dao.getDayNotesFlow(timetableId).collectAsState(initial = emptyList())
    } else remember { mutableStateOf(emptyList<DayNoteEntity>()) }

    val noteSheetState = rememberModalBottomSheetState()
    val noteScope = rememberCoroutineScope()
    var selectedDateForNote by remember { mutableStateOf<LocalDate?>(null) }
    var noteDraft by remember { mutableStateOf("") }
    val notesByDate = remember(dayNotes) { dayNotes.associateBy { it.date } }

    LaunchedEffect(timetableId) {
        selectedDateForNote = null
        noteDraft = ""
    }

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

    LaunchedEffect(calculatedWeekNumber) {
        if (calculatedWeekNumber != null && calculatedWeekNumber != currentWeekNumber) {
            onCurrentWeekNumberChange(calculatedWeekNumber)
        }
        if (calculatedWeekNumber != null) {
            upDateRealCurrentWeek(calculatedWeekNumber)
        }
    }

    val visibleEntities: List<CourseEntity> = if (currentWeekNumber > 0) {
        if (timetable?.showFuture ?: false) {
            val currentCourses = courses.filter { it.weeks.contains(currentWeekNumber) }

            val occupied = currentCourses
                .flatMap { ce -> ce.periods.map { p -> ce.dayOfWeek to p } }
                .toMutableSet()

            val futureCourses = mutableListOf<CourseEntity>()

            val futureWeeks = courses.flatMap { it.weeks }
                .filter { it > currentWeekNumber }
                .distinct()
                .sorted()

            for (week in futureWeeks) {
                courses
                    .filter { entity ->
                        entity.weeks.contains(week) && !entity.weeks.contains(currentWeekNumber)
                    }
                    .forEach { entity ->
                        val occupiedNow =
                            entity.periods.any { p -> (entity.dayOfWeek to p) in occupied }
                        if (!occupiedNow) {
                            futureCourses.add(entity)
                            occupied.addAll(entity.periods.map { p -> entity.dayOfWeek to p })
                        }
                    }
            }

            currentCourses + futureCourses
        } else {
            courses.filter { it.weeks.contains(currentWeekNumber) }
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
            cellHeight = timetable?.rowHeight?.dp ?: 60.dp,
            showWeekend = timetable?.showWeekend ?: true,
            currentWeek = currentWeekNumber,
            realCurrentWeek = calculatedWeekNumber,
            courseEntities = visibleEntities,
            lessonTimeEntities = lessonTimes,
            dayNotes = dayNotes,
            onDayNoteClick = { date ->
                if (timetableId != null) {
                    selectedDateForNote = date
                    noteDraft = notesByDate[date.toString()]?.content ?: ""
                }
            }
        )
    }

    if (selectedDateForNote != null && timetableId != null) {
        val targetDate = selectedDateForNote!!
        OptimizedBottomSheet(
            onDismiss = {
                selectedDateForNote = null
                noteDraft = ""
            },
            sheetState = noteSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "便签 · ${targetDate.monthValue}/${targetDate.dayOfMonth}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "将备注钉在当天课表上，方便回看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text("便签内容") },
                    placeholder = { Text("写下待办、考试提醒等…") }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        noteScope.launch {
                            noteSheetState.hide()
                        }.invokeOnCompletion {
                            selectedDateForNote = null
                            noteDraft = ""
                        }
                    }) {
                        Text("取消")
                    }
                    if (notesByDate.containsKey(targetDate.toString())) {
                        TextButton(onClick = {
                            noteScope.launch(Dispatchers.IO) {
                                dao.deleteDayNote(timetableId, targetDate.toString())
                            }
                            noteScope.launch {
                                noteSheetState.hide()
                            }.invokeOnCompletion {
                                selectedDateForNote = null
                                noteDraft = ""
                            }
                        }) {
                            Text("删除")
                        }
                    }
                    Button(onClick = {
                        val content = noteDraft.trim()
                        noteScope.launch(Dispatchers.IO) {
                            if (content.isBlank()) {
                                dao.deleteDayNote(timetableId, targetDate.toString())
                            } else {
                                dao.upsertDayNote(
                                    DayNoteEntity(
                                        timetableId = timetableId,
                                        date = targetDate.toString(),
                                        content = content
                                    )
                                )
                            }
                        }
                        noteScope.launch {
                            noteSheetState.hide()
                        }.invokeOnCompletion {
                            selectedDateForNote = null
                            noteDraft = ""
                        }
                    }) {
                        Text("保存")
                    }
                }
            }
        }
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
                blocks.add(CourseBlock(course, course.dayOfWeek, start, prev))
                start = sorted[i]
            }
            prev = sorted[i]
        }
        blocks.add(CourseBlock(course, course.dayOfWeek, start, prev))
    }
    return blocks
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    courses: List<Course>,
    lessonTimes: List<LessonTime>,
    cellHeight: Dp = 60.dp,
    cellPadding: Dp = 2.dp,
    showWeekend: Boolean = true,
    currentWeek: Int? = null,
    realCurrentWeek: Int? = null,
    courseEntities: List<CourseEntity> = emptyList(),
    lessonTimeEntities: List<LessonTimeEntity> = emptyList(),
    dayNotes: List<DayNoteEntity> = emptyList(),
    onDayNoteClick: (LocalDate) -> Unit = {},
) {
    var selectedCourse by remember { mutableStateOf<CourseEntity?>(null) }
    val haptic = LocalHapticFeedback.current
    val allDayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    val visibleDays = if (showWeekend) (1..7).toList() else (1..5).toList()
    val visibleDayLabels = visibleDays.map { allDayLabels[it - 1] }
    val courseBlocks = buildCourseBlocks(courses)
    val scrollState = rememberScrollState()
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val notesByDate = remember(dayNotes) { dayNotes.associateBy { it.date } }

    // 动态计算左侧时间列宽度
    val leftColumnWidth by remember(lessonTimes) {
        mutableStateOf(
            with(density) {
                val maxPx = lessonTimes.maxOfOrNull { lesson ->
                    val periodWidth = textMeasurer.measure(AnnotatedString(lesson.period.toString())).size.width.toFloat()
                    val timeWidth = textMeasurer.measure(AnnotatedString("${lesson.startTime}\n${lesson.endTime}")).size.width.toFloat()
                    maxOf(periodWidth, timeWidth)
                } ?: 35f
                maxPx.toDp() + 2.dp
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalWidth = maxWidth
        val dayColumnWidth = (totalWidth - leftColumnWidth - 5.dp) / visibleDayLabels.size

        var headerHeightDp by remember { mutableStateOf(0.dp) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(bottom = 32.dp)) {
                val firstDayOfCurrentWeek = LocalDate.now()
                    .with(java.time.DayOfWeek.MONDAY)
                    .plusWeeks(((currentWeek ?: 1) - (realCurrentWeek ?: 1)).toLong())

                val visibleDates = visibleDays.map { day ->
                    firstDayOfCurrentWeek.plusDays((day - 1).toLong())
                }

                // Header
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
                                text = "${firstDayOfCurrentWeek.monthValue}月",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    visibleDates.forEachIndexed { index, date ->
                        val dayLabel = allDayLabels[visibleDays[index] - 1]
                        val note = notesByDate[date.toString()]
                        Box(
                            modifier = Modifier
                                .width(dayColumnWidth)
                                .padding(cellPadding)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    onDayNoteClick(date)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "周$dayLabel",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "${date.monthValue}/${date.dayOfMonth}",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (note != null) {
                                    Text(
                                        text = "有便签哦",
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Text(
                                        text = "添加便签",
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
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
                        Column(
                            modifier = Modifier
                                .width(leftColumnWidth)
                                .fillMaxHeight(),
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
                            Box(
                                modifier = Modifier
                                    .width(dayColumnWidth)
                                    .height(cellHeight)
                                    .padding(cellPadding)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                            )
                        }
                    }
                }
            }

            val todayDayOfWeek = LocalDate.now().dayOfWeek.value

            // 渲染课程块
            courseBlocks.forEachIndexed { index, block ->
                val dayIndex = visibleDays.indexOf(block.dayOfWeek)
                if (dayIndex == -1) return@forEachIndexed
                val span = block.endPeriod - block.startPeriod + 1

                val alpha = remember(currentWeek) { Animatable(0f) }
                val offsetY = remember(currentWeek) { Animatable(20f) }

                LaunchedEffect(currentWeek) {
                    delay(index * 50L)
                    launch { alpha.animateTo(1f, animationSpec = tween(400)) }
                    launch { offsetY.animateTo(0f, animationSpec = tween(400)) }
                }

                Card(
                    modifier = Modifier
                        .absoluteOffset(
                            x = leftColumnWidth + dayColumnWidth * dayIndex + cellPadding,
                            y = headerHeightDp + cellHeight * (block.startPeriod - 1) + cellPadding + offsetY.value.dp
                        )
                        .width(dayColumnWidth - cellPadding * 2)
                        .height(cellHeight * span - cellPadding * 2)
                        .alpha(alpha.value * if (block.course.weeks.contains(currentWeek)) 1f else 0.5f),
                    shape = RoundedCornerShape(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if ((block.dayOfWeek == todayDayOfWeek) &&
                            (block.course.weeks.contains(currentWeek)) &&
                            (currentWeek == realCurrentWeek)
                        ) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondary,
                        contentColor = if ((block.dayOfWeek == todayDayOfWeek) &&
                            (block.course.weeks.contains(currentWeek)) &&
                            (currentWeek == realCurrentWeek)
                        ) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSecondary
                    ),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        val courseEntity = courseEntities.find { entity ->
                            entity.name == block.course.name &&
                                    entity.location == block.course.location &&
                                    entity.dayOfWeek == block.dayOfWeek &&
                                    entity.periods == block.course.periods &&
                                    entity.weeks == block.course.weeks
                        }
                        if (courseEntity != null) {
                            selectedCourse = courseEntity
                        }
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        val nextWeek =
                            block.course.weeks.filter { it > (currentWeek ?: 0) }.minOrNull()
                        val name = if (block.course.weeks.contains(currentWeek)) {
                            block.course.name
                        } else if (nextWeek != null) {
                            "[第${nextWeek}周] ${block.course.name}"
                        } else {
                            "[非本周] ${block.course.name}"
                        }
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

    if (selectedCourse != null) {
        CourseDetailBottomSheet(
            course = selectedCourse!!,
            lessonTimes = lessonTimeEntities,
            onDismiss = { selectedCourse = null },
            sheetState = rememberModalBottomSheetState()
        )
    }
}
