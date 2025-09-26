package com.star.schedule.ui.layouts

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.star.schedule.R
import com.star.schedule.db.ScheduleDao
import com.star.schedule.notification.UnifiedNotificationManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(content: Activity, dao: ScheduleDao, notificationManager: UnifiedNotificationManager) {
    val haptic = LocalHapticFeedback.current
    var clickCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var resetJob by remember { mutableStateOf<Job?>(null) }

    // 权限申请器
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                // 权限获得后，发送测试通知
                notificationManager.sendTestNotification()
            }
        }
    )

    // 所有课表
    val timetables by dao.getAllTimetables().collectAsState(initial = emptyList())
    val currentTimetableIdPref by dao.getPreferenceFlow("current_timetable")
        .collectAsState(initial = null)

    // 当前课表ID
    var currentTimetableId by remember { mutableStateOf<Long?>(null) }

    // 课前提醒开关状态
    var reminderEnabled by remember { mutableStateOf(false) }

    // 控制 BottomSheet 显示
    var showTimetableSheet by remember { mutableStateOf(false) }

    // 权限申请辅助函数
    fun requestNotificationPermissionIfNeeded(onPermissionGranted: () -> Unit) {
        when (ContextCompat.checkSelfPermission(content, Manifest.permission.POST_NOTIFICATIONS)) {
            PackageManager.PERMISSION_GRANTED -> {
                onPermissionGranted()
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

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )

        // 当前课表切换
        OutlinedButton(
            onClick = { showTimetableSheet = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            val currentName =
                timetables.firstOrNull { it.id == currentTimetableId }?.name ?: "未选择"
            Text("当前课表: $currentName")
        }

        // 课表选择 BottomSheet
        if (showTimetableSheet) {
            ModalBottomSheet(
                onDismissRequest = { showTimetableSheet = false }
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding()
                ) {
                    item {
                        Text(
                            text = "选择课表",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(16.dp)
                        )
                    }

                    items(timetables) { timetable ->
                        ListItem(
                            headlineContent = { Text(timetable.name) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        dao.setPreference(
                                            "current_timetable",
                                            timetable.id.toString()
                                        )
                                    }
                                    showTimetableSheet = false
                                }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

        // 课前15分钟提醒开关
        ListItem(
            headlineContent = { Text("课前提醒") },
            supportingContent = {
                val currentName =
                    timetables.firstOrNull { it.id == currentTimetableId }?.name ?: "未选择课表"
                Text("为当前课表（$currentName）开启课前15分钟提醒（自动选择通知类型）")
            },
            leadingContent = {
                Icon(
                    if (reminderEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications,
                    contentDescription = null
                )
            },
            trailingContent = {
                Switch(
                    checked = reminderEnabled,
                    enabled = currentTimetableId != null,
                    onCheckedChange = { enabled ->
                        if (enabled && currentTimetableId != null) {
                            requestNotificationPermissionIfNeeded {
                                scope.launch {
                                    notificationManager.enableRemindersForTimetable(
                                        currentTimetableId!!
                                    )
                                    reminderEnabled = true
                                    notificationManager.logNotificationStatus()
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

        ListItem(
            headlineContent = { Text("通知测试") },
            supportingContent = { Text("测试通知功能（自动选择普通通知或魅族实况通知）") },
            leadingContent = { Icon(Icons.Rounded.PhoneAndroid, contentDescription = null) },
            modifier = Modifier.clickable {
                scope.launch {
                    if (notificationManager.hasNotificationPermission()) {
                        notificationManager.sendTestNotification()
                        notificationManager.logNotificationStatus()
                    } else {
                        requestNotificationPermissionIfNeeded {
                            notificationManager.sendTestNotification()
                        }
                    }
                }
            }
        )

        ListItem(
            headlineContent = { Text("关于应用") },
            supportingContent = {
                Text(
                    "版本 " + content.packageManager.getPackageInfo(
                        content.packageName,
                        0
                    ).versionName
                )
            },
            leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
            modifier = Modifier.clickable {
                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                clickCount++
                if (clickCount == 5) {
                    val mediaPlayer = MediaPlayer.create(content, R.raw.egg)
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
    }
}