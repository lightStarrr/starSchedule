package com.star.schedule

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.star.schedule.db.DatabaseProvider
import com.star.schedule.notification.UnifiedNotificationManager
import com.star.schedule.ui.layouts.DateRange
import com.star.schedule.ui.layouts.Settings
import com.star.schedule.ui.layouts.TimetableSettings
import com.star.schedule.ui.theme.StarScheduleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DatabaseProvider.init(this)
        enableEdgeToEdge()
        setContent {
            StarScheduleTheme {
                Layout(context = this)
            }
        }
    }
}

@Composable
fun Layout(context: Activity) {
    var selectedItem by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current
    val dao = DatabaseProvider.dao()
    val notificationManager = UnifiedNotificationManager(context)

    // 注入 notificationManager 到 dao 中
    dao.notificationManager = notificationManager

    // 在应用启动时初始化提醒系统
    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            notificationManager.initializeOnAppStart()
        }
    }

    data class ItemData(
        val name: String,
        val icon: ImageVector
    )

    val items = listOf(
        ItemData("日程", Icons.Rounded.DateRange),
        ItemData("课表设置", Icons.Rounded.CalendarMonth),
        ItemData("设置", Icons.Rounded.Settings)
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.name
                            )
                        },
                        label = { Text(item.name) },
                        selected = selectedItem == index,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            selectedItem = index
                        }
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedItem,
            modifier = Modifier.padding(innerPadding),
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(
                        animationSpec = tween(300),
                        initialOffsetX = { it / 5 }
                    ) + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(300),
                                targetOffsetX = { -it / 5 }
                            ) + fadeOut(tween(300)))
                } else {
                    // 从左往右切
                    (slideInHorizontally(
                        animationSpec = tween(300),
                        initialOffsetX = { -it / 5 }
                    ) + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(300),
                                targetOffsetX = { it / 5 }
                            ) + fadeOut(tween(300)))
                }
            },
            label = "pageAnimation"
        ) { page ->
            when (page) {
                0 -> DateRange(
                    context = context,
                    dao = dao
                )

                1 -> TimetableSettings(
                    context = context,
                    dao = dao
                )

                2 -> Settings(
                    context = context,
                    dao = dao,
                    notificationManager = notificationManager
                )
            }
        }
    }
}
