package com.star.schedule

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection.Companion.Bottom
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.star.schedule.db.DatabaseProvider
import com.star.schedule.notification.UnifiedNotificationManager
import com.star.schedule.ui.layouts.DateRange
import com.star.schedule.ui.layouts.Settings
import com.star.schedule.ui.layouts.TimetableSettings
import com.star.schedule.ui.theme.StarScheduleTheme
import com.star.schedule.ui.components.OptimizedBottomSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Layout(context: Activity) {
    val scope = rememberCoroutineScope()
    var selectedItem by remember { mutableIntStateOf(0) }
    var realCurrentWeek by remember { mutableIntStateOf(0) }
    var currentWeekNumber by remember { mutableIntStateOf(0) }
    var weeksWithCourses by remember { mutableStateOf<List<Int>>(emptyList()) }
    var showWeekSelector by remember { mutableStateOf(false) }
    val weekSelectorSheetState = rememberModalBottomSheetState()

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

    // 定义更新周数的函数
    val onCurrentWeekNumberChange: (Int) -> Unit = { newWeekNumber ->
        currentWeekNumber = newWeekNumber
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

    val exitAlwaysScrollBehavior =
        FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = Bottom)
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(exitAlwaysScrollBehavior),
        floatingActionButton = {
            HorizontalFloatingToolbar(
                expanded = true,
                shape = MaterialTheme.shapes.largeIncreased,
                scrollBehavior = exitAlwaysScrollBehavior,
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
                content = {
                    items.forEachIndexed { index, item ->
                        if (selectedItem == 0 && index == 0) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                            ) {
                                ToggleButton(
                                    checked = false,
                                    enabled = currentWeekNumber > 1,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        if (currentWeekNumber > 1) {
                                            onCurrentWeekNumberChange(currentWeekNumber - 1)
                                        }
                                    },
                                    colors = ToggleButtonDefaults.toggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    )
                                ) {
                                    Icon(
                                        Icons.Rounded.ChevronLeft,
                                        contentDescription = "上一周"
                                    )
                                }
                                ToggleButton(
                                    checked = false,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        showWeekSelector = true
                                    },
                                    colors = ToggleButtonDefaults.toggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    )
                                ) {
                                    Text("第 $currentWeekNumber 周")
                                }
                                ToggleButton(
                                    checked = false,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                        onCurrentWeekNumberChange(currentWeekNumber + 1)
                                    },
                                    colors = ToggleButtonDefaults.toggleButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    )
                                ) {
                                    Icon(
                                        Icons.Rounded.ChevronRight,
                                        contentDescription = "下一周"
                                    )
                                }
                            }

                        } else {
                            ToggleButton(
                                checked = selectedItem == index,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    selectedItem = index
                                },
                                content = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.name
                                    )
                                }
                            )
                        }
                    }
                },
            )
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        // 周数选择器BottomSheet
        if (showWeekSelector && weeksWithCourses.isNotEmpty()) {
            OptimizedBottomSheet(
                sheetState = weekSelectorSheetState,
                onDismiss = { scope.launch { weekSelectorSheetState.hide() }.invokeOnCompletion {
                    if (!weekSelectorSheetState.isVisible) {
                        showWeekSelector = false
                    }
                } }
            ) {
                WeekSelectorSheet(
                    currentWeekNumber = currentWeekNumber,
                    weeksWithCourses = weeksWithCourses,
                    onSelectWeek = { week ->
                        onCurrentWeekNumberChange(week)
                    },
                    realCurrentWeek = realCurrentWeek,
                    onDismiss = {
                        scope.launch { weekSelectorSheetState.hide() }.invokeOnCompletion {
                            if (!weekSelectorSheetState.isVisible) {
                                showWeekSelector = false
                            }
                        }
                    }
                )
            }
        }

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
                    dao = dao,
                    currentWeekNumber = currentWeekNumber,
                    onCurrentWeekNumberChange = { newWeekNumber ->
                        currentWeekNumber = newWeekNumber
                    },
                    onWeeksCalculated = { weeks ->
                        weeksWithCourses = weeks
                    },
                    upDateRealCurrentWeek = {
                        realCurrentWeek = it
                    }
                )

                1 -> TimetableSettings(
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

@Composable
fun WeekSelectorSheet(
    currentWeekNumber: Int,
    weeksWithCourses: List<Int>,
    realCurrentWeek: Int,
    onSelectWeek: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    LaunchedEffect(currentWeekNumber, weeksWithCourses, screenWidth) {
        val index = weeksWithCourses.indexOf(currentWeekNumber)
        if (index != -1) {
            kotlinx.coroutines.yield()

            val itemWidth = 92.dp + 6.dp
            val halfViewportPx = with(density) { (screenWidth - 40.dp).toPx() / 2 } // 20dp padding * 2
            val halfItemPx = with(density) { itemWidth.toPx() / 2 }
            val offset = (halfViewportPx - halfItemPx).toInt()

            listState.animateScrollToItem(index, scrollOffset = -offset)
        }
    }

    // ✅ 其他 UI 代码保持不变...
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = "选择周数",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                items(weeksWithCourses) { week ->
                    val isSelected = week == currentWeekNumber
                    val isCurrent = week == realCurrentWeek

                    val backgroundColor by animateColorAsState(
                        targetValue = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        animationSpec = tween(200)
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(200)
                    )
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1f,
                        animationSpec = tween(150)
                    )

                    Box(
                        modifier = Modifier
                            .graphicsLayer(scaleX = scale, scaleY = scale)
                            .height(56.dp)
                            .defaultMinSize(minWidth = 92.dp)
                    ) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                onSelectWeek(week)
                                scope.launch { onDismiss() }
                            },
                            modifier = Modifier.fillMaxSize(),
                            shape = MaterialTheme.shapes.large,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = backgroundColor,
                                contentColor = textColor
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "第 $week 周",
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1
                                )
                            }
                        }

                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 8.dp, y = (-8).dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .zIndex(2f)
                            ) {
                                Text(
                                    text = "本周",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
