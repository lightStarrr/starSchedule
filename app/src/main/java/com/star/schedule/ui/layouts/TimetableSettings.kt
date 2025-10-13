package com.star.schedule.ui.layouts

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.star.schedule.db.CourseEntity
import com.star.schedule.db.LessonTimeEntity
import com.star.schedule.db.ScheduleDao
import com.star.schedule.db.TimetableEntity
import com.star.schedule.ui.components.OptimizedBottomSheet
import com.star.schedule.utils.ValidationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.CellType
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableSettings(dao: ScheduleDao) {
    val scope = rememberCoroutineScope()
    val timetables by dao.getAllTimetables().collectAsState(initial = emptyList())
    val haptic = LocalHapticFeedback.current
    LocalContext.current

    // BottomSheet 状态管理
    var showAddLessonSheet by remember { mutableStateOf(false) }
    var showAddCourseSheet by remember { mutableStateOf(false) }
    var showEditLessonSheet by remember { mutableStateOf<LessonTimeEntity?>(null) }
    var showEditCourseSheet by remember { mutableStateOf<CourseEntity?>(null) }
    var showTimetableDetailSheet by remember { mutableStateOf<TimetableEntity?>(null) }
    var showImportOptionsSheet by remember { mutableStateOf(false) }
    var showWakeUpImportSheet by remember { mutableStateOf(false) }
    var showXuexitongImportSheet by remember { mutableStateOf(false) }
    var currentTimetableId by remember { mutableStateOf<Long?>(null) }

    // BottomSheet状态
    val addLessonSheetState = rememberModalBottomSheetState()
    val addCourseSheetState = rememberModalBottomSheetState()
    val editLessonSheetState = rememberModalBottomSheetState()
    val editCourseSheetState = rememberModalBottomSheetState()
    val timetableDetailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val importOptionsSheetState = rememberModalBottomSheetState()
    val wakeUpImportSheetState = rememberModalBottomSheetState()
    val xuexitongImportSheetState = rememberModalBottomSheetState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp, 16.dp, 16.dp, 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "课表管理",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        // 新建和导入按钮
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    shape = RoundedCornerShape(
                        topStart = 50.dp,
                        topEnd = 8.dp,
                        bottomEnd = 8.dp,
                        bottomStart = 50.dp
                    ),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        scope.launch {
                            dao.insertTimetableWithReminders(
                                TimetableEntity(
                                    name = "新建课表",
                                    showWeekend = true,
                                    startDate = LocalDate.now().toString()
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(0.5f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("新建")
                    }
                }

                Spacer(Modifier.width(2.dp))

                Button(
                    shape = RoundedCornerShape(
                        topStart = 8.dp,
                        topEnd = 50.dp,
                        bottomEnd = 50.dp,
                        bottomStart = 8.dp
                    ),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        showImportOptionsSheet = true
                    },
                    modifier = Modifier.weight(0.5f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("导入")
                    }
                }
            }
        }
        items(timetables) { timetable ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    showTimetableDetailSheet = timetable
                }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(timetable.name, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            scope.launch {
                                dao.deleteTimetableWithReminders(
                                    timetable
                                )
                            }
                        }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "删除课表")
                        }
                    }
                }
            }
        }


    }

    // 课表详情 BottomSheet
    showTimetableDetailSheet?.let { timetable ->
        TimetableDetailSheet(
            timetable = timetable,
            onDismiss = {
                scope.launch { timetableDetailSheetState.hide() }.invokeOnCompletion {
                    if (!timetableDetailSheetState.isVisible) {
                        showTimetableDetailSheet = null
                    }
                }
            },
            dao = dao,
            sheetState = timetableDetailSheetState
        )
    }

    // 新增课程时间 BottomSheet
    if (showAddLessonSheet && currentTimetableId != null) {
        AddLessonTimeSheet(
            timetableId = currentTimetableId!!,
            onDismiss = {
                scope.launch { addLessonSheetState.hide() }.invokeOnCompletion {
                    if (!addLessonSheetState.isVisible) {
                        showAddLessonSheet = false
                        currentTimetableId = null
                    }
                }
            },
            dao = dao,
            sheetState = addLessonSheetState
        )
    }

    // 新增课程 BottomSheet
    if (showAddCourseSheet && currentTimetableId != null) {
        AddCourseSheet(
            timetableId = currentTimetableId!!,
            onDismiss = {
                scope.launch { addCourseSheetState.hide() }.invokeOnCompletion {
                    if (!addCourseSheetState.isVisible) {
                        showAddCourseSheet = false
                        currentTimetableId = null
                    }
                }
            },
            dao = dao,
            sheetState = addCourseSheetState
        )
    }

    // 编辑课程时间 BottomSheet
    showEditLessonSheet?.let { lesson ->
        EditLessonTimeSheet(
            lesson = lesson,
            onDismiss = {
                scope.launch { editLessonSheetState.hide() }.invokeOnCompletion {
                    if (!editLessonSheetState.isVisible) {
                        showEditLessonSheet = null
                    }
                }
            },
            dao = dao,
            sheetState = editLessonSheetState
        )
    }

    // 编辑课程 BottomSheet
    showEditCourseSheet?.let { course ->
        EditCourseSheet(
            course = course,
            onDismiss = {
                scope.launch { editCourseSheetState.hide() }.invokeOnCompletion {
                    if (!editCourseSheetState.isVisible) {
                        showEditCourseSheet = null
                    }
                }
            },
            dao = dao,
            sheetState = editCourseSheetState
        )
    }

    // 导入选项 BottomSheet
    if (showImportOptionsSheet) {
        ImportOptionsSheet(
            onDismiss = {
                scope.launch { importOptionsSheetState.hide() }.invokeOnCompletion {
                    if (!importOptionsSheetState.isVisible) {
                        showImportOptionsSheet = false
                    }
                }
            },
            onWakeUpImport = {
                scope.launch { importOptionsSheetState.hide() }.invokeOnCompletion {
                    if (!importOptionsSheetState.isVisible) {
                        showImportOptionsSheet = false
                        showWakeUpImportSheet = true
                    }
                }
            },
            onXuexitongImport = {
                scope.launch { importOptionsSheetState.hide() }.invokeOnCompletion {
                    if (!importOptionsSheetState.isVisible) {
                        showImportOptionsSheet = false
                        showXuexitongImportSheet = true
                    }
                }
            },
            sheetState = importOptionsSheetState
        )
    }

    // WakeUp导入 BottomSheet
    if (showWakeUpImportSheet) {
        WakeUpImportSheet(
            onDismiss = {
                scope.launch { wakeUpImportSheetState.hide() }.invokeOnCompletion {
                    if (!wakeUpImportSheetState.isVisible) {
                        showWakeUpImportSheet = false
                    }
                }
            },
            dao = dao,
            sheetState = wakeUpImportSheetState
        )
    }

    // 超星导入 BottomSheet
    if (showXuexitongImportSheet) {
        XuexitongImportSheet(
            onDismiss = {
                scope.launch { xuexitongImportSheetState.hide() }.invokeOnCompletion {
                    if (!xuexitongImportSheetState.isVisible) {
                        showXuexitongImportSheet = false
                    }
                }
            },
            dao = dao,
            sheetState = xuexitongImportSheetState
        )
    }
}

// ---------- 编辑课程时间弹窗 ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLessonTimeSheet(
    lesson: LessonTimeEntity,
    onDismiss: () -> Unit,
    dao: ScheduleDao,
    sheetState: androidx.compose.material3.SheetState
) {
    // 获取当前课表的所有课程时间，用于重叠检测
    val lessonTimes by dao.getLessonTimesFlow(lesson.timetableId)
        .collectAsState(initial = emptyList())
    val sortedLessonTimes =
        lessonTimes.filter { it.id != lesson.id }.sortedBy { it.period } // 排除当前正在编辑的课程时间

    var startTime by remember { mutableStateOf(lesson.startTime) }
    var endTime by remember { mutableStateOf(lesson.endTime) }
    var errorMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // 时间选择器状态
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

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
            Text(
                text = "编辑课程时间",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 错误信息显示
            if (errorMessage.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = startTime,
                onValueChange = {
                    startTime = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text("开始时间") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showStartTimePicker = true }) {
                        Icon(Icons.Rounded.AccessTime, contentDescription = "选择时间")
                    }
                },
                isError = errorMessage.contains("开始时间") || errorMessage.contains("重叠"),
                supportingText = if (errorMessage.contains("开始时间")) {
                    { Text("格式如：08:00") }
                } else if (errorMessage.contains("重叠")) {
                    { Text(errorMessage) }
                } else {
                    { Text("点击选择课程开始时间") }
                }
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = endTime,
                onValueChange = {
                    endTime = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text("结束时间") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showEndTimePicker = true }) {
                        Icon(Icons.Rounded.AccessTime, contentDescription = "选择时间")
                    }
                },
                isError = errorMessage.contains("结束时间") || errorMessage.contains("重叠"),
                supportingText = if (errorMessage.contains("结束时间")) {
                    { Text("格式如：08:45，且必须晚于开始时间") }
                } else if (errorMessage.contains("重叠")) {
                    { Text(errorMessage) }
                } else {
                    { Text("点击选择课程结束时间") }
                }
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    // 数据验证
                    val validationResult = ValidationUtils.LessonTimeValidation.validateTimeFormat(
                        startTime,
                        "开始时间"
                    )
                    if (!validationResult.isValid) {
                        errorMessage = validationResult.errorMessage
                        return@Button
                    }

                    val validationResult2 =
                        ValidationUtils.LessonTimeValidation.validateTimeFormat(endTime, "结束时间")
                    if (!validationResult2.isValid) {
                        errorMessage = validationResult2.errorMessage
                        return@Button
                    }

                    val timeRangeResult =
                        ValidationUtils.LessonTimeValidation.validateTimeRange(startTime, endTime)
                    if (!timeRangeResult.isValid) {
                        errorMessage = timeRangeResult.errorMessage
                        return@Button
                    }

                    // 检查时间重叠
                    val newStartTime = startTime
                    val newEndTime = endTime
                    val hasOverlap = sortedLessonTimes.any { l ->
                        // 检查时间是否重叠
                        (newStartTime < l.endTime && newEndTime > l.startTime)
                    }

                    if (hasOverlap) {
                        errorMessage = "时间重叠：与现有课程时间冲突"
                        return@Button
                    }

                    // 验证通过，保存数据（节次将自动分配）
                    scope.launch {
                        try {
                            val result = dao.insertOrUpdateLessonTimeAutoSort(
                                lesson.copy(
                                    startTime = startTime,
                                    endTime = endTime
                                ),
                                isInsert = false
                            )
                            Log.d("EditLessonTimeSheet", "更新课程时间成功，ID: $result")
                            onDismiss()
                        } catch (e: Exception) {
                            Log.e("EditLessonTimeSheet", "更新课程时间失败", e)
                            errorMessage = "保存失败: ${e.message}"
                        }
                    }
                }) { Text("保存") }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // 时间选择器
    if (showStartTimePicker) {
        TimePickerDialog(
            onDismiss = { showStartTimePicker = false },
            onConfirm = { hour, minute ->
                startTime =
                    java.text.MessageFormat.format("{0,number,00}:{1,number,00}", hour, minute)
                showStartTimePicker = false
            },
            initialHour = startTime.split(":")[0].toIntOrNull() ?: 8,
            initialMinute = startTime.split(":")[1].toIntOrNull() ?: 0
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            onDismiss = { showEndTimePicker = false },
            onConfirm = { hour, minute ->
                endTime =
                    java.text.MessageFormat.format("{0,number,00}:{1,number,00}", hour, minute)
                showEndTimePicker = false
            },
            initialHour = endTime.split(":")[0].toIntOrNull() ?: 8,
            initialMinute = endTime.split(":")[1].toIntOrNull() ?: 45
        )
    }
}

// ---------- 编辑课程弹窗 ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCourseSheet(
    course: CourseEntity,
    onDismiss: () -> Unit,
    dao: ScheduleDao,
    sheetState: androidx.compose.material3.SheetState
) {
    // 获取当前课表的课程和课程时间，用于重叠检测
    val courses by dao.getCoursesFlow(course.timetableId).collectAsState(initial = emptyList())
    val filteredCourses = courses.filter { it.id != course.id } // 排除当前正在编辑的课程

    var name by remember { mutableStateOf(course.name) }
    var teacher by remember { mutableStateOf(course.teacher) }
    var location by remember { mutableStateOf(course.location) }
    var dayOfWeek by remember { mutableStateOf(course.dayOfWeek.toString()) }
    var periods by remember {
        mutableStateOf(
            ValidationUtils.CourseValidation.formatNumberList(
                course.periods
            )
        )
    }
    var weeks by remember { mutableStateOf(ValidationUtils.CourseValidation.formatNumberList(course.weeks)) }
    var errorMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

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
            Text(
                text = "编辑课程",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 错误信息显示
            if (errorMessage.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text("课程名称") },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage.contains("课程名称"),
                supportingText = if (errorMessage.contains("课程名称")) {
                    { Text("课程名称不能为空，且不超过50个字符") }
                } else null
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = teacher,
                onValueChange = {
                    teacher = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text("教师名称（可选）") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("教师名称，不超过50个字符") }
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = location,
                onValueChange = {
                    location = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text("地点") },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage.contains("上课地点"),
                supportingText = if (errorMessage.contains("上课地点")) {
                    { Text("上课地点不能超过100个字符") }
                } else null
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = dayOfWeek,
                onValueChange = {
                    dayOfWeek = it.filter { c -> c.isDigit() }
                    errorMessage = "" // 清除错误信息
                },
                label = { Text("星期 (1-7)") },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage.contains("星期"),
                supportingText = if (errorMessage.contains("星期")) {
                    { Text("1=周一，2=周二，...，7=周日") }
                } else null
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = periods,
                onValueChange = {
                    periods = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text("节次 (如 1,2,3 或 1-7 或 1-5,7)") },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage.contains("节次") || errorMessage.contains("重叠"),
                supportingText = if (errorMessage.contains("节次")) {
                    { Text("用逗号分隔，如：1,2,3 或范围格式：1-7") }
                } else if (errorMessage.contains("重叠")) {
                    { Text(errorMessage) }
                } else {
                    { Text("支持单个数字、逗号分隔或范围格式，如：1,2,3 或 1-7 或 1-5,7") }
                }
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = weeks,
                onValueChange = {
                    weeks = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text("周次 (如 1,2,3 或 1-7 或 1-5,7)") },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage.contains("周次"),
                supportingText = if (errorMessage.contains("周次")) {
                    { Text("用逗号分隔，如：1,2,3 或范围格式：1-7，表示第几周上课") }
                } else {
                    { Text("支持单个数字、逗号分隔或范围格式，如：1,2,3 或 1-7 或 1-5,7") }
                }
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    // 数据验证
                    val validationResult = ValidationUtils.CourseValidation.validateCourseData(
                        name = name,
                        location = location,
                        dayOfWeek = dayOfWeek,
                        periods = periods,
                        weeks = weeks
                    )

                    if (!validationResult.isValid) {
                        errorMessage = validationResult.errorMessage
                        return@Button
                    }

                    // 检查课程时间重叠
                    val day = dayOfWeek.toInt()
                    val periodList = ValidationUtils.CourseValidation.parseNumberRange(periods)
                    val weekList = ValidationUtils.CourseValidation.parseNumberRange(weeks)

                    // 检查是否有时间冲突
                    val hasTimeOverlap = filteredCourses.any { existingCourse ->
                        // 检查是否同一天
                        if (existingCourse.dayOfWeek != day) return@any false

                        // 检查是否同一周
                        val weekOverlap = existingCourse.weeks.any { w -> weekList.contains(w) }
                        if (!weekOverlap) return@any false

                        // 检查是否同一节次
                        val periodOverlap =
                            existingCourse.periods.any { p -> periodList.contains(p) }
                        periodOverlap
                    }

                    if (hasTimeOverlap) {
                        errorMessage = "时间重叠：与现有课程在同一时间"
                        return@Button
                    }

                    // 验证通过，保存数据
                    scope.launch {
                        try {
                            dao.updateCourseWithReminders(
                                course.copy(
                                    name = name,
                                    teacher = teacher,
                                    location = location,
                                    dayOfWeek = day,
                                    periods = periodList,
                                    weeks = weekList
                                )
                            )
                            Log.d("EditCourseSheet", "更新课程成功，ID: ${course.id}")
                            onDismiss()
                        } catch (e: Exception) {
                            Log.e("EditCourseSheet", "更新课程失败", e)
                            errorMessage = "保存失败: ${e.message}"
                        }
                    }
                }) {
                    Text("保存")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ------------------ 新增课程时间弹窗 ------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLessonTimeSheet(
    timetableId: Long,
    onDismiss: () -> Unit,
    dao: ScheduleDao,
    sheetState: androidx.compose.material3.SheetState
) {
    // 获取当前课表的所有课程时间，用于重叠检测
    val lessonTimes by dao.getLessonTimesFlow(timetableId).collectAsState(initial = emptyList())

    var startTime by remember { mutableStateOf("08:00") }
    var endTime by remember { mutableStateOf("08:45") }
    var errorMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // 时间选择器状态
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

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
            Text(
                text = "新增课程时间",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 错误信息显示
            if (errorMessage.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = startTime,
                onValueChange = {
                    startTime = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text("开始时间") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showStartTimePicker = true }) {
                        Icon(Icons.Rounded.AccessTime, contentDescription = "选择时间")
                    }
                },
                isError = errorMessage.contains("开始时间") || errorMessage.contains("重叠"),
                supportingText = if (errorMessage.contains("开始时间")) {
                    { Text("格式如：08:00") }
                } else if (errorMessage.contains("重叠")) {
                    { Text(errorMessage) }
                } else {
                    { Text("点击选择课程开始时间") }
                }
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = endTime,
                onValueChange = {
                    endTime = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text("结束时间") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showEndTimePicker = true }) {
                        Icon(Icons.Rounded.AccessTime, contentDescription = "选择时间")
                    }
                },
                isError = errorMessage.contains("结束时间") || errorMessage.contains("重叠"),
                supportingText = if (errorMessage.contains("结束时间")) {
                    { Text("格式如：08:45，且必须晚于开始时间") }
                } else if (errorMessage.contains("重叠")) {
                    { Text(errorMessage) }
                } else {
                    { Text("点击选择课程结束时间") }
                }
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    // 数据验证
                    val validationResult = ValidationUtils.LessonTimeValidation.validateTimeFormat(
                        startTime,
                        "开始时间"
                    )
                    if (!validationResult.isValid) {
                        errorMessage = validationResult.errorMessage
                        return@Button
                    }

                    val validationResult2 =
                        ValidationUtils.LessonTimeValidation.validateTimeFormat(endTime, "结束时间")
                    if (!validationResult2.isValid) {
                        errorMessage = validationResult2.errorMessage
                        return@Button
                    }

                    val timeRangeResult =
                        ValidationUtils.LessonTimeValidation.validateTimeRange(startTime, endTime)
                    if (!timeRangeResult.isValid) {
                        errorMessage = timeRangeResult.errorMessage
                        return@Button
                    }

                    // 检查时间重叠
                    val newStartTime = startTime
                    val newEndTime = endTime
                    val hasOverlap = lessonTimes.any { lesson ->
                        // 检查时间是否重叠
                        (newStartTime < lesson.endTime && newEndTime > lesson.startTime)
                    }

                    if (hasOverlap) {
                        errorMessage = "时间重叠：与现有课程时间冲突"
                        return@Button
                    }

                    // 验证通过，保存数据（节次将自动分配）
                    scope.launch {
                        try {
                            val result = dao.insertOrUpdateLessonTimeAutoSort(
                                LessonTimeEntity(
                                    timetableId = timetableId,
                                    period = 1, // 临时值，会被自动排序方法覆盖
                                    startTime = startTime,
                                    endTime = endTime
                                )
                            )
                            Log.d("AddLessonTimeSheet", "新增课程时间成功，ID: $result")
                            onDismiss()
                        } catch (e: Exception) {
                            Log.e("AddLessonTimeSheet", "新增课程时间失败", e)
                            errorMessage = "保存失败: ${e.message}"
                        }
                    }
                }) {
                    Text("保存")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // 时间选择器
    if (showStartTimePicker) {
        TimePickerDialog(
            onDismiss = { showStartTimePicker = false },
            onConfirm = { hour, minute ->
                startTime =
                    java.text.MessageFormat.format("{0,number,00}:{1,number,00}", hour, minute)
                showStartTimePicker = false
            },
            initialHour = startTime.split(":")[0].toIntOrNull() ?: 8,
            initialMinute = startTime.split(":")[1].toIntOrNull() ?: 0
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            onDismiss = { showEndTimePicker = false },
            onConfirm = { hour, minute ->
                endTime =
                    java.text.MessageFormat.format("{0,number,00}:{1,number,00}", hour, minute)
                showEndTimePicker = false
            },
            initialHour = endTime.split(":")[0].toIntOrNull() ?: 8,
            initialMinute = endTime.split(":")[1].toIntOrNull() ?: 45
        )
    }
}

// ------------------ 新增课程弹窗 ------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCourseSheet(
    timetableId: Long,
    onDismiss: () -> Unit,
    dao: ScheduleDao,
    sheetState: androidx.compose.material3.SheetState
) {
    // 获取当前课表的课程和课程时间，用于重叠检测
    val courses by dao.getCoursesFlow(timetableId).collectAsState(initial = emptyList())

    var name by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var dayOfWeek by remember { mutableStateOf("1") }
    var periods by remember { mutableStateOf("1") }
    var weeks by remember { mutableStateOf("1") }
    var errorMessage by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

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
            Text(
                text = "新增课程",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 错误信息显示
            if (errorMessage.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text("课程名称") },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage.contains("课程名称"),
                supportingText = if (errorMessage.contains("课程名称")) {
                    { Text("课程名称不能为空，且不超过50个字符") }
                } else null
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = teacher,
                onValueChange = {
                    teacher = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text("教师名称（可选）") },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("教师名称，不超过50个字符") }
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = location,
                onValueChange = {
                    location = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text("地点") },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage.contains("上课地点"),
                supportingText = if (errorMessage.contains("上课地点")) {
                    { Text("上课地点不能超过100个字符") }
                } else null
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = dayOfWeek,
                onValueChange = {
                    dayOfWeek = it.filter { c -> c.isDigit() }
                    errorMessage = "" // 清除错误信息
                },
                label = { Text("星期 (1-7)") },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage.contains("星期"),
                supportingText = if (errorMessage.contains("星期")) {
                    { Text("1=周一，2=周二，...，7=周日") }
                } else null
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = periods,
                onValueChange = {
                    periods = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text("节次 (如 1,2,3 或 1-7 或 1-5,7)") },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage.contains("节次") || errorMessage.contains("重叠"),
                supportingText = if (errorMessage.contains("节次")) {
                    { Text("用逗号分隔，如：1,2,3 或范围格式：1-7") }
                } else if (errorMessage.contains("重叠")) {
                    { Text(errorMessage) }
                } else {
                    { Text("支持单个数字、逗号分隔或范围格式，如：1,2,3 或 1-7 或 1-5,7") }
                }
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = weeks,
                onValueChange = {
                    weeks = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text("周次 (如 1,2,3 或 1-7 或 1-5,7)") },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage.contains("周次"),
                supportingText = if (errorMessage.contains("周次")) {
                    { Text("用逗号分隔，如：1,2,3 或范围格式：1-7，表示第几周上课") }
                } else {
                    { Text("支持单个数字、逗号分隔或范围格式，如：1,2,3 或 1-7 或 1-5,7") }
                }
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    // 数据验证
                    val validationResult = ValidationUtils.CourseValidation.validateCourseData(
                        name = name,
                        location = location,
                        dayOfWeek = dayOfWeek,
                        periods = periods,
                        weeks = weeks
                    )

                    if (!validationResult.isValid) {
                        errorMessage = validationResult.errorMessage
                        return@Button
                    }

                    // 检查课程时间重叠
                    val day = dayOfWeek.toIntOrNull() ?: 1
                    val periodList = ValidationUtils.CourseValidation.parseNumberRange(periods)
                    val weekList = ValidationUtils.CourseValidation.parseNumberRange(weeks)

                    // 检查是否有时间冲突
                    val hasTimeOverlap = courses.any { existingCourse ->
                        // 检查是否同一天
                        if (existingCourse.dayOfWeek != day) return@any false

                        // 检查是否同一周
                        val weekOverlap = existingCourse.weeks.any { w -> weekList.contains(w) }
                        if (!weekOverlap) return@any false

                        // 检查是否同一节次
                        val periodOverlap =
                            existingCourse.periods.any { p -> periodList.contains(p) }
                        periodOverlap
                    }

                    if (hasTimeOverlap) {
                        errorMessage = "时间重叠：与现有课程在同一时间"
                        return@Button
                    }

                    // 验证通过，保存数据
                    scope.launch {
                        try {
                            val result = dao.insertCourseWithReminders(
                                CourseEntity(
                                    timetableId = timetableId,
                                    name = name,
                                    teacher = teacher,
                                    location = location,
                                    dayOfWeek = day,
                                    periods = periodList,
                                    weeks = weekList
                                )
                            )
                            Log.d("AddCourseSheet", "新增课程成功，ID: $result")
                            onDismiss()
                        } catch (e: Exception) {
                            Log.e("AddCourseSheet", "新增课程失败", e)
                            errorMessage = "保存失败: ${e.message}"
                        }
                    }
                }) {
                    Text("保存")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableDetailSheet(
    timetable: TimetableEntity,
    onDismiss: () -> Unit,
    dao: ScheduleDao,
    sheetState: androidx.compose.material3.SheetState
) {
    val scope = rememberCoroutineScope()

    // 课表信息状态
    var name by remember { mutableStateOf(timetable.name) }
    var startDate by remember { mutableStateOf(timetable.startDate) }
    var showWeekend by remember { mutableStateOf(timetable.showWeekend) }
    var showFuture by remember { mutableStateOf(timetable.showFuture) }
    var rowHeight by remember { mutableStateOf(timetable.rowHeight.toFloat()) }
    var reminderTime by remember { mutableStateOf(timetable.reminderTime.toFloat()) }
    var errorMessage by remember { mutableStateOf("") }

    // 日期选择器状态
    var showDatePicker by remember { mutableStateOf(false) }

    // 课程时间管理 (按节次排序)
    val lessonTimes by dao.getLessonTimesFlow(timetable.id).collectAsState(initial = emptyList())
    val sortedLessonTimes = lessonTimes.sortedBy { it.period }

    // 课程管理
    val courses by dao.getCoursesFlow(timetable.id).collectAsState(initial = emptyList())

    // 子 BottomSheet 状态
    var showAddLessonSheet by remember { mutableStateOf(false) }
    var showAddCourseSheet by remember { mutableStateOf(false) }
    var showEditLessonSheet by remember { mutableStateOf<LessonTimeEntity?>(null) }
    var showEditCourseSheet by remember { mutableStateOf<CourseEntity?>(null) }

    // 子 BottomSheet 状态
    val addLessonSheetState = rememberModalBottomSheetState()
    val addCourseSheetState = rememberModalBottomSheetState()
    val editLessonSheetState = rememberModalBottomSheetState()
    val editCourseSheetState = rememberModalBottomSheetState()

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
            // 错误信息显示
            if (errorMessage.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // 课表信息编辑
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            errorMessage = "" // 清除错误信息
                        },
                        label = { Text("课表名称") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = errorMessage.contains("课程表名称"),
                        supportingText = if (errorMessage.contains("课程表名称")) {
                            { Text("课程表名称不能为空，且不超过100个字符") }
                        } else null
                    )

                    OutlinedTextField(
                        value = startDate,
                        onValueChange = {
                            startDate = it
                            errorMessage = "" // 清除错误信息
                        },
                        label = { Text("开学日期") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(Icons.Rounded.CalendarMonth, contentDescription = "选择日期")
                            }
                        },
                        isError = errorMessage.contains("日期") || errorMessage.contains("学期"),
                        supportingText = if (errorMessage.contains("日期") || errorMessage.contains(
                                "学期"
                            )
                        ) {
                            { Text("请使用有效的日期格式") }
                        } else null
                    )

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("显示周末")
                        Switch(
                            checked = showWeekend,
                            onCheckedChange = { showWeekend = it }
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("显示非本周课程")
                        Switch(
                            checked = showFuture,
                            onCheckedChange = { showFuture = it }
                        )
                    }

                    // 课时行高度设置
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("课时行高度")
                            Text(
                                text = "${rowHeight.toInt()} dp",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = rowHeight,
                            onValueChange = { rowHeight = it },
                            valueRange = 40f..240f,
                            steps = 19
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "40 dp",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "240 dp",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    // 课前提醒时间设置
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("课前提醒时间")
                            Text(
                                text = "${reminderTime.toInt()} 分钟",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = reminderTime,
                            onValueChange = { reminderTime = it },
                            valueRange = 5f..60f,
                            steps = 10
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "5 分钟",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "60 分钟",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Button(
                        onClick = {
                            // 数据验证
                            val validationResult =
                                ValidationUtils.TimetableValidation.validateTimetableData(
                                    name = name,
                                    startDate = startDate
                                )

                            if (!validationResult.isValid) {
                                errorMessage = validationResult.errorMessage
                                return@Button
                            }

                            // 验证通过，保存数据
                            scope.launch {
                                dao.updateTimetableWithReminders(
                                    timetable.copy(
                                        name = name,
                                        startDate = startDate,
                                        showWeekend = showWeekend,
                                        showFuture = showFuture,
                                        rowHeight = rowHeight.toInt(),
                                        reminderTime = reminderTime.toInt()
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("保存修改")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 课程时间管理
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("课程时间管理", style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { showAddLessonSheet = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = "新增课程时间")
                        }
                    }

                    if (sortedLessonTimes.isEmpty()) {
                        Text(
                            text = "暂无课程时间",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        sortedLessonTimes.forEach { lesson ->
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "第${lesson.period}节 ${lesson.startTime}-${lesson.endTime}",
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.width(IntrinsicSize.Min)
                                ) {
                                    IconButton(onClick = { showEditLessonSheet = lesson }) {
                                        Icon(
                                            Icons.Rounded.Edit,
                                            contentDescription = "编辑课程时间"
                                        )
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            dao.deleteLessonTimeAutoSort(
                                                lesson
                                            )
                                        }
                                    }) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "删除课程时间"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 课程管理
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("课程管理", style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { showAddCourseSheet = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = "新增课程")
                        }
                    }

                    if (courses.isEmpty()) {
                        Text(
                            text = "暂无课程",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        courses.forEach { course ->
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${course.name}${if (course.teacher.isNotEmpty()) " (${course.teacher})" else ""} (周${course.dayOfWeek} 节次:${
                                        ValidationUtils.CourseValidation.formatNumberList(course.periods)
                                    })",
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.width(IntrinsicSize.Min)
                                ) {
                                    IconButton(onClick = { showEditCourseSheet = course }) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "编辑课程")
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            dao.deleteCourseWithReminders(
                                                course
                                            )
                                        }
                                    }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "删除课程")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // 日期选择器对话框
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = try {
                LocalDate.parse(startDate).atStartOfDay(ZoneId.systemDefault()).toInstant()
                    .toEpochMilli()
            } catch (_: Exception) {
                System.currentTimeMillis()
            }
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            val date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            startDate = date.toString()
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                    }
                ) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // 子 BottomSheet
    if (showAddLessonSheet) {
        AddLessonTimeSheet(
            timetableId = timetable.id,
            onDismiss = {
                scope.launch { addLessonSheetState.hide() }.invokeOnCompletion {
                    if (!addLessonSheetState.isVisible) {
                        showAddLessonSheet = false
                    }
                }
            },
            dao = dao,
            sheetState = addLessonSheetState
        )
    }

    if (showAddCourseSheet) {
        AddCourseSheet(
            timetableId = timetable.id,
            onDismiss = {
                scope.launch { addCourseSheetState.hide() }.invokeOnCompletion {
                    if (!addCourseSheetState.isVisible) {
                        showAddCourseSheet = false
                    }
                }
            },
            dao = dao,
            sheetState = addCourseSheetState
        )
    }

    showEditLessonSheet?.let { lesson ->
        EditLessonTimeSheet(
            lesson = lesson,
            onDismiss = {
                scope.launch { editLessonSheetState.hide() }.invokeOnCompletion {
                    if (!editLessonSheetState.isVisible) {
                        showEditLessonSheet = null
                    }
                }
            },
            dao = dao,
            sheetState = editLessonSheetState
        )
    }

    showEditCourseSheet?.let { course ->
        EditCourseSheet(
            course = course,
            onDismiss = {
                scope.launch { editCourseSheetState.hide() }.invokeOnCompletion {
                    if (!editCourseSheetState.isVisible) {
                        showEditCourseSheet = null
                    }
                }
            },
            dao = dao,
            sheetState = editCourseSheetState
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    initialHour: Int,
    initialMinute: Int
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(timePickerState.hour, timePickerState.minute)
            }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        text = {
            TimePicker(state = timePickerState)
        }
    )
}

// ---------- 导入选项弹窗 ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportOptionsSheet(
    onDismiss: () -> Unit,
    onWakeUpImport: () -> Unit,
    onXuexitongImport: () -> Unit,
    sheetState: androidx.compose.material3.SheetState
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
        ) {
            Text(
                text = "选择导入方式",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // WakeUp课程表导入选项
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                onClick = { onWakeUpImport() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.CalendarMonth,
                        contentDescription = "WakeUp课程表",
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = "WakeUp课程表",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "从WakeUp课程表在线导入",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                onClick = {
                    onXuexitongImport()
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.FileOpen,
                        contentDescription = "学习通导入",
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = "学习通",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "从学习通导出xls导入",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

//            Spacer(Modifier.height(8.dp))
//
//            Card(
//                modifier = Modifier
//                    .fillMaxWidth(),
//                onClick = {
//                    //跳转activity
//                    val intent = Intent(context, WebActivity::class.java)
//                    context.startActivity(intent)
//                },
//                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
//            ) {
//                Row(
//                    modifier = Modifier.padding(16.dp),
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    Icon(
//                        Icons.Rounded.CalendarMonth,
//                        contentDescription = "从教务系统导入",
//                        modifier = Modifier.padding(end = 12.dp)
//                    )
//                    Column {
//                        Text(
//                            text = "教务系统",
//                            style = MaterialTheme.typography.titleMedium
//                        )
//                        Text(
//                            text = "从教务系统导入",
//                            style = MaterialTheme.typography.bodySmall,
//                            color = MaterialTheme.colorScheme.onSurfaceVariant
//                        )
//                    }
//                }
//            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "更多导入方式即将推出...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}


// ---------- WakeUp导入弹窗 ----------
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WakeUpImportSheet(
    onDismiss: () -> Unit,
    dao: ScheduleDao,
    sheetState: androidx.compose.material3.SheetState
) {
    var shareText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
            Text(
                text = "从WakeUp课程表在线导入",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "请完整复制分享口令",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 示例分享口令
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "[示例]这是来自「WakeUp课程表」的课表分享，30分钟内有效哦，如果失效请朋友再分享一遍叭。为了保护隐私我们选择不监听你的剪贴板，请复制这条消息后，打开App的主界面，右上角第二个按钮 -> 从分享口令导入，按操作提示即可完成导入~分享口令为「0000000000000000」",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = shareText,
                onValueChange = {
                    shareText = it
                    errorMessage = ""
                },
                label = { Text("分享口令或口令内容") },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage.isNotEmpty(),
                supportingText = {
                    if (errorMessage.isNotEmpty()) {
                        Text(errorMessage)
                    } else {
                        Text("可粘贴完整分享口令或直接输入口令内容")
                    }
                },
                placeholder = { Text("粘贴分享口令或输入口令内容") }
            )

            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) { LoadingIndicator() }
            } else {
                Button(
                    onClick = {
                        if (shareText.isBlank()) {
                            errorMessage = "请输入分享口令"
                            return@Button
                        }

                        // 提取口令内容
                        val key = extractKeyFromShareText(shareText)
                        if (key.isBlank()) {
                            errorMessage = "未找到有效的分享口令"
                            return@Button
                        }

                        isLoading = true
                        scope.launch {
                            try {
                                // 调用WakeUp API导入课表
                                val result = importFromWakeUp(key, dao)
                                if (result) {
                                    onDismiss()
                                } else {
                                    errorMessage = "导入失败，请检查分享口令是否有效"
                                }
                            } catch (e: Exception) {
                                errorMessage = "导入失败: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text("导入")
                }
            }
        }
    }
}

// 从分享文本中提取口令
fun extractKeyFromShareText(text: String): String {
    val pattern = "分享口令为「([a-f0-9]+)」".toRegex()
    val match = pattern.find(text)
    val key = match?.groupValues?.get(1) ?: ""
    return key
}

// WakeUp导入函数
suspend fun importFromWakeUp(key: String, dao: ScheduleDao): Boolean = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("https://i.wakeup.fun/share_schedule/get?key=$key")
            .get()
            .build()
        val response = client.newCall(request).execute()
        val body = response.body.string()

        if (response.code != 200) return@withContext false

        val rootJson = Json.parseToJsonElement(body).jsonObject
        if (rootJson["status"]?.jsonPrimitive?.int != 1) return@withContext false

        val dataStr = rootJson["data"]?.jsonPrimitive?.content ?: return@withContext false
        val segments = dataStr.split("\n")
        if (segments.size < 4) return@withContext false

        val timetableInfo = Json.decodeFromString<JsonObject>(segments[0])
        val lessonTimes = Json.decodeFromString<JsonArray>(segments[1])
        val configInfo = Json.decodeFromString<JsonObject>(segments[2])
        val courses = Json.decodeFromString<JsonArray>(segments[3])
        val courseInfo = Json.decodeFromString<JsonArray>(segments[4])

        Log.d("WakeUp", "timetableInfo: $timetableInfo")
        Log.d("WakeUp", "lessonTimes: $lessonTimes")
        Log.d("WakeUp", "configInfo: $configInfo")
        Log.d("WakeUp", "courses: $courses")
        Log.d("WakeUp", "courseInfo: $courseInfo")


        val timetableId = dao.insertTimetableWithReminders(
            TimetableEntity(
                name = configInfo["tableName"]?.jsonPrimitive?.content ?: "未命名WakeUp课程表",
                showWeekend = configInfo["showSun"]?.jsonPrimitive?.boolean ?: true,
                startDate = configInfo["startDate"]?.jsonPrimitive?.content?.let {
                    parseDateAutoFix(
                        it
                    )
                }
                    ?: LocalDate.now().toString()
            )
        )

        // 用于存储已经处理过的时间段，避免重复
        val processedTimes = mutableSetOf<String>()

        lessonTimes.forEach { jsonElement ->
            val lessonObject = jsonElement.jsonObject
            val period = lessonObject["node"]?.jsonPrimitive?.int ?: 1
            val startTime = lessonObject["startTime"]?.jsonPrimitive?.content ?: return@forEach
            val endTime = lessonObject["endTime"]?.jsonPrimitive?.content ?: return@forEach

            if (startTime == endTime) {
                return@forEach
            }

            // 创建时间段的唯一标识符
            val timeKey = "${startTime}_${endTime}"

            // 如果已经处理过相同的时间段，则跳过
            if (processedTimes.contains(timeKey)) {
                return@forEach
            }

            // 将当前时间段添加到已处理集合中
            processedTimes.add(timeKey)

            dao.insertOrUpdateLessonTimeAutoSort(
                LessonTimeEntity(
                    timetableId = timetableId,
                    period = period,
                    startTime = startTime,
                    endTime = endTime
                )
            )
        }

        courseInfo.forEach { jsonElement ->
            val courseInfoObject = jsonElement.jsonObject
            val startWeek = courseInfoObject["startWeek"]?.jsonPrimitive?.int ?: return@forEach
            val endWeek = courseInfoObject["endWeek"]?.jsonPrimitive?.int ?: return@forEach
            val type = courseInfoObject["type"]?.jsonPrimitive?.int ?: return@forEach
            val weeks = when (type) {
                1 -> (startWeek..endWeek).toList().filter { it and 1 == 1 }
                2 -> (startWeek..endWeek).toList().filter { it and 1 == 0 }
                else -> (startWeek..endWeek).toList()
            }

            val startPeriod = courseInfoObject["startNode"]?.jsonPrimitive?.int ?: return@forEach
            val endPeriod =
                startPeriod + (courseInfoObject["step"]?.jsonPrimitive?.int ?: return@forEach) - 1
            val periods = (startPeriod..endPeriod).toList()
            val location = courseInfoObject["room"]?.jsonPrimitive?.content ?: return@forEach
            val courseId = courseInfoObject["id"]?.jsonPrimitive?.int ?: return@forEach
            val teacher = courseInfoObject["teacher"]?.jsonPrimitive?.content ?: return@forEach
            val courseInfo = courses.firstOrNull { course ->
                course.jsonObject["id"]?.jsonPrimitive?.int == courseId
            }
            if (courseInfo == null) return@withContext false
            val courseName =
                courseInfo.jsonObject["courseName"]?.jsonPrimitive?.content ?: return@forEach

            val dayOfWeek = courseInfoObject["day"]?.jsonPrimitive?.int ?: return@forEach

            dao.insertCourseWithReminders(
                CourseEntity(
                    timetableId = timetableId,
                    name = courseName,
                    location = location,
                    dayOfWeek = dayOfWeek,
                    periods = periods,
                    weeks = weeks,
                    teacher = teacher
                )
            )
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun parseDateAutoFix(dateStr: String): String {
    val parts = dateStr.split("-")
    if (parts.size != 3) throw IllegalArgumentException("Invalid date format: $dateStr")
    val year = parts[0].padStart(4, '0')
    val month = parts[1].padStart(2, '0')
    val day = parts[2].padStart(2, '0')
    val fixedDateStr = "$year-$month-$day"
    return LocalDate.parse(fixedDateStr, DateTimeFormatter.ISO_LOCAL_DATE).toString()
}

// ---------- 超星导入弹窗 ----------
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun XuexitongImportSheet(
    onDismiss: () -> Unit,
    dao: ScheduleDao,
    sheetState: androidx.compose.material3.SheetState
) {
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                selectedFileUri = it
                fileName = getFileNameFromUri(context, it) ?: "未知文件"
                errorMessage = ""
            }
        }
    )

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
            Text(
                text = "从超星导出xls文件导入",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "请选择从超星导出的xls文件",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 文件选择说明
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "从超星APP或网页版导出课程表xls文件，然后在此处选择该文件进行导入。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // 文件选择按钮
            Button(
                onClick = {
                    filePickerLauncher.launch("application/vnd.ms-excel")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FileOpen, contentDescription = "选择文件")
                    Spacer(Modifier.width(8.dp))
                    Text("选择xls文件")
                }
            }

            // 显示选中的文件
            if (fileName.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.FileOpen, contentDescription = "已选择文件")
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 错误信息显示
            if (errorMessage.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) { LoadingIndicator() }
            } else {
                Button(
                    onClick = {
                        if (selectedFileUri == null) {
                            errorMessage = "请先选择xls文件"
                            return@Button
                        }

                        isLoading = true
                        scope.launch {
                            try {
                                // 调用超星导入函数
                                val result = importFromXuexitong(selectedFileUri!!, context, dao)
                                if (result) {
                                    onDismiss()
                                } else {
                                    errorMessage = "导入失败，请检查文件格式是否正确"
                                }
                            } catch (e: Exception) {
                                errorMessage = "导入失败: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && selectedFileUri != null
                ) {
                    Text("导入")
                }
            }
        }
    }
}

// 从URI获取文件名
fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var fileName: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst()) {
            fileName = cursor.getString(nameIndex)
        }
    }
    return fileName
}

// 超星导入函数
suspend fun importFromXuexitong(
    fileUri: Uri,
    context: Context,
    dao: ScheduleDao
): Boolean = withContext(Dispatchers.IO) {
    try {
        val inputStream: InputStream = context.contentResolver.openInputStream(fileUri)
            ?: return@withContext false
        val workbook = HSSFWorkbook(inputStream)
        val sheet = workbook.getSheetAt(0)

        data class TimeSlot(val row: Int, val start: String, val end: String)
        data class WeekInfo(val col: Int, val weekDay: Int)
        data class Course(
            val name: String,
            val teacher: String,
            val weeks: List<Int>,
            val location: String,
            val timeSlots: MutableList<Int>,
            val weekDay: Int
        )

        val timeListRegex = Regex("""\d{1,2}:\d{2}""")
        val weekRegex = Regex("""星期\s*([一二三四五六日])""")

        val timeList = mutableListOf<TimeSlot>()
        val weekList = mutableListOf<WeekInfo>()
        val courses = mutableListOf<Course>()

        // 创建公式计算器
        val evaluator = workbook.creationHelper.createFormulaEvaluator()

        // 解析所有行的时间段（第5列）
        for (rowIndex in 0..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            val cell = row.getCell(4) ?: continue
            val text = cell.toString().trim()
            if (text.isEmpty()) continue

            val lines =
                text.split("\n", "\r\n").map { it.trim() }.filter { it.matches(timeListRegex) }

            if (lines.size == 2) {
                timeList.add(TimeSlot(row = rowIndex + 1, start = lines[0], end = lines[1]))
            }
        }

        // 解析第六行（索引5）的星期列
        val weekRow = sheet.getRow(5)
        if (weekRow != null) {
            for (colIndex in 0 until weekRow.lastCellNum) {
                val cell = weekRow.getCell(colIndex) ?: continue

                val cellValue = when (cell.cellTypeEnum) {
                    CellType.FORMULA -> evaluator.evaluate(cell).formatAsString()
                    CellType.STRING -> cell.stringCellValue
                    CellType.NUMERIC -> cell.numericCellValue.toInt().toString()
                    else -> cell.toString()
                }.replace("\n", "").replace("\r", "").trim()

                val match = weekRegex.find(cellValue)
                if (match != null) {
                    val weekStr = match.groupValues[1]
                    val weekNum = when (weekStr) {
                        "一" -> 1
                        "二" -> 2
                        "三" -> 3
                        "四" -> 4
                        "五" -> 5
                        "六" -> 6
                        "日" -> 7
                        else -> return@withContext false
                    }
                    weekList.add(WeekInfo(col = colIndex + 1, weekDay = weekNum))
                }
            }
        }

        // 解析课程信息
        fun parseWeeks(weekStr: String): List<Int> {
            val result = mutableListOf<Int>()
            val regex = Regex("""(\d+)(?:-(\d+))?""")
            regex.findAll(weekStr).forEach {
                val start = it.groupValues[1].toInt()
                val end = it.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: start
                for (w in start..end) result.add(w)
            }
            return result
        }

        fun mergeOrCreateCourse(newCourse: Course, slot: Int) {
            val existing = courses.find {
                it.name == newCourse.name &&
                        it.teacher == newCourse.teacher &&
                        it.weeks == newCourse.weeks &&
                        it.location == newCourse.location &&
                        it.weekDay == newCourse.weekDay
            }
            if (existing != null) {
                existing.timeSlots.add(slot)
            } else {
                newCourse.timeSlots.add(slot)
                courses.add(newCourse)
            }
        }

        for (week in weekList) {
            for ((i, time) in timeList.withIndex()) {
                val row = sheet.getRow(time.row - 1) ?: continue
                val cell = row.getCell(week.col - 1) ?: continue
                val cellText = cell.toString().trim()
                if (cellText.isEmpty()) continue

                val records = cellText.split("………………").map { it.trim() }.filter { it.isNotEmpty() }
                for (record in records) {
                    val lines = record.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    if (lines.size < 3) continue

                    val name = lines[0]
                    val teacherLine = lines[1]
                    val teacherMatch = Regex("""(.+?)【(.+)】""").find(teacherLine)
                    val teacher = teacherMatch?.groupValues?.get(1)?.trim() ?: ""
                    val weeks =
                        teacherMatch?.groupValues?.get(2)?.let { parseWeeks(it) } ?: emptyList()
                    val location = lines[2]

                    val newCourse = Course(
                        name = name,
                        teacher = teacher,
                        weeks = weeks,
                        location = location,
                        timeSlots = mutableListOf(),
                        weekDay = week.weekDay
                    )

                    mergeOrCreateCourse(newCourse, i + 1)
                }
            }
        }

        fun <T> List<T>.hasNoData(): Boolean = this.isEmpty() || this.all {
            when (it) {
                is TimeSlot -> it.start.isEmpty() || it.end.isEmpty()
                is WeekInfo -> false // WeekInfo 只有 col 和 weekDay，基本不会空
                is Course -> it.name.isEmpty() || it.teacher.isEmpty() || it.weeks.isEmpty() || it.location.isEmpty() || it.timeSlots.isEmpty()
                else -> false
            }
        }

        if (timeList.hasNoData() || weekList.hasNoData() || courses.hasNoData()) {
            Log.e("XuexitongImport", "解析失败: timeList, weekList 或 courses 内部数据无效")
            workbook.close()
            inputStream.close()
            return@withContext false
        }

        workbook.close()
        inputStream.close()

        val timetableId = dao.insertTimetableWithReminders(
            TimetableEntity(
                name = "学习通导入的课表",
                showWeekend = true,
                startDate = LocalDate.now().toString()
            )
        )

        for (time in timeList) {
            dao.insertOrUpdateLessonTimeAutoSort(
                LessonTimeEntity(
                    timetableId = timetableId,
                    period = 1,
                    startTime = time.start,
                    endTime = time.end
                )
            )
        }

        for (course in courses) {
            dao.insertCourseWithReminders(
                CourseEntity(
                    timetableId = timetableId,
                    name = course.name,
                    teacher = course.teacher,
                    location = course.location,
                    dayOfWeek = course.weekDay,
                    periods = course.timeSlots,
                    weeks = course.weeks
                )
            )
        }

        true
    } catch (e: Exception) {
        Log.e("XuexitongImport", "解析失败: ${e.message}", e)
        false
    }
}
