package com.star.schedule.ui.layouts

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LooksOne
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.github.skydoves.colorpicker.compose.ColorPickerController
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.star.schedule.R
import com.star.schedule.db.ScheduleDao
import com.star.schedule.notification.UnifiedNotificationManager
import com.star.schedule.notification.FlymeLiveTemplate
import com.star.schedule.ui.components.OptimizedBottomSheet
import com.star.schedule.ui.viewmodel.SettingsViewModel
import com.star.schedule.ui.viewmodel.SettingsViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.graphics.toColorInt
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import androidx.lifecycle.viewmodel.compose.viewModel
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.graphics.ColorFilter
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun Settings(context: Activity, dao: ScheduleDao, notificationManager: UnifiedNotificationManager) {
    val viewModel: SettingsViewModel = viewModel(
        factory = remember(dao, notificationManager) {
            SettingsViewModelFactory(dao, notificationManager)
        }
    )
    val haptic = LocalHapticFeedback.current
    var clickCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var resetJob by remember { mutableStateOf<Job?>(null) }
    val scrollState = rememberScrollState()
    val showLiveCapsuleSetting = viewModel.isLiveCapsuleCustomizationAvailable
    var reminderAnimationsReady by remember { mutableStateOf(false) }
    var startupHintAnimationsReady by remember { mutableStateOf(false) }

    // 权限申请器
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {}
    )

    val timetables by viewModel.timetables.collectAsState()
    val currentTimetableId by viewModel.currentTimetableId.collectAsState()
    val reminderEnabled by viewModel.reminderEnabled.collectAsState()
    val notifyOnlyForFirstContinuousClass by viewModel.notifyOnlyForFirstContinuousClass.collectAsState()
    val hideFromRecents by viewModel.hideFromRecents.collectAsState()
    val startupHintClosed by viewModel.startupHintClosed.collectAsState()
    val liveCapsuleBgColorPref by viewModel.liveCapsuleBgColor.collectAsState()
    val liveNotificationTemplate by viewModel.liveNotificationTemplate.collectAsState()
    val liveCapsuleIconPath by viewModel.liveCapsuleIconPath.collectAsState()
    var liveCapsuleIconBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // 控制 BottomSheet 显示
    var showTimetableSheet by remember { mutableStateOf(false) }
    val timetableSheetState = rememberModalBottomSheetState()

    var showStartupHint by remember { mutableStateOf(false) }
    var previousTimetableId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(startupHintClosed) {
        showStartupHint = !startupHintClosed
    }

    LaunchedEffect(showStartupHint) {
        if (showStartupHint && !startupHintAnimationsReady) {
            withFrameNanos { }
            startupHintAnimationsReady = true
        }
    }

    LaunchedEffect(timetables, currentTimetableId) {
        val newTimetableId = currentTimetableId
        if (previousTimetableId != null && newTimetableId != previousTimetableId) {
            viewModel.disableReminders()
            com.star.schedule.service.WidgetRefreshManager.onTimetableSwitched(context)
        }
        if (!reminderAnimationsReady) {
            withFrameNanos { }
            reminderAnimationsReady = true
        }
    }

    LaunchedEffect(liveCapsuleIconPath) {
        val loaded: Bitmap? = withContext(Dispatchers.IO) {
            liveCapsuleIconPath?.let { path ->
                runCatching {
                    val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                    BitmapFactory.decodeFile(path, options)?.let { decoded ->
                        val targetSize = (128 * context.resources.displayMetrics.density).toInt().coerceAtLeast(64)
                        scaleLiveIcon(decoded, targetSize)
                    }
                }.getOrNull()
            }
        }
        liveCapsuleIconBitmap = loaded
    }

    // 权限申请辅助函数
    fun requestNotificationPermissionIfNeeded(onPermissionGranted: () -> Unit) {
        when (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)) {
            PackageManager.PERMISSION_GRANTED -> {
                // Step 1: 检查精确闹钟权限
                val alarmManager = context.getSystemService<AlarmManager>()
                if (alarmManager?.canScheduleExactAlarms() != true) {
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
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
                    return
                }

                // ✅ Step 2: 检查是否支持实况通知（Android 16+）
                if (Build.VERSION.SDK_INT >= 36 && !Build.MANUFACTURER.equals("Xiaomi") && !Build.MANUFACTURER.equals("meizu")) {
                    val nm = context.getSystemService(android.app.NotificationManager::class.java)
                    if (!nm.canPostPromotedNotifications()) {
                        try {
                            val intent = Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS").apply {
                                data = "package:${context.packageName}".toUri()
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {

                            val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                        CoroutineScope(Dispatchers.Main).launch {
                            Toast.makeText(
                                context,
                                "请允许应用使用实况通知，然后重试，如已允许请提issues",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        return
                    }
                }

                // ✅ 所有权限都满足
                onPermissionGranted()
            }

            else -> {
                // 请求通知权限
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    suspend fun saveLiveIconToLocal(uri: Uri): Result<Pair<String, Bitmap>> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
                val original = BitmapFactory.decodeStream(input, null, options)
                    ?: throw IllegalStateException("无法读取图片")
                val targetSize = (256 * context.resources.displayMetrics.density).toInt().coerceAtLeast(64)
                val scaled = scaleLiveIcon(original, targetSize)
                val iconDir = File(context.filesDir, "live_icons").apply { mkdirs() }
                val iconFile = File(iconDir, "custom_live_icon.png")
                FileOutputStream(iconFile).use { out ->
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Pair(iconFile.absolutePath, scaled)
            } ?: throw IllegalStateException("无法打开图片")
        }
    }

    fun deleteSavedLiveIcon(path: String?) {
        path?.let { stored ->
            runCatching { File(stored).takeIf { it.exists() }?.delete() }
        }
    }

    val liveIconPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri ?: return@rememberLauncherForActivityResult
            scope.launch {
                val saveResult = saveLiveIconToLocal(uri)
                saveResult.onSuccess { (path, preview) ->
                    viewModel.updateLiveCapsuleIconPath(path)
                    liveCapsuleIconBitmap = preview
                    Toast.makeText(context, "已更新实况通知图标", Toast.LENGTH_SHORT).show()
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        "更新图标失败: ${error.message ?: "未知错误"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    )


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
                                    viewModel.selectTimetable(timetable.id)
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

        val startupHintCard: @Composable () -> Unit = {
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
                        viewModel.closeStartupHint()
                        showStartupHint = false
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

        if (!startupHintAnimationsReady) {
            if (showStartupHint) {
                startupHintCard()
            }
        } else {
            AnimatedContent(
                targetState = showStartupHint,
                transitionSpec = {
                    (fadeIn(tween(300)) + scaleIn()).togetherWith(fadeOut(tween(300)) + scaleOut())
                }
            ) { show ->
                if (show) {
                    startupHintCard()
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
                        if (reminderAnimationsReady) {
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
                        } else {
                            (EnterTransition.None).togetherWith(ExitTransition.None)
                        }
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
                                    viewModel.enableRemindersForCurrentTimetable()
                                }
                            } else {
                                viewModel.disableReminders()
                            }
                        }
                    )
            }
        )

        // 通知设置项 - 作为一个整体一起出现
        val reminderEnterTransition =
            if (reminderAnimationsReady) {
                expandVertically(
                    animationSpec = tween(durationMillis = 300),
                    expandFrom = Alignment.Top
                ) + fadeIn(
                    animationSpec = tween(durationMillis = 200)
                )
            } else {
                EnterTransition.None
            }
        val reminderExitTransition =
            if (reminderAnimationsReady) {
                shrinkVertically(
                    animationSpec = tween(durationMillis = 300),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(
                    animationSpec = tween(durationMillis = 200)
                )
            } else {
                ExitTransition.None
            }
        AnimatedVisibility(
            visible = reminderEnabled,
            enter = reminderEnterTransition,
            exit = reminderExitTransition
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
                                viewModel.setNotifyOnlyForFirstContinuousClass(enabled)
                            }
                        )
                    }
                )

                ListItem(
                    headlineContent = { Text("通知图标") },
                    supportingContent = {
                        Text(
                            if (liveCapsuleIconPath.isNullOrEmpty()) "点击上传自定义图标"
                            else "已设置自定义图标，点击更换或清除"
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Notifications,
                            contentDescription = null
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            liveCapsuleIconBitmap?.let { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                                )
                            } ?: Icon(
                                painter = painterResource(id = R.drawable.ic_notification),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            if (!liveCapsuleIconPath.isNullOrEmpty()) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        deleteSavedLiveIcon(liveCapsuleIconPath)
                                        viewModel.clearLiveCapsuleIcon()
                                        liveCapsuleIconBitmap = null
                                    }
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "恢复默认")
                                }
                            }
                        }
                    },
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                        liveIconPickerLauncher.launch(arrayOf("image/*"))
                    }
                )

                if (showLiveCapsuleSetting) {
                    // 实况通知胶囊背景颜色设置（Flyme 特有）
                    var showColorPicker by remember { mutableStateOf(false) }
                    var showTemplatePicker by remember { mutableStateOf(false) }
                    val defaultColor = Color(0xFFFFE082)
                    val savedColor = remember(liveCapsuleBgColorPref) {
                        try {
                            liveCapsuleBgColorPref?.let { Color(it.toColorInt()) } ?: defaultColor
                        } catch (_: Exception) {
                            defaultColor
                        }
                    }
                    var selectedColor by remember(liveCapsuleBgColorPref) {
                        mutableStateOf(savedColor)
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
                                Card(
                                    modifier = Modifier
                                        .wrapContentWidth()
                                        .align(Alignment.CenterHorizontally)
                                        .padding(bottom = 16.dp),
                                    shape = RoundedCornerShape(50),
                                    colors = CardDefaults.cardColors(containerColor = selectedColor)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                        liveCapsuleIconBitmap?.let { bitmap ->
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(RoundedCornerShape(4.dp)),
                                                contentScale = ContentScale.Crop,
                                                colorFilter = ColorFilter.tint(autoContentColorFor(selectedColor))
                                            )
                                        } ?: Icon(
                                            painter = painterResource(id = R.drawable.ic_notification),
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

                                HsvColorPicker(
                                    modifier = Modifier
                                        .size(300.dp)
                                        .align(Alignment.CenterHorizontally),
                                    controller = colorPickerController,
                                    initialColor = savedColor,
                                    onColorChanged = { colorEnvelope ->
                                        selectedColor = colorEnvelope.color
                                        colorPickerController.wheelColor = colorEnvelope.color
                                    },
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                BrightnessSlider(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .height(24.dp)
                                        .align(Alignment.CenterHorizontally),
                                    controller = colorPickerController
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
                                            viewModel.updateLiveCapsuleBgColor(colorHex)
                                            scope.launch {
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

                    ListItem(
                        headlineContent = { Text("实况通知模板") },
                        supportingContent = { Text(liveNotificationTemplate.description) },
                        leadingContent = { Icon(Icons.Rounded.NotificationsActive, contentDescription = null) },
                        trailingContent = {
                            Text(
                                text = liveNotificationTemplate.title,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            showTemplatePicker = true
                        }
                    )

                    if (showTemplatePicker) {
                        val templateSheetState =
                            rememberModalBottomSheetState(skipPartiallyExpanded = true)

                        OptimizedBottomSheet(
                            sheetState = templateSheetState,
                            onDismiss = {
                                scope.launch {
                                    templateSheetState.hide()
                                }.invokeOnCompletion {
                                    if (!templateSheetState.isVisible) {
                                        showTemplatePicker = false
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
                                    text = "选择实况通知模板",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                FlymeLiveTemplate.entries.forEachIndexed { index, template ->
                                    FlymeTemplateOption(
                                        template = template,
                                        selected = template == liveNotificationTemplate,
                                        customIcon = liveCapsuleIconBitmap,
                                        onSelect = {
                                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            viewModel.updateFlymeLiveTemplate(template)
                                            scope.launch {
                                                templateSheetState.hide()
                                            }.invokeOnCompletion {
                                                if (!templateSheetState.isVisible) {
                                                    showTemplatePicker = false
                                                }
                                            }
                                        }
                                    )

                                    if (index != FlymeLiveTemplate.entries.toTypedArray().lastIndex) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
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
                            viewModel.sendTestNotification()
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
                            viewModel.scheduleTestReminder()
                        }
                    }
                )
                
                // 应用在后台显示开关
                ListItem(
                    headlineContent = { Text("后台隐藏应用") },
                    supportingContent = { Text("开启后应用将不在最近任务列表中显示") },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.VisibilityOff,
                            contentDescription = null
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = hideFromRecents,
                            onCheckedChange = { enabled ->
                                viewModel.setHideFromRecents(enabled)
                            }
                        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlymeTemplateOption(
    template: FlymeLiveTemplate,
    selected: Boolean,
    customIcon: Bitmap?,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp)
            ),
        onClick = onSelect,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = template.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            FlymeTemplatePreview(
                template = template,
                customIcon = customIcon
            )
        }
    }
}

@Composable
private fun FlymeTemplatePreview(
    template: FlymeLiveTemplate,
    customIcon: Bitmap?
) {
    val context = LocalContext.current
    val sampleCourse = "示例课程"
    val sampleLocation = "示例教室 A101"
    val sampleTime = "10:00"

    @Composable
    fun TemplateCard(label: String, layoutRes: Int) {
        val isLightTheme = !isSystemInDarkTheme()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Card(
                modifier = Modifier.fillMaxWidth().border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(14.dp)
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isLightTheme) Color(0xFFF5F5F5) else Color(0xFF1F1F1F)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    factory = { ctx ->
                        LayoutInflater.from(ctx).inflate(layoutRes, null).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        }
                    },
                    update = { view ->
                        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        view.findViewById<TextView>(R.id.live_title)?.apply {
                            text = sampleCourse
                        }
                        view.findViewById<TextView>(R.id.live_time)?.apply {
                            text = sampleTime
                        }
                        view.findViewById<TextView>(R.id.location)?.apply {
                            text = sampleLocation
                        }
                        view.findViewById<ImageView>(R.id.live_icon)?.apply {
                            if (customIcon != null) {
                                setImageBitmap(customIcon)
                            } else {
                                setImageDrawable(
                                    ContextCompat.getDrawable(
                                        context,
                                        R.drawable.star
                                    )
                                )
                            }
                        }
                    }
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TemplateCard(label = "即将上课", layoutRes = template.ongoingLayout)
        TemplateCard(label = "已上课", layoutRes = template.finishedLayout)
    }
}

private fun scaleLiveIcon(bitmap: Bitmap, targetSizePx: Int): Bitmap {
    val maxSide = maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
    if (maxSide <= targetSizePx) return bitmap

    val width = (bitmap.width * targetSizePx / maxSide).coerceAtLeast(1)
    val height = (bitmap.height * targetSizePx / maxSide).coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, width, height, true)
}
