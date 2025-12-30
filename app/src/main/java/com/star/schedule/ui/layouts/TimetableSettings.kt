package com.star.schedule.ui.layouts

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Polyline
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.star.schedule.Constants
import com.star.schedule.R
import com.star.schedule.autoupdate.QiangzhiJwAutoUpdateConfig
import com.star.schedule.autoupdate.TimetableAutoUpdateJson
import com.star.schedule.autoupdate.TimetableAutoUpdateTypes
import com.star.schedule.db.CourseEntity
import com.star.schedule.db.LessonTimeEntity
import com.star.schedule.db.LessonTimeTemplateEntity
import com.star.schedule.db.LessonTimeTemplateItemEntity
import com.star.schedule.db.ScheduleDao
import com.star.schedule.db.TimetableEntity
import com.star.schedule.service.WidgetRefreshManager
import com.star.schedule.ui.components.OptimizedBottomSheet
import com.star.schedule.utils.ImportManager.importTimetable
import com.star.schedule.utils.LessonTimeTemplateExport
import com.star.schedule.utils.LessonTimeTemplateExportBundle
import com.star.schedule.utils.QiangzhiJwImporter
import com.star.schedule.utils.ValidationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class LessonTimeTemplateImportConflictStrategy {
    OVERWRITE,
    RENAME,
    SKIP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableSettings(dao: ScheduleDao) {
    val scope = rememberCoroutineScope()
    val timetables by dao.getAllTimetables().collectAsState(initial = emptyList())
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    var updatingTimetableIds by remember { mutableStateOf(setOf<Long>()) }

    // BottomSheet 状态管理
    var showAddLessonSheet by remember { mutableStateOf(false) }
    var showAddCourseSheet by remember { mutableStateOf(false) }
    var showEditLessonSheet by remember { mutableStateOf<LessonTimeEntity?>(null) }
    var showEditCourseSheet by remember { mutableStateOf<CourseEntity?>(null) }
    var showTimetableDetailSheet by remember { mutableStateOf<TimetableEntity?>(null) }
    var showImportOptionsSheet by remember { mutableStateOf(false) }
    var showWakeUpImportSheet by remember { mutableStateOf(false) }
    var showXuexitongImportSheet by remember { mutableStateOf(false) }
    var showQiangzhiImportSheet by remember { mutableStateOf(false) }
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
    val qiangzhiImportSheetState = rememberModalBottomSheetState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp, 16.dp, 16.dp, 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.timetable_management_title),
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
                                    name = context.getString(R.string.timetable_new_name),
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
                        Text(stringResource(R.string.action_create))
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
                        Icon(Icons.Rounded.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_import))
                    }
                }
            }
        }
        if (timetables.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.timetable_empty_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.timetable_empty_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            itemsIndexed(
                items = timetables,
                key = { _, timetable -> timetable.id }
            ) { index, timetable ->
                val cardAlpha = remember(timetable.id) { Animatable(0f) }
                val cardOffset = remember(timetable.id) { Animatable(32f) }
                var isRemoving by remember(timetable.id) { mutableStateOf(false) }
                val exitDurationMillis = 260
                LaunchedEffect(timetable.id) {
                    val delayMillis = (index * 40).coerceAtMost(240)
                    launch {
                        cardAlpha.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 350,
                                delayMillis = delayMillis
                            )
                        )
                    }
                    launch {
                        cardOffset.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = 350,
                                delayMillis = delayMillis
                            )
                        )
                    }
                }

                AnimatedVisibility(
                    visible = !isRemoving,
                    enter = EnterTransition.None,
                    exit = fadeOut(animationSpec = tween(exitDurationMillis)) + shrinkVertically(
                        animationSpec = tween(exitDurationMillis)
                    )
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = cardAlpha.value
                                translationY = cardOffset.value
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
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
                                Text(
                                    text = timetable.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.width(IntrinsicSize.Min)
                                ) {
                                    val isUpdating = updatingTimetableIds.contains(timetable.id)

                                    val autoUpdateJson = timetable.autoUpdateJson
                                    val autoUpdateType = TimetableAutoUpdateJson.getType(autoUpdateJson)
                                    val hasAutoUpdate = TimetableAutoUpdateTypes.isSupported(autoUpdateType)

                                    if (hasAutoUpdate) {
                                        IconButton(
                                            onClick = {
                                                if (isRemoving || isUpdating) return@IconButton
                                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)

                                                val configJson = autoUpdateJson.orEmpty()
                                                val config = TimetableAutoUpdateJson.decode(configJson)
                                                if (config == null) {
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.auto_update_config_invalid),
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                    return@IconButton
                                                }

                                                when (config) {
                                                    is QiangzhiJwAutoUpdateConfig -> {
                                                        updatingTimetableIds = updatingTimetableIds + timetable.id
                                                        scope.launch {
                                                            try {
                                                                Toast.makeText(
                                                                    context,
                                                                    context.getString(R.string.auto_update_in_progress),
                                                                    Toast.LENGTH_SHORT
                                                                ).show()

                                                                when (val result =
                                                                    QiangzhiJwImporter.updateCoursesForTimetable(
                                                                        timetableId = timetable.id,
                                                                        baseUrl = config.baseUrl,
                                                                        account = config.account,
                                                                        password = config.password,
                                                                        dao = dao
                                                                    )) {
                                                                    is QiangzhiJwImporter.ImportResult.Success -> {
                                                                        result.warning?.let {
                                                                            Toast.makeText(
                                                                                context,
                                                                                it,
                                                                                Toast.LENGTH_LONG
                                                                            ).show()
                                                                        }
                                                                        WidgetRefreshManager.onCourseDataChanged(context)
                                                                        Toast.makeText(
                                                                            context,
                                                                            context.getString(R.string.auto_update_success),
                                                                            Toast.LENGTH_SHORT
                                                                        ).show()
                                                                    }

                                                                    is QiangzhiJwImporter.ImportResult.Error -> {
                                                                        Toast.makeText(
                                                                            context,
                                                                            result.message,
                                                                            Toast.LENGTH_LONG
                                                                        ).show()
                                                                    }
                                                                }
                                                            } finally {
                                                                updatingTimetableIds =
                                                                    updatingTimetableIds - timetable.id
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = !isRemoving && !isUpdating
                                        ) {
                                            Icon(
                                                Icons.Rounded.Refresh,
                                                contentDescription = stringResource(R.string.content_desc_refresh_courses)
                                            )
                                        }
                                    }

                                    IconButton(onClick = {
                                        if (isRemoving || isUpdating) return@IconButton
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        isRemoving = true
                                        scope.launch {
                                            cardAlpha.stop()
                                            cardOffset.stop()
                                            val exitSpec = tween<Float>(durationMillis = exitDurationMillis)
                                            val fadeJob = launch {
                                                cardAlpha.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = exitSpec
                                                )
                                            }
                                            val slideJob = launch {
                                                cardOffset.animateTo(
                                                    targetValue = 16f,
                                                    animationSpec = exitSpec
                                                )
                                            }
                                            fadeJob.join()
                                            slideJob.join()
                                            dao.deleteTimetableWithReminders(timetable)
                                        }
                                    }, enabled = !isRemoving && !isUpdating) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = stringResource(R.string.content_desc_delete_timetable)
                                        )
                                    }
                                }
                            }
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
            onQiangzhiImport = {
                scope.launch { importOptionsSheetState.hide() }.invokeOnCompletion {
                    if (!importOptionsSheetState.isVisible) {
                        showImportOptionsSheet = false
                        showQiangzhiImportSheet = true
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

    // 强智教务系统导入 BottomSheet
    if (showQiangzhiImportSheet) {
        QiangzhiImportSheet(
            onDismiss = {
                scope.launch { qiangzhiImportSheetState.hide() }.invokeOnCompletion {
                    if (!qiangzhiImportSheetState.isVisible) {
                        showQiangzhiImportSheet = false
                    }
                }
            },
            dao = dao,
            sheetState = qiangzhiImportSheetState
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

    val context = LocalContext.current
    val startLabel = stringResource(R.string.label_start_time)
    val endLabel = stringResource(R.string.label_end_time)

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
                text = stringResource(R.string.title_edit_lesson_time),
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

            val overlapMessages = remember(context) {
                listOf(
                    context.getString(R.string.error_time_overlap_lesson_time),
                    context.getString(R.string.error_time_overlap_course)
                )
            }
            val isOverlapError = overlapMessages.any { errorMessage == it }

            OutlinedTextField(
                value = startTime,
                onValueChange = {
                    startTime = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text(startLabel) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showStartTimePicker = true }) {
                        Icon(
                            Icons.Rounded.AccessTime,
                            contentDescription = startLabel
                        )
                    }
                },
                isError = errorMessage.contains(startLabel) || isOverlapError,
                supportingText = if (errorMessage.contains(startLabel)) {
                    { Text(stringResource(R.string.support_start_time_format)) }
                } else if (isOverlapError) {
                    { Text(errorMessage) }
                } else {
                    { Text(stringResource(R.string.support_start_time_hint)) }
                }
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = endTime,
                onValueChange = {
                    endTime = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text(endLabel) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showEndTimePicker = true }) {
                        Icon(
                            Icons.Rounded.AccessTime,
                            contentDescription = endLabel
                        )
                    }
                },
                isError = errorMessage.contains(endLabel) || isOverlapError,
                supportingText = if (errorMessage.contains(endLabel)) {
                    { Text(stringResource(R.string.support_end_time_format)) }
                } else if (isOverlapError) {
                    { Text(errorMessage) }
                } else {
                    { Text(stringResource(R.string.support_end_time_hint)) }
                }
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    // 数据验证
                    val validationResult = ValidationUtils.LessonTimeValidation.validateTimeFormat(
                        startTime,
                        startLabel,
                        context.resources
                    )
                    if (!validationResult.isValid) {
                        errorMessage = validationResult.errorMessage
                        return@Button
                    }

                    val validationResult2 =
                        ValidationUtils.LessonTimeValidation.validateTimeFormat(
                            endTime,
                            endLabel,
                            context.resources
                        )
                    if (!validationResult2.isValid) {
                        errorMessage = validationResult2.errorMessage
                        return@Button
                    }

                    val timeRangeResult =
                        ValidationUtils.LessonTimeValidation.validateTimeRange(
                            startTime,
                            endTime,
                            context.resources
                        )
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
                        errorMessage = context.getString(R.string.error_time_overlap_lesson_time)
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
                            // 课程时间编辑后立即刷新小组件
                            WidgetRefreshManager.onCourseDataChanged(context)
                            onDismiss()
                        } catch (e: Exception) {
                            Log.e("EditLessonTimeSheet", "更新课程时间失败", e)
                            errorMessage = context.getString(
                                R.string.error_save_failed_with_reason,
                                e.message.orEmpty()
                            )
                        }
                    }
                }) { Text(stringResource(R.string.action_save)) }
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
    val context = LocalContext.current
    val startLabel = stringResource(R.string.label_start_time)
    val endLabel = stringResource(R.string.label_end_time)
    val courseNameLabel = stringResource(R.string.label_course_name)
    val teacherLabel = stringResource(R.string.label_teacher_optional)
    val locationLabel = stringResource(R.string.label_location_input)
    val dayOfWeekLabel = stringResource(R.string.label_day_of_week)
    val periodsLabel = stringResource(R.string.label_periods)
    val weeksLabel = stringResource(R.string.label_weeks)

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
                text = stringResource(R.string.title_edit_course),
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

            val courseNameErrors = remember(context) {
                listOf(
                    context.getString(R.string.error_course_name_required),
                    context.getString(R.string.error_course_name_too_long)
                )
            }
            val locationErrors = remember(context) {
                listOf(context.getString(R.string.error_location_too_long))
            }
            val dayOfWeekErrors = remember(context) {
                listOf(
                    context.getString(R.string.error_day_of_week_not_number),
                    context.getString(R.string.error_day_of_week_range)
                )
            }
            val periodErrors = remember(context) {
                listOf(
                    context.getString(R.string.error_periods_empty),
                    context.getString(R.string.error_periods_at_least_one),
                    context.getString(R.string.error_period_range_format),
                    context.getString(R.string.error_period_range_start_number),
                    context.getString(R.string.error_period_range_end_number),
                    context.getString(R.string.error_period_range_start_after_end),
                    context.getString(R.string.error_period_range),
                    context.getString(R.string.error_periods_number_format),
                    context.getString(R.string.error_periods_range),
                    context.getString(R.string.error_periods_duplicate),
                    context.getString(R.string.error_periods_format)
                )
            }
            val weekErrors = remember(context) {
                listOf(
                    context.getString(R.string.error_weeks_empty),
                    context.getString(R.string.error_weeks_at_least_one),
                    context.getString(R.string.error_week_range_format),
                    context.getString(R.string.error_week_range_start_number),
                    context.getString(R.string.error_week_range_end_number),
                    context.getString(R.string.error_week_range_start_after_end),
                    context.getString(R.string.error_week_range),
                    context.getString(R.string.error_weeks_number_format),
                    context.getString(R.string.error_weeks_range),
                    context.getString(R.string.error_weeks_duplicate),
                    context.getString(R.string.error_weeks_format)
                )
            }
            val overlapErrors = remember(context) {
                listOf(
                    context.getString(R.string.error_time_overlap_course),
                    context.getString(R.string.error_time_overlap_lesson_time)
                )
            }

            val isCourseNameError = courseNameErrors.any { errorMessage == it }
            val isLocationError = locationErrors.any { errorMessage == it }
            val isDayError = dayOfWeekErrors.any { errorMessage == it }
            val isPeriodError = periodErrors.any { errorMessage == it }
            val isWeekError = weekErrors.any { errorMessage == it }
            val isOverlapError = overlapErrors.any { errorMessage == it }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text(courseNameLabel) },
                modifier = Modifier.fillMaxWidth(),
                isError = isCourseNameError,
                supportingText = if (isCourseNameError) {
                    { Text(stringResource(R.string.support_course_name_required)) }
                } else null
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = teacher,
                onValueChange = {
                    teacher = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text(teacherLabel) },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(R.string.support_teacher_hint)) }
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = location,
                onValueChange = {
                    location = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text(locationLabel) },
                modifier = Modifier.fillMaxWidth(),
                isError = isLocationError,
                supportingText = if (isLocationError) {
                    { Text(stringResource(R.string.support_location_length)) }
                } else null
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = dayOfWeek,
                onValueChange = {
                    dayOfWeek = it.filter { c -> c.isDigit() }
                    errorMessage = "" // 清除错误信息
                },
                label = { Text(dayOfWeekLabel) },
                modifier = Modifier.fillMaxWidth(),
                isError = isDayError,
                supportingText = if (isDayError) {
                    { Text(stringResource(R.string.support_day_of_week)) }
                } else null
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = periods,
                onValueChange = {
                    periods = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text(periodsLabel) },
                modifier = Modifier.fillMaxWidth(),
                isError = isPeriodError || isOverlapError,
                supportingText = if (isPeriodError) {
                    { Text(stringResource(R.string.support_periods)) }
                } else if (isOverlapError) {
                    { Text(errorMessage) }
                } else {
                    { Text(stringResource(R.string.support_periods_overlap)) }
                }
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = weeks,
                onValueChange = {
                    weeks = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text(weeksLabel) },
                modifier = Modifier.fillMaxWidth(),
                isError = isWeekError,
                supportingText = if (isWeekError) {
                    { Text(stringResource(R.string.support_weeks)) }
                } else {
                    { Text(stringResource(R.string.support_weeks_hint)) }
                }
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    // 数据验证
                    val validationResult = ValidationUtils.CourseValidation.validateCourseData(
                        name = name,
                        location = location,
                        dayOfWeek = dayOfWeek,
                        periods = periods,
                        weeks = weeks,
                        resources = context.resources
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
                        errorMessage = context.getString(R.string.error_time_overlap_course)
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
                            // 课程编辑后立即刷新小组件
                            WidgetRefreshManager.onCourseDataChanged(context)
                            onDismiss()
                        } catch (e: Exception) {
                            Log.e("EditCourseSheet", "更新课程失败", e)
                            errorMessage = context.getString(
                                R.string.error_save_failed_with_reason,
                                e.message.orEmpty()
                            )
                        }
                    }
                }) {
                    Text(stringResource(R.string.action_save))
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
    val context = LocalContext.current
    val startLabel = stringResource(R.string.label_start_time)
    val endLabel = stringResource(R.string.label_end_time)

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
                text = stringResource(R.string.title_add_lesson_time),
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

            val overlapMessages = remember(context) {
                listOf(
                    context.getString(R.string.error_time_overlap_lesson_time),
                    context.getString(R.string.error_time_overlap_course)
                )
            }
            val isOverlapError = overlapMessages.any { errorMessage == it }

            OutlinedTextField(
                value = startTime,
                onValueChange = {
                    startTime = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text(startLabel) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showStartTimePicker = true }) {
                        Icon(
                            Icons.Rounded.AccessTime,
                            contentDescription = startLabel
                        )
                    }
                },
                isError = errorMessage.contains(startLabel) || isOverlapError,
                supportingText = if (errorMessage.contains(startLabel)) {
                    { Text(stringResource(R.string.support_start_time_format)) }
                } else if (isOverlapError) {
                    { Text(errorMessage) }
                } else {
                    { Text(stringResource(R.string.support_start_time_hint)) }
                }
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = endTime,
                onValueChange = {
                    endTime = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text(endLabel) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showEndTimePicker = true }) {
                        Icon(
                            Icons.Rounded.AccessTime,
                            contentDescription = endLabel
                        )
                    }
                },
                isError = errorMessage.contains(endLabel) || isOverlapError,
                supportingText = if (errorMessage.contains(endLabel)) {
                    { Text(stringResource(R.string.support_end_time_format)) }
                } else if (isOverlapError) {
                    { Text(errorMessage) }
                } else {
                    { Text(stringResource(R.string.support_end_time_hint)) }
                }
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    // 数据验证
                    val validationResult = ValidationUtils.LessonTimeValidation.validateTimeFormat(
                        startTime,
                        startLabel,
                        context.resources
                    )
                    if (!validationResult.isValid) {
                        errorMessage = validationResult.errorMessage
                        return@Button
                    }

                    val validationResult2 =
                        ValidationUtils.LessonTimeValidation.validateTimeFormat(
                            endTime,
                            endLabel,
                            context.resources
                        )
                    if (!validationResult2.isValid) {
                        errorMessage = validationResult2.errorMessage
                        return@Button
                    }

                    val timeRangeResult =
                        ValidationUtils.LessonTimeValidation.validateTimeRange(
                            startTime,
                            endTime,
                            context.resources
                        )
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
                        errorMessage = context.getString(R.string.error_time_overlap_lesson_time)
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
                            // 课程时间添加后立即刷新小组件
                            WidgetRefreshManager.onCourseDataChanged(context)
                            onDismiss()
                        } catch (e: Exception) {
                            Log.e("AddLessonTimeSheet", "新增课程时间失败", e)
                            errorMessage = context.getString(
                                R.string.error_save_failed_with_reason,
                                e.message.orEmpty()
                            )
                        }
                    }
                }) {
                    Text(stringResource(R.string.action_save))
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
    val context = LocalContext.current
    val courseNameLabel = stringResource(R.string.label_course_name)
    val teacherLabel = stringResource(R.string.label_teacher_optional)
    val locationLabel = stringResource(R.string.label_location_input)
    val dayOfWeekLabel = stringResource(R.string.label_day_of_week)
    val periodsLabel = stringResource(R.string.label_periods)
    val weeksLabel = stringResource(R.string.label_weeks)

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
                text = stringResource(R.string.title_add_course),
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

            val courseNameErrors = remember(context) {
                listOf(
                    context.getString(R.string.error_course_name_required),
                    context.getString(R.string.error_course_name_too_long)
                )
            }
            val locationErrors = remember(context) {
                listOf(context.getString(R.string.error_location_too_long))
            }
            val dayOfWeekErrors = remember(context) {
                listOf(
                    context.getString(R.string.error_day_of_week_not_number),
                    context.getString(R.string.error_day_of_week_range)
                )
            }
            val periodErrors = remember(context) {
                listOf(
                    context.getString(R.string.error_periods_empty),
                    context.getString(R.string.error_periods_at_least_one),
                    context.getString(R.string.error_period_range_format),
                    context.getString(R.string.error_period_range_start_number),
                    context.getString(R.string.error_period_range_end_number),
                    context.getString(R.string.error_period_range_start_after_end),
                    context.getString(R.string.error_period_range),
                    context.getString(R.string.error_periods_number_format),
                    context.getString(R.string.error_periods_range),
                    context.getString(R.string.error_periods_duplicate),
                    context.getString(R.string.error_periods_format)
                )
            }
            val weekErrors = remember(context) {
                listOf(
                    context.getString(R.string.error_weeks_empty),
                    context.getString(R.string.error_weeks_at_least_one),
                    context.getString(R.string.error_week_range_format),
                    context.getString(R.string.error_week_range_start_number),
                    context.getString(R.string.error_week_range_end_number),
                    context.getString(R.string.error_week_range_start_after_end),
                    context.getString(R.string.error_week_range),
                    context.getString(R.string.error_weeks_number_format),
                    context.getString(R.string.error_weeks_range),
                    context.getString(R.string.error_weeks_duplicate),
                    context.getString(R.string.error_weeks_format)
                )
            }
            val overlapErrors = remember(context) {
                listOf(
                    context.getString(R.string.error_time_overlap_course),
                    context.getString(R.string.error_time_overlap_lesson_time)
                )
            }

            val isCourseNameError = courseNameErrors.any { errorMessage == it }
            val isLocationError = locationErrors.any { errorMessage == it }
            val isDayError = dayOfWeekErrors.any { errorMessage == it }
            val isPeriodError = periodErrors.any { errorMessage == it }
            val isWeekError = weekErrors.any { errorMessage == it }
            val isOverlapError = overlapErrors.any { errorMessage == it }

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text(courseNameLabel) },
                modifier = Modifier.fillMaxWidth(),
                isError = isCourseNameError,
                supportingText = if (isCourseNameError) {
                    { Text(stringResource(R.string.support_course_name_required)) }
                } else null
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = teacher,
                onValueChange = {
                    teacher = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text(teacherLabel) },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(R.string.support_teacher_hint)) }
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = location,
                onValueChange = {
                    location = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text(locationLabel) },
                modifier = Modifier.fillMaxWidth(),
                isError = isLocationError,
                supportingText = if (isLocationError) {
                    { Text(stringResource(R.string.support_location_length)) }
                } else null
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = dayOfWeek,
                onValueChange = {
                    dayOfWeek = it.filter { c -> c.isDigit() }
                    errorMessage = "" // 清除错误信息
                },
                label = { Text(dayOfWeekLabel) },
                modifier = Modifier.fillMaxWidth(),
                isError = isDayError,
                supportingText = if (isDayError) {
                    { Text(stringResource(R.string.support_day_of_week)) }
                } else null
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = periods,
                onValueChange = {
                    periods = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text(periodsLabel) },
                modifier = Modifier.fillMaxWidth(),
                isError = isPeriodError || isOverlapError,
                supportingText = if (isPeriodError) {
                    { Text(stringResource(R.string.support_periods)) }
                } else if (isOverlapError) {
                    { Text(errorMessage) }
                } else {
                    { Text(stringResource(R.string.support_periods_overlap)) }
                }
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = weeks,
                onValueChange = {
                    weeks = it
                    errorMessage = "" // 清除错误信息
                },
                label = { Text(weeksLabel) },
                modifier = Modifier.fillMaxWidth(),
                isError = isWeekError,
                supportingText = if (isWeekError) {
                    { Text(stringResource(R.string.support_weeks)) }
                } else {
                    { Text(stringResource(R.string.support_weeks_hint)) }
                }
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    // 数据验证
                    val validationResult = ValidationUtils.CourseValidation.validateCourseData(
                        name = name,
                        location = location,
                        dayOfWeek = dayOfWeek,
                        periods = periods,
                        weeks = weeks,
                        resources = context.resources
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
                        errorMessage = context.getString(R.string.error_time_overlap_course)
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
                            // 课程添加后立即刷新小组件
                            WidgetRefreshManager.onCourseDataChanged(context)
                            onDismiss()
                        } catch (e: Exception) {
                            Log.e("AddCourseSheet", "新增课程失败", e)
                            errorMessage = context.getString(
                                R.string.error_save_failed_with_reason,
                                e.message.orEmpty()
                            )
                        }
                    }
                }) {
                    Text(stringResource(R.string.action_save))
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
    val context = LocalContext.current

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

    // 课程时间模板
    val lessonTimeTemplates by dao.getLessonTimeTemplatesFlow().collectAsState(initial = emptyList())
    var showLessonTimeTemplateDialog by remember { mutableStateOf(false) }
    var showSaveLessonTimeTemplateDialog by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }
    var templateNameError by remember { mutableStateOf("") }
    var overwriteTemplateName by remember { mutableStateOf<String?>(null) }
    var confirmApplyTemplate by remember { mutableStateOf<LessonTimeTemplateEntity?>(null) }
    var confirmDeleteTemplate by remember { mutableStateOf<LessonTimeTemplateEntity?>(null) }
    var exportTemplate by remember { mutableStateOf<LessonTimeTemplateEntity?>(null) }
    var exportAllTemplates by remember { mutableStateOf(false) }
    var pendingImportTemplates by remember { mutableStateOf<List<LessonTimeTemplateExport>>(emptyList()) }
    var showImportTemplateConflictDialog by remember { mutableStateOf(false) }
    val templateExportJson = remember { Json { prettyPrint = true } }
    val templateImportJson = remember { Json { ignoreUnknownKeys = true; coerceInputValues = true } }

    fun sanitizeFileName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifEmpty { "lesson_time_template" }

    val exportTemplateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val template = exportTemplate
        val exportAll = exportAllTemplates
        exportTemplate = null
        exportAllTemplates = false

        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            try {
                val jsonText = withContext(Dispatchers.IO) {
                    if (exportAll) {
                        val exports = lessonTimeTemplates.map { t ->
                            val items = dao.getLessonTimeTemplateItemsOnce(t.id)
                            LessonTimeTemplateExport(
                                templateName = t.name,
                                createdAt = t.createdAt,
                                updatedAt = t.updatedAt,
                                lessonTimes = items.map { item ->
                                    LessonTimeTemplateExport.LessonTime(
                                        period = item.period,
                                        startTime = item.startTime,
                                        endTime = item.endTime
                                    )
                                }
                            )
                        }
                        val bundle = LessonTimeTemplateExportBundle(templates = exports)
                        templateExportJson.encodeToString(bundle)
                    } else {
                        requireNotNull(template) { "No template selected" }
                        val items = dao.getLessonTimeTemplateItemsOnce(template.id)
                        val exportData = LessonTimeTemplateExport(
                            templateName = template.name,
                            createdAt = template.createdAt,
                            updatedAt = template.updatedAt,
                            lessonTimes = items.map { item ->
                                LessonTimeTemplateExport.LessonTime(
                                    period = item.period,
                                    startTime = item.startTime,
                                    endTime = item.endTime
                                )
                            }
                        )
                        templateExportJson.encodeToString(exportData)
                    }
                }

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonText.toByteArray(Charsets.UTF_8))
                } ?: error("无法写入文件")

                Toast.makeText(
                    context,
                    context.getString(R.string.toast_template_exported),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_template_export_failed, e.message.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val importTemplateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            try {
                val jsonText = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        String(inputStream.readBytes(), Charsets.UTF_8)
                    } ?: error("无法读取文件")
                }

                val templates = try {
                    templateImportJson.decodeFromString(
                        LessonTimeTemplateExportBundle.serializer(),
                        jsonText
                    ).templates
                } catch (_: Exception) {
                    listOf(
                        templateImportJson.decodeFromString(
                            LessonTimeTemplateExport.serializer(),
                            jsonText
                        )
                    )
                }

                val normalized = templates.mapNotNull { t ->
                    val name = t.templateName.trim()
                    if (name.isBlank()) null else t.copy(templateName = name)
                }

                if (normalized.isEmpty()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_template_parse_none),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                pendingImportTemplates = normalized
                showImportTemplateConflictDialog = true
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_template_import_failed_reason, e.message.orEmpty()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun runTemplateImport(strategy: LessonTimeTemplateImportConflictStrategy) {
        val templatesToImport = pendingImportTemplates
        pendingImportTemplates = emptyList()
        showImportTemplateConflictDialog = false

        if (templatesToImport.isEmpty()) return

        scope.launch {
            try {
                val (imported, overwritten, skipped) = withContext(Dispatchers.IO) {
                    suspend fun findAvailableName(baseName: String): String {
                        if (dao.getLessonTimeTemplateByNameOnce(baseName) == null) return baseName

                        var index = 1
                        while (true) {
                            val candidate = if (index == 1) {
                                context.getString(
                                    R.string.template_import_rename_format,
                                    baseName
                                )
                            } else {
                                context.getString(
                                    R.string.template_import_rename_format_indexed,
                                    baseName,
                                    index
                                )
                            }
                            if (dao.getLessonTimeTemplateByNameOnce(candidate) == null) return candidate
                            index++
                        }
                    }

                    var importedCount = 0
                    var overwrittenCount = 0
                    var skippedCount = 0
                    val now = System.currentTimeMillis()

                    templatesToImport.forEach { t ->
                        val baseName = t.templateName.trim()
                        if (baseName.isBlank()) {
                            skippedCount++
                            return@forEach
                        }

                        val createdAt = t.createdAt.takeIf { it > 0 } ?: now
                        val updatedAt = t.updatedAt.takeIf { it > 0 } ?: createdAt
                        val lessonTimes = t.lessonTimes
                            .asSequence()
                            .filter { it.period > 0 && it.startTime.isNotBlank() && it.endTime.isNotBlank() }
                            .filter { it.startTime != it.endTime }
                            .sortedBy { it.period }
                            .distinctBy { it.period }
                            .map { time ->
                                LessonTimeTemplateItemEntity(
                                    templateId = 0,
                                    period = time.period,
                                    startTime = time.startTime,
                                    endTime = time.endTime
                                )
                            }
                            .toList()

                        when (strategy) {
                            LessonTimeTemplateImportConflictStrategy.OVERWRITE -> {
                                val exists = dao.getLessonTimeTemplateByNameOnce(baseName) != null
                                dao.saveLessonTimeTemplateFromItems(
                                    templateName = baseName,
                                    lessonTimes = lessonTimes,
                                    overwrite = true,
                                    createdAt = createdAt,
                                    updatedAt = updatedAt
                                )
                                importedCount++
                                if (exists) overwrittenCount++
                            }

                            LessonTimeTemplateImportConflictStrategy.RENAME -> {
                                val finalName = findAvailableName(baseName)
                                dao.saveLessonTimeTemplateFromItems(
                                    templateName = finalName,
                                    lessonTimes = lessonTimes,
                                    overwrite = false,
                                    createdAt = createdAt,
                                    updatedAt = updatedAt
                                )
                                importedCount++
                            }

                            LessonTimeTemplateImportConflictStrategy.SKIP -> {
                                if (dao.getLessonTimeTemplateByNameOnce(baseName) != null) {
                                    skippedCount++
                                    return@forEach
                                }

                                dao.saveLessonTimeTemplateFromItems(
                                    templateName = baseName,
                                    lessonTimes = lessonTimes,
                                    overwrite = false,
                                    createdAt = createdAt,
                                    updatedAt = updatedAt
                                )
                                importedCount++
                            }
                        }
                    }

                    Triple(importedCount, overwrittenCount, skippedCount)
                }

                val suffix = when (strategy) {
                    LessonTimeTemplateImportConflictStrategy.OVERWRITE ->
                        if (overwritten > 0) context.getString(
                            R.string.template_import_suffix_overwrite,
                            overwritten
                        ) else ""

                    LessonTimeTemplateImportConflictStrategy.RENAME -> ""
                    LessonTimeTemplateImportConflictStrategy.SKIP ->
                        if (skipped > 0) context.getString(
                            R.string.template_import_suffix_skip,
                            skipped
                        ) else ""
                }

                Toast.makeText(
                    context,
                    context.getString(
                        R.string.toast_template_imported,
                        imported,
                        suffix,
                        ""
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.toast_template_import_failed_reason,
                        e.message.orEmpty()
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

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

            val timetableNameErrors = remember(context) {
                listOf(
                    context.getString(R.string.error_timetable_name_required),
                    context.getString(R.string.error_timetable_name_too_long)
                )
            }
            val startDateErrors = remember(context) {
                listOf(
                    context.getString(R.string.error_semester_start_date_required),
                    context.getString(R.string.error_semester_start_year_range),
                    context.getString(R.string.error_date_format_detail)
                )
            }
            val isTimetableNameError = timetableNameErrors.any { errorMessage == it }
            val isStartDateError = startDateErrors.any { errorMessage == it }

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
                        label = { Text(stringResource(R.string.label_timetable_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        isError = isTimetableNameError,
                        supportingText = if (isTimetableNameError) {
                            { Text(stringResource(R.string.support_timetable_name_required)) }
                        } else null
                    )

                    OutlinedTextField(
                        value = startDate,
                        onValueChange = {
                            startDate = it
                            errorMessage = "" // 清除错误信息
                        },
                        label = { Text(stringResource(R.string.label_start_date)) },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { showDatePicker = true }) {
                                Icon(
                                    Icons.Rounded.CalendarMonth,
                                    contentDescription = stringResource(R.string.content_desc_select_date)
                                )
                            }
                        },
                        isError = isStartDateError,
                        supportingText = if (isStartDateError) {
                            { Text(stringResource(R.string.support_invalid_date)) }
                        } else null
                    )

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.toggle_show_weekend))
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
                        Text(stringResource(R.string.toggle_show_other_weeks))
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
                            Text(stringResource(R.string.label_row_height))
                            Text(
                                text = stringResource(
                                    R.string.row_height_value,
                                    rowHeight.toInt()
                                ),
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
                                text = stringResource(R.string.row_height_min),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = stringResource(R.string.row_height_max),
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
                            Text(stringResource(R.string.label_reminder_lead_time))
                            Text(
                                text = stringResource(
                                    R.string.reminder_minutes_value,
                                    reminderTime.toInt()
                                ),
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
                                text = stringResource(R.string.option_minutes_5),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = stringResource(R.string.option_minutes_60),
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
                                    startDate = startDate,
                                    resources = context.resources
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
                                
                                // 课表设置修改后立即刷新小组件
                                WidgetRefreshManager.onTimetableSettingsChanged(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.action_save_changes))
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
                        Text(
                            stringResource(R.string.title_course_time_management),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { showLessonTimeTemplateDialog = true }) {
                                Text(stringResource(R.string.label_template))
                            }
                            IconButton(onClick = { showAddLessonSheet = true }) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.content_desc_add_lesson_time)
                                )
                            }
                        }
                    }

                    if (sortedLessonTimes.isEmpty()) {
                        Text(
                            text = stringResource(R.string.label_no_lesson_time),
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
                                    text = stringResource(
                                        R.string.lesson_time_item,
                                        lesson.period,
                                        lesson.startTime,
                                        lesson.endTime
                                    ),
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
                                            contentDescription = stringResource(R.string.content_desc_edit_lesson_time)
                                        )
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            dao.deleteLessonTimeAutoSort(
                                                lesson
                                            )
                                            // 课程时间删除后立即刷新小组件
                                            WidgetRefreshManager.onCourseDataChanged(context)
                                        }
                                    }) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = stringResource(R.string.content_desc_delete_lesson_time)
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
                        Text(
                            stringResource(R.string.title_courses_management),
                            style = MaterialTheme.typography.titleSmall
                        )
                        IconButton(onClick = { showAddCourseSheet = true }) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.content_desc_add_course)
                            )
                        }
                    }

                    if (courses.isEmpty()) {
                        Text(
                            text = stringResource(R.string.label_no_course),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        courses.forEach { course ->
                            val teacherSuffix =
                                if (course.teacher.isNotEmpty()) stringResource(
                                    R.string.course_teacher_suffix,
                                    course.teacher
                                ) else ""
                            val periodText =
                                ValidationUtils.CourseValidation.formatNumberList(course.periods)
                            val timeBrief =
                                stringResource(R.string.course_time_brief, course.dayOfWeek, periodText)
                            val titleText = "${course.name}$teacherSuffix ($timeBrief)"
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = titleText,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.width(IntrinsicSize.Min)
                                ) {
                                    IconButton(onClick = { showEditCourseSheet = course }) {
                                        Icon(
                                            Icons.Rounded.Edit,
                                            contentDescription = stringResource(R.string.content_desc_edit_course)
                                        )
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            dao.deleteCourseWithReminders(
                                                course
                                            )
                                            // 课程删除后立即刷新小组件
                                            WidgetRefreshManager.onCourseDataChanged(context)
                                        }
                                    }) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = stringResource(R.string.content_desc_delete_course)
                                        )
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
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // 课程时间模板
    if (showLessonTimeTemplateDialog) {
        AlertDialog(
            onDismissRequest = { showLessonTimeTemplateDialog = false },
            title = { Text(stringResource(R.string.title_course_time_templates)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                if (sortedLessonTimes.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_no_lessons_to_save_template),
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()
                                    return@TextButton
                                }
                                templateName = ""
                                templateNameError = ""
                                showLessonTimeTemplateDialog = false
                                showSaveLessonTimeTemplateDialog = true
                            }
                        ) {
                            Text(stringResource(R.string.action_save_current_as_template))
                        }
                        TextButton(
                            onClick = {
                                showLessonTimeTemplateDialog = false
                                importTemplateLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            }
                        ) {
                            Text(stringResource(R.string.action_import_template))
                        }
                        TextButton(
                            onClick = {
                                if (lessonTimeTemplates.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_no_template_to_export),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@TextButton
                                }
                                exportAllTemplates = true
                                exportTemplate = null
                                exportTemplateLauncher.launch("lesson_time_templates.json")
                            }
                        ) {
                            Text(stringResource(R.string.action_export_all))
                        }
                    }

                    if (lessonTimeTemplates.isEmpty()) {
                        Text(
                            text = stringResource(R.string.label_no_template),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            itemsIndexed(
                                items = lessonTimeTemplates,
                                key = { _, item -> item.id }
                            ) { _, template ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = template.name,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(modifier = Modifier.width(IntrinsicSize.Min)) {
                                        IconButton(
                                            onClick = {
                                                confirmApplyTemplate = template
                                                showLessonTimeTemplateDialog = false
                                            }
                                        ) {
                                            Icon(
                                                Icons.Rounded.FileDownload,
                                                contentDescription = stringResource(R.string.content_desc_apply_template)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                exportTemplate = template
                                                exportAllTemplates = false
                                                showLessonTimeTemplateDialog = false
                                                exportTemplateLauncher.launch("${sanitizeFileName(template.name)}.json")
                                            }
                                        ) {
                                            Icon(
                                                Icons.Rounded.Polyline,
                                                contentDescription = stringResource(R.string.content_desc_export_template)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                confirmDeleteTemplate = template
                                                showLessonTimeTemplateDialog = false
                                            }
                                        ) {
                                            Icon(
                                                Icons.Rounded.Delete,
                                                contentDescription = stringResource(R.string.content_desc_delete_template)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLessonTimeTemplateDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    if (showImportTemplateConflictDialog) {
        AlertDialog(
            onDismissRequest = {
                showImportTemplateConflictDialog = false
                pendingImportTemplates = emptyList()
            },
            title = { Text(stringResource(R.string.title_import_template)) },
            text = {
                Text(
                    stringResource(
                        R.string.import_template_parsed_count,
                        pendingImportTemplates.size
                    )
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { runTemplateImport(LessonTimeTemplateImportConflictStrategy.OVERWRITE) }) {
                        Text(stringResource(R.string.action_overwrite))
                    }
                    TextButton(onClick = { runTemplateImport(LessonTimeTemplateImportConflictStrategy.RENAME) }) {
                        Text(stringResource(R.string.action_rename))
                    }
                    TextButton(onClick = { runTemplateImport(LessonTimeTemplateImportConflictStrategy.SKIP) }) {
                        Text(stringResource(R.string.action_skip))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportTemplateConflictDialog = false
                        pendingImportTemplates = emptyList()
                    }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showSaveLessonTimeTemplateDialog) {
        AlertDialog(
            onDismissRequest = { showSaveLessonTimeTemplateDialog = false },
            title = { Text(stringResource(R.string.title_save_template)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = {
                            templateName = it
                            templateNameError = ""
                        },
                        label = { Text(stringResource(R.string.label_template_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        isError = templateNameError.isNotEmpty(),
                        supportingText = if (templateNameError.isNotEmpty()) {
                            { Text(templateNameError) }
                        } else null
                    )
                    Text(
                        text = stringResource(R.string.save_template_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val nameToSave = templateName.trim()
                        if (nameToSave.isBlank()) {
                            templateNameError = context.getString(R.string.error_template_name_empty)
                            return@TextButton
                        }
                        if (nameToSave.length > 100) {
                            templateNameError = context.getString(R.string.error_template_name_too_long)
                            return@TextButton
                        }

                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    dao.saveLessonTimeTemplateFromTimetable(
                                        timetableId = timetable.id,
                                        templateName = nameToSave,
                                        overwrite = false
                                    )
                                }
                                showSaveLessonTimeTemplateDialog = false
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_template_saved),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: IllegalStateException) {
                                if (e.message == "TEMPLATE_EXISTS") {
                                    showSaveLessonTimeTemplateDialog = false
                                    overwriteTemplateName = nameToSave
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.toast_template_save_failed,
                                            e.message.orEmpty()
                                        ),
                                        Toast.LENGTH_SHORT
                                    )
                                        .show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.toast_template_save_failed,
                                        e.message.orEmpty()
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveLessonTimeTemplateDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    overwriteTemplateName?.let { nameToOverwrite ->
        AlertDialog(
            onDismissRequest = { overwriteTemplateName = null },
            title = { Text(stringResource(R.string.title_overwrite_template)) },
            text = {
                Text(
                    stringResource(
                        R.string.text_overwrite_template,
                        nameToOverwrite
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    dao.saveLessonTimeTemplateFromTimetable(
                                        timetableId = timetable.id,
                                    templateName = nameToOverwrite,
                                    overwrite = true
                                )
                                }
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_template_overwritten),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.toast_template_overwrite_failed,
                                        e.message.orEmpty()
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                overwriteTemplateName = null
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_overwrite))
                }
            },
            dismissButton = {
                TextButton(onClick = { overwriteTemplateName = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    confirmApplyTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { confirmApplyTemplate = null },
            title = { Text(stringResource(R.string.title_apply_template)) },
            text = {
                Text(
                    stringResource(
                        R.string.text_apply_template,
                        template.name
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    dao.applyLessonTimeTemplateToTimetable(
                                        timetableId = timetable.id,
                                        templateId = template.id
                                    )
                                }
                                WidgetRefreshManager.onCourseDataChanged(context)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_template_applied),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.toast_template_apply_failed,
                                        e.message.orEmpty()
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                confirmApplyTemplate = null
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmApplyTemplate = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    confirmDeleteTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { confirmDeleteTemplate = null },
            title = { Text(stringResource(R.string.title_delete_template)) },
            text = {
                Text(
                    stringResource(
                        R.string.text_delete_template,
                        template.name
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    dao.deleteLessonTimeTemplate(template)
                                }
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_template_deleted),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.toast_template_delete_failed,
                                        e.message.orEmpty()
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } finally {
                                confirmDeleteTemplate = null
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteTemplate = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
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
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
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
    onQiangzhiImport: () -> Unit,
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
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.import_options_title),
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
                        contentDescription = stringResource(R.string.import_wakeup_title),
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.import_wakeup_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.import_wakeup_subtitle),
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
                        contentDescription = stringResource(R.string.import_xuexitong_content_description),
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.import_xls_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.import_xls_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 强智教务系统导入选项
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onQiangzhiImport() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.CalendarMonth,
                        contentDescription = stringResource(R.string.import_qiangzhi_title),
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.import_qiangzhi_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.import_qiangzhi_subtitle),
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
                text = stringResource(R.string.import_more_coming),
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
    val context = LocalContext.current

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
                text = stringResource(R.string.wakeup_import_title),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = stringResource(R.string.wakeup_import_instruction),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 示例分享口令
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.wakeup_import_example),
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
                label = { Text(stringResource(R.string.wakeup_share_code_label)) },
                modifier = Modifier.fillMaxWidth(),
                isError = errorMessage.isNotEmpty(),
                supportingText = {
                    if (errorMessage.isNotEmpty()) {
                        Text(errorMessage)
                    } else {
                        Text(stringResource(R.string.wakeup_share_code_supporting))
                    }
                },
                placeholder = { Text(stringResource(R.string.wakeup_share_code_placeholder)) }
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
                            errorMessage = context.getString(R.string.wakeup_error_empty_code)
                            return@Button
                        }

                        // 提取口令内容
                        val key = extractKeyFromShareText(shareText)
                        if (key.isBlank()) {
                            errorMessage = context.getString(R.string.wakeup_error_invalid_code)
                            return@Button
                        }

                        isLoading = true
                        scope.launch {
                            try {
                                // 调用WakeUp API导入课表
                                val result = importFromWakeUp(key, dao, context)
                                if (result) {
                                    onDismiss()
                                } else {
                                    errorMessage =
                                        context.getString(R.string.wakeup_error_import_failed)
                                }
                            } catch (e: Exception) {
                                errorMessage = context.getString(
                                    R.string.wakeup_error_import_failed_reason,
                                    e.message.orEmpty()
                                )
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text(stringResource(R.string.action_import))
                }
            }
        }
    }
}

// ---------- 强智教务系统导入弹窗 ----------
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QiangzhiImportSheet(
    onDismiss: () -> Unit,
    dao: ScheduleDao,
    sheetState: androidx.compose.material3.SheetState
) {
    var baseUrl by remember { mutableStateOf(QiangzhiJwImporter.EXAMPLE_BASE_URL) }
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val config = withContext(Dispatchers.IO) {
            dao.getAllTimetablesOnce()
                .asSequence()
                .mapNotNull { timetable -> TimetableAutoUpdateJson.decode(timetable.autoUpdateJson) }
                .firstOrNull { it is QiangzhiJwAutoUpdateConfig }
        }
        when (config) {
            is QiangzhiJwAutoUpdateConfig -> {
                baseUrl = config.baseUrl
            }

            null -> Unit
        }
    }

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
                text = stringResource(R.string.qiangzhi_import_title),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        R.string.qiangzhi_info_text,
                        QiangzhiJwImporter.EXAMPLE_BASE_URL
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // 错误信息显示
            if (errorMessage.isNotEmpty()) {
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
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = {
                    baseUrl = it
                    errorMessage = ""
                },
                label = { Text(stringResource(R.string.label_base_url)) },
                placeholder = { Text(QiangzhiJwImporter.EXAMPLE_BASE_URL) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = account,
                onValueChange = {
                    account = it
                    errorMessage = ""
                },
                label = { Text(stringResource(R.string.label_account)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMessage = ""
                },
                label = { Text(stringResource(R.string.label_password)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
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
                        val normalizedBaseUrl = baseUrl.trim().removeSuffix("/")
                        if (normalizedBaseUrl.isBlank()) {
                            errorMessage = context.getString(R.string.qiangzhi_error_empty_url)
                            return@Button
                        }
                        if (!normalizedBaseUrl.startsWith("http://") && !normalizedBaseUrl.startsWith("https://")) {
                            errorMessage = context.getString(R.string.qiangzhi_error_invalid_url)
                            return@Button
                        }

                        val trimmedAccount = account.trim()
                        if (trimmedAccount.isBlank() || password.isBlank()) {
                            errorMessage =
                                context.getString(R.string.qiangzhi_error_empty_credentials)
                            return@Button
                        }

                        isLoading = true
                        scope.launch {
                            try {
                                when (val result =
                                    QiangzhiJwImporter.importFromQiangzhiJw(
                                        baseUrl = normalizedBaseUrl,
                                        account = trimmedAccount,
                                        password = password,
                                        dao = dao
                                    )) {
                                    is QiangzhiJwImporter.ImportResult.Success -> {
                                        result.warning?.let {
                                            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                                        }
                                        WidgetRefreshManager.onTimetableSwitched(context)
                                        onDismiss()
                                    }

                                    is QiangzhiJwImporter.ImportResult.Error -> {
                                        errorMessage = result.message
                                    }
                                }
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text(stringResource(R.string.action_login_and_import))
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
suspend fun importFromWakeUp(key: String, dao: ScheduleDao, context: Context): Boolean = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("https://i.wakeup.fun/share_schedule/get?key=$key")
            .get()
            .addHeader("User-Agent", "StarSchedule/1.0")
            .build()
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            Log.e("WakeUpImport", "请求失败: ${response.code} - ${response.message}")
            return@withContext false
        }
        
        val body = response.body?.string() ?: run {
            Log.e("WakeUpImport", "响应体为空")
            return@withContext false
        }

        val rootJson = Json.parseToJsonElement(body).jsonObject
        if (rootJson["status"]?.jsonPrimitive?.int != 1) {
            Log.e("WakeUpImport", "API返回错误状态: ${rootJson["status"]}")
            return@withContext false
        }

        val dataStr = rootJson["data"]?.jsonPrimitive?.content ?: run {
            Log.e("WakeUpImport", "API返回数据为空")
            return@withContext false
        }
        val segments = dataStr.split("\n")
        if (segments.size < 4) {
            Log.e("WakeUpImport", "API返回数据格式错误，段数: ${segments.size}")
            return@withContext false
        }

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
                name = configInfo["tableName"]?.jsonPrimitive?.content
                    ?: context.getString(R.string.wakeup_default_timetable_name),
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
        Log.e("WakeUpImport", "导入失败", e)
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
                fileName = getFileNameFromUri(context, it)
                    ?: context.getString(R.string.label_selected_file_unknown)
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
                text = stringResource(R.string.import_xls_title),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = stringResource(R.string.import_xls_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 文件选择说明
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.import_xls_instruction),
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
                    Icon(
                        Icons.Rounded.FileOpen,
                        contentDescription = stringResource(R.string.content_desc_select_file)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.import_xls_title))
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
                        Icon(
                            Icons.Rounded.FileOpen,
                            contentDescription = stringResource(R.string.content_desc_selected_file)
                        )
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
                            errorMessage = context.getString(R.string.error_select_xls_first)
                            return@Button
                        }

                        isLoading = true
                        scope.launch {
                            try {
                                // 调用导入函数
                                val result = importTimetable(selectedFileUri!!, context, dao)
                                if (result) {
                                    // 课表导入成功后立即刷新小组件
                                    WidgetRefreshManager.onTimetableSwitched(context)
                                    onDismiss()
                                } else {
                                    errorMessage =
                                        context.getString(R.string.error_import_failed_generic)
                                }
                            } catch (e: Exception) {
                                errorMessage = context.getString(
                                    R.string.error_import_failed_reason,
                                    e.message.orEmpty()
                                )
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && selectedFileUri != null
                ) {
                    Text(stringResource(R.string.action_import))
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
