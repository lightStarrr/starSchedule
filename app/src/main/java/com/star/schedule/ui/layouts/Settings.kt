package com.star.schedule.ui.layouts

import android.app.Activity
import android.media.MediaPlayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import com.star.schedule.db.ScheduleDao
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun Settings(content: Activity, dao: ScheduleDao) {
    var notificationsEnabled by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    var clickCount by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    var resetJob by remember { mutableStateOf<Job?>(null) }

    // ---------- 所有课表 ----------
    val timetables by dao.getAllTimetables().collectAsState(initial = emptyList())
    val currentTimetableIdPref by dao.getPreferenceFlow("current_timetable").collectAsState(initial = null)

    // ---------- 当前课表ID ----------
    var currentTimetableId by remember { mutableStateOf<Long?>(null) }

    var menuExpanded by remember { mutableStateOf(false) }

    // ---------- 保证 currentTimetableId 与 preference 和 timetables 同步 ----------
    LaunchedEffect(timetables, currentTimetableIdPref) {
        currentTimetableId = currentTimetableIdPref?.toLongOrNull()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )

        // ---------- 当前课表切换 ----------
        Box {
            OutlinedButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                val currentName = timetables.firstOrNull { it.id == currentTimetableId }?.name ?: "未选择"
                Text("当前课表: $currentName")
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                timetables.forEach { timetable ->
                    DropdownMenuItem(
                        text = { Text(timetable.name) },
                        onClick = {
                            currentTimetableId = timetable.id
                            scope.launch { dao.setPreference("current_timetable", timetable.id.toString()) }
                            menuExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Divider()

        // ---------- 通知开关 ----------
        ListItem(
            headlineContent = { Text("通知") },
            supportingContent = { Text("开启后会在课程前10分钟发送通知") },
            leadingContent = { Icon(Icons.Rounded.Notifications, contentDescription = null) },
            trailingContent = {
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { notificationsEnabled = it }
                )
            }
        )

        Divider()

        // ---------- 关于应用彩蛋 ----------
        ListItem(
            headlineContent = { Text("关于应用") },
            supportingContent = { Text("版本 " + content.packageManager.getPackageInfo(content.packageName, 0).versionName) },
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
