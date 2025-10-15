package com.star.schedule.ui.layouts

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LooksOne
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.github.skydoves.colorpicker.compose.ColorPickerController
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.star.schedule.R
import com.star.schedule.db.ScheduleDao
import com.star.schedule.notification.UnifiedNotificationManager
import com.star.schedule.ui.components.OptimizedBottomSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.graphics.toColorInt
import com.star.schedule.Constants

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun Settings(context: Activity, dao: ScheduleDao, notificationManager: UnifiedNotificationManager) {
    val haptic = LocalHapticFeedback.current
    var clickCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var resetJob by remember { mutableStateOf<Job?>(null) }
    val scrollState = rememberScrollState()

    // 权限申请器
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )

    // 所有课表
    val timetables by dao.getAllTimetables().collectAsState(initial = emptyList())
    val currentTimetableIdPref by dao.getPreferenceFlow(Constants.PREF_CURRENT_TIMETABLE)
        .collectAsState(initial = null)

    // 当前课表ID
    var currentTimetableId by remember { mutableStateOf<Long?>(null) }

    // 课前提醒开关状态
    var reminderEnabled by remember { mutableStateOf(false) }

    // 只在连续课程的第一节课前发送通知的开关状态
    var notifyOnlyForFirstContinuousClass by remember { mutableStateOf(false) }


    // 控制 BottomSheet 显示
    var showTimetableSheet by remember { mutableStateOf(false) }
    val timetableSheetState = rememberModalBottomSheetState()

    var showStartupHint by remember { mutableStateOf(false) }

    val startupHintClosedPref by dao.getPreferenceFlow("startup_hint_closed")
        .collectAsState(initial = "false")

    // 只在连续课程的第一节课前发送通知的偏好设置
    val notifyOnlyForFirstContinuousClassPref by dao.getPreferenceFlow(Constants.PREF_NOTIFY_ONLY_FOR_FIRST_CONTINUOUS_CLASS)
        .collectAsState(initial = "false")
    LaunchedEffect(startupHintClosedPref) {
        showStartupHint = startupHintClosedPref != "true"
    }

    // 同步"只在连续课程的第一节课前发送通知"的偏好设置
    LaunchedEffect(notifyOnlyForFirstContinuousClassPref) {
        notifyOnlyForFirstContinuousClass = notifyOnlyForFirstContinuousClassPref == "true"
    }

    // 权限申请辅助函数
    fun requestNotificationPermissionIfNeeded(onPermissionGranted: () -> Unit) {
        when (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)) {
            PackageManager.PERMISSION_GRANTED -> {
                when {
                    context.getSystemService<AlarmManager>()!!.canScheduleExactAlarms() -> {
                        onPermissionGranted()
                    }

                    else -> {
                        val intent =
                            Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = "package:${context.packageName}".toUri()
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        context.startActivity(intent)

                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(
                                context,
                                "请允许应用使用精确闹钟，然后重试",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }

            else -> {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // 保证 currentTimetableId 与 preference 和 timetables 同步
    LaunchedEffect(timetables, currentTimetableIdPref) {
        val newTimetableId = currentTimetableIdPref?.toLongOrNull()

        // 如果课表切换了，需要关闭之前课表的提醒
        if (currentTimetableId != null && newTimetableId != currentTimetableId) {
            // 课表切换时自动关闭提醒
            notificationManager.disableReminders()
            reminderEnabled = false
        }

        currentTimetableId = newTimetableId

        // 检查当前课表是否启用了提醒
        reminderEnabled = if (newTimetableId != null) {
            notificationManager.isReminderEnabledForTimetableSync(newTimetableId)
        } else {
            false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp),
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                showTimetableSheet = true
            },
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(12.dp))
                val currentName =
                    timetables.firstOrNull { it.id == currentTimetableId }?.name ?: "未选择"
                Text(
                    text = "当前课表: $currentName",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }


        // 课表选择 BottomSheet
        if (showTimetableSheet) {
            OptimizedBottomSheet(
                onDismiss = { showTimetableSheet = false },
                sheetState = timetableSheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding()
                ) {
                    Text(
                        text = "选择课表",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        items(timetables) { timetable ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    scope.launch {
                                        dao.setPreference(
                                            Constants.PREF_CURRENT_TIMETABLE,
                                            timetable.id.toString()
                                        )
                                    }
//                                    hideBottomSheet(timetableSheetState, scope) {
//                                        showTimetableSheet = false
//                                    }
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (timetable.id == currentTimetableId)
                                        MaterialTheme.colorScheme.secondary
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.CalendarMonth,
                                        contentDescription = "课表",
                                        modifier = Modifier.padding(end = 12.dp),
                                        tint = if (timetable.id == currentTimetableId)
                                            MaterialTheme.colorScheme.onSecondary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Column {
                                        Text(
                                            text = timetable.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (timetable.id == currentTimetableId)
                                                MaterialTheme.colorScheme.onSecondary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (timetable.id == currentTimetableId) {
                                            Text(
                                                text = "当前课表",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        AnimatedContent(
            targetState = showStartupHint,
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn()).togetherWith(fadeOut(tween(300)) + scaleOut())
            }
        ) { show ->
            if (show) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = "提示",
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "请允许开机自启和后台运行",
                                style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSecondary)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "软件不会常驻后台，仅在需要发送通知时被系统唤起。",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.8f)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = {
                            scope.launch {
                                dao.setPreference("startup_hint_closed", "true")
                                showStartupHint = false
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "关闭提示",
                                tint = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    }
                }
            }
        }

        // 课前提醒开关
        ListItem(
            headlineContent = { Text("课前提醒") },
            supportingContent = {
                val currentTimetable = timetables.firstOrNull { it.id == currentTimetableId }
                val currentName = currentTimetable?.name ?: "未选择课表"
                val reminderTime = currentTimetable?.reminderTime ?: 15
                Text("为当前课表（$currentName）开启课前${reminderTime}分钟提醒")
            },
            leadingContent = {
                AnimatedContent(
                    targetState = reminderEnabled,
                    transitionSpec = {
                        (scaleIn(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow
                            ),
                            initialScale = 0.8f
                        ) + fadeIn() togetherWith
                                scaleOut(
                                    animationSpec = tween(100),
                                    targetScale = 0.8f
                                ) + fadeOut())
                    }, label = "NotificationIcon"
                ) { enabled ->
                    Icon(
                        imageVector = if (enabled) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            trailingContent = {
                    Switch(
                        checked = reminderEnabled,
                        enabled = currentTimetableId != null,
                        onCheckedChange = { checked ->
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            if (checked && currentTimetableId != null) {
                                requestNotificationPermissionIfNeeded {
                                    scope.launch {
                                        notificationManager.enableRemindersForTimetable(
                                            currentTimetableId!!
                                        )
                                        reminderEnabled = true
                                    }
                                }
                            } else {
                                scope.launch {
                                    notificationManager.disableReminders()
                                    reminderEnabled = false
                                }
                            }
                        }
                    )
            }
        )

        // 通知设置项 - 作为一个整体一起出现
        AnimatedVisibility(
            visible = reminderEnabled,
            enter = expandVertically(
                animationSpec = tween(durationMillis = 300),
                expandFrom = Alignment.Top
            ) + fadeIn(
                animationSpec = tween(durationMillis = 200)
            ),
            exit = shrinkVertically(
                animationSpec = tween(durationMillis = 300),
                shrinkTowards = Alignment.Top
            ) + fadeOut(
                animationSpec = tween(durationMillis = 200)
            )
        ) {
            Column {
                // 只在连续课程的第一节课前发送通知的开关
                ListItem(
                    headlineContent = { Text("仅第一节连续课程提醒") },
                    supportingContent = { Text("对于连续的课程，只在第一节课前发送通知") },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.LooksOne,
                            contentDescription = null
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = notifyOnlyForFirstContinuousClass,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    dao.setPreference(
                                        Constants.PREF_NOTIFY_ONLY_FOR_FIRST_CONTINUOUS_CLASS,
                                        enabled.toString()
                                    )
                                    // 重新设置提醒以应用新的设置
                                    if (currentTimetableId != null) {
                                        notificationManager.enableRemindersForTimetable(
                                            currentTimetableId!!
                                        )
                                    }
                                }
                            }
                        )
                    }
                )

                // 实况通知胶囊背景颜色设置
                var showColorPicker by remember { mutableStateOf(false) }
                val liveCapsuleBgColorPref by dao.getPreferenceFlow(Constants.PREF_LIVE_CAPSULE_BG_COLOR)
          .collectAsState(initial = "#FFE082")
                val defaultColor = Color(0xFFFFE082)
                var selectedColor by remember {
                    mutableStateOf(
                        try {
                            liveCapsuleBgColorPref?.let { Color(it.toColorInt()) } ?: defaultColor
                        } catch (_: Exception) {
                            defaultColor
                        }
                    )
                }
                val savedColor = try {
                    liveCapsuleBgColorPref?.let { Color(it.toColorInt()) } ?: defaultColor
                } catch (_: Exception) {
                    defaultColor
                }


                ListItem(
                    headlineContent = { Text("实况通知胶囊背景颜色") },
                    supportingContent = { Text("自定义实况通知胶囊的背景颜色") },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.ColorLens,
                            contentDescription = null
                        )
                    },
                    trailingContent = {
                        val modifier = Modifier
                            .width(48.dp)
                            .height(24.dp)
                            .background(
                                color = savedColor,
                                shape = RoundedCornerShape(6.dp)
                            )
                        Box(
                            modifier = modifier
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(6.dp)
                                )
                        )
                    },
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        showColorPicker = true
                    }
                )

                // 颜色选择器BottomSheet
                if (showColorPicker) {
                    val colorPickerController = remember { ColorPickerController() }
                    val colorPickerSheetState =
                        rememberModalBottomSheetState(skipPartiallyExpanded = true)

                    OptimizedBottomSheet(
                        sheetState = colorPickerSheetState,
                        onDismiss = {
                            scope.launch {
                                colorPickerSheetState.hide()
                            }.invokeOnCompletion {
                                if (!colorPickerSheetState.isVisible) {
                                    showColorPicker = false
                                }
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .navigationBarsPadding()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "选择颜色",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            fun autoContentColorFor(background: Color): Color {
                                return if (background.luminance() > 0.7f) Color.Black else Color.White
                            }
                            // 胶囊预览卡片
                            selectedColor.let {
                                Card(
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .align(Alignment.CenterHorizontally)
                                        .padding(bottom = 16.dp),
                                    shape = RoundedCornerShape(50),
                                    colors = CardDefaults.cardColors(containerColor = it)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_notification),
                                            contentDescription = null,
                                            tint = autoContentColorFor(selectedColor),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "测试内容",
                                            color = autoContentColorFor(selectedColor),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }


                            HsvColorPicker(
                                modifier = Modifier
                                    .size(300.dp)
                                    .align(Alignment.CenterHorizontally),
                                controller = colorPickerController,
                                initialColor = savedColor,
                                onColorChanged = { colorEnvelope ->
                                    selectedColor = colorEnvelope.color
                                }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // 按钮区域 - 右对齐
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                              .padding(end = 8.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                // 取消按钮 - 无边框样式
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            colorPickerSheetState.hide()
                                        }.invokeOnCompletion {
                                            if (!colorPickerSheetState.isVisible) {
                                                showColorPicker = false
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text("取消")
                                }

                                // 确定按钮 - 固定样式
                                Button(
                                    onClick = {
                                        val colorHex = "#${Integer.toHexString(selectedColor.toArgb()).substring(2).uppercase()}"
                                        scope.launch {
                                            dao.setPreference(
                                                Constants.PREF_LIVE_CAPSULE_BG_COLOR,
                                                colorHex
                                            )
                                            colorPickerSheetState.hide()
                                        }.invokeOnCompletion {
                                            if (!colorPickerSheetState.isVisible) {
                                                showColorPicker = false
                                            }
                                        }
                                    }
                                ) {
                                    Text("确定")
                                }
                            }
                        }
                    }
                }

                // 通知测试项
                ListItem(
                    headlineContent = { Text("通知测试") },
                    supportingContent = { Text("测试通知功能（即时）") },
                    leadingContent = { Icon(Icons.Rounded.Science, contentDescription = null) },
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        requestNotificationPermissionIfNeeded {
                            scope.launch {
                                notificationManager.sendTestNotification()
                            }
                        }
                    }
                )

                ListItem(
                    headlineContent = { Text("通知测试") },
                    supportingContent = { Text("测试通知功能（延迟）") },
                    leadingContent = { Icon(Icons.Rounded.Science, contentDescription = null) },
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        requestNotificationPermissionIfNeeded {
                            scope.launch {
                                notificationManager.scheduleTestReminder()
                            }
                        }
                    }
                )
            }
        }

        ListItem(
            headlineContent = { Text("关于应用") },
            supportingContent = {
                Text(
                    "版本 " + context.packageManager.getPackageInfo(
                        context.packageName,
                        0
                    ).versionName
                )
            },
            leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
            modifier = Modifier.clickable {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                clickCount++
                if (clickCount == 5) {
                    val mediaPlayer = MediaPlayer.create(context, R.raw.egg)
                    mediaPlayer.start()
                    mediaPlayer.setOnCompletionListener { it.release() }
                    clickCount = 0
                    resetJob?.cancel()
                } else {
                    resetJob?.cancel()
                    resetJob = scope.launch {
                        delay(2000)
                        clickCount = 0
                    }
                }
            }
        )

        ListItem(
            headlineContent = { Text("Github") },
            supportingContent = { Text("https://github.com/lightStarrr/starSchedule") },
            leadingContent = { Icon(Icons.Rounded.Code, contentDescription = null) },
            modifier = Modifier.clickable {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://github.com/lightStarrr/starSchedule".toUri()
                    )
                context.startActivity(intent)
            }
        )

        ListItem(
            headlineContent = { Text("QQ群聊") },
            supportingContent = { Text("947574953") },
            leadingContent = { Icon(Icons.Rounded.Group, contentDescription = null) },
            modifier = Modifier.clickable {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                val intent =
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://qun.qq.com/universal-share/share?ac=1&authKey=vkf2HO4ASBIuSUo58JkyisXvAH3O2ahAWe8WCZhNtWb7naUMzjEaLdmFzqMq%2B1c9&busi_data=eyJncm91cENvZGUiOiI5NDc1NzQ5NTMiLCJ0b2tlbiI6ImZlZ2k0bjV0RXRmMVphaEZDNDFPZkVHSmFZSzMxMUErRExWVXp0M2k4cHR2RmthaTdaR3JwR2dVL3Q1RWFIZ2oiLCJ1aW4iOiIyNzMzOTc3OTMyIn0%3D&data=NkI03UL5UEBOZSjmEjCZ1XOX_FMW7sODMR0NVcuFpi5n-wd8cRrJzpDlKmpZ63I8SJ8U2_9S81TBshw62OIXcQ&svctype=4&tempid=h5_group_info".toUri()
                    )
                context.startActivity(intent)
            }
        )
    }
}
