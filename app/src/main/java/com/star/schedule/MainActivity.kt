package com.star.schedule

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.star.schedule.db.DatabaseProvider
import com.star.schedule.notification.UnifiedNotificationManager
import com.star.schedule.notification.WorkManagerConfig
import com.star.schedule.ui.layouts.DateRange
import com.star.schedule.ui.layouts.Settings
import com.star.schedule.ui.layouts.TimetableSettings
import com.star.schedule.ui.theme.StarScheduleTheme
import com.star.schedule.ui.components.OptimizedBottomSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DatabaseProvider.init(this)
        WorkManagerConfig.initialize(this) // 初始化 WorkManager
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
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val latestTag = fetchLatestReleaseTag()
        if (latestTag == null) {
            Log.d("StarSchedule", "Failed to fetch latest release tag")
            return@LaunchedEffect
        }
        Log.d("StarSchedule", "Latest release tag: $latestTag")
        val currentVersion = getAppVersionName(context)
        Log.d("StarSchedule", "Current version: $currentVersion")
        if (isNewerVersion(latestTag, currentVersion)) {
            Log.d("StarSchedule", "New version available: $latestTag")
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = "发现新版本：v$currentVersion => $latestTag",
                    actionLabel = "更新",
                    duration = SnackbarDuration.Indefinite
                )
            }
        }
    }

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
        ItemData("日程", Icons.Rounded.CalendarMonth),
        ItemData("课表设置", Icons.Rounded.EditCalendar),
        ItemData("设置", Icons.Rounded.Settings)
    )

    val exitAlwaysScrollBehavior =
        FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = Bottom)
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(exitAlwaysScrollBehavior),
        snackbarHost = {
            UpdateSnackbarHost(snackbarHostState = snackbarHostState, context = context)
        },
        floatingActionButton = {
            HorizontalFloatingToolbar(
                expanded = true,
                shape = MaterialTheme.shapes.largeIncreased,
                scrollBehavior = exitAlwaysScrollBehavior,
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
                content = {
                    items.forEachIndexed { index, item ->
                        if (index == 0) {
                            AnimatedContent(
                                targetState = selectedItem == 0
                            ) {isWeekRow ->
                                if (isWeekRow) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                                    ) {
                                        ToggleButton(
                                            checked = false,
                                            enabled = weeksWithCourses.indexOf(currentWeekNumber) > 0,
                                            onCheckedChange = {
                                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                val index = weeksWithCourses.indexOf(currentWeekNumber)
                                                if (index > 0) {
                                                    onCurrentWeekNumberChange(weeksWithCourses[index - 1])
                                                }
                                            },
                                            colors = ToggleButtonDefaults.toggleButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Icon(Icons.Rounded.ChevronLeft, contentDescription = "上一周")
                                        }
                                        ToggleButton(
                                            checked = false,
                                            onCheckedChange = {
                                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                showWeekSelector = true
                                            },
                                            colors = ToggleButtonDefaults.toggleButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Text("第 $currentWeekNumber 周")
                                        }
                                        ToggleButton(
                                            checked = false,
                                            enabled = weeksWithCourses.indexOf(currentWeekNumber) < weeksWithCourses.size - 1,
                                            onCheckedChange = {
                                                haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                                val index = weeksWithCourses.indexOf(currentWeekNumber)
                                                if (index < weeksWithCourses.size - 1) {
                                                    onCurrentWeekNumberChange(weeksWithCourses[index + 1])
                                                }
                                            },
                                            colors = ToggleButtonDefaults.toggleButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            )
                                        ) {
                                            Icon(Icons.Rounded.ChevronRight, contentDescription = "下一周")
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
    val windowInfo = LocalWindowInfo.current
    val screenWidth = with(LocalDensity.current) { windowInfo.containerSize.width.toDp() }

    LaunchedEffect(currentWeekNumber, weeksWithCourses, screenWidth) {
        val index = weeksWithCourses.indexOf(currentWeekNumber)
        if (index != -1) {
            kotlinx.coroutines.yield()

            val itemWidth = 92.dp
            val halfViewportPx = with(density) { (screenWidth - 40.dp).toPx() / 2 }
            val halfItemPx = with(density) { itemWidth.toPx() / 2 }
            val offset = (halfViewportPx - halfItemPx).toInt()

            listState.animateScrollToItem(index, scrollOffset = -offset)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState())
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

fun getAppVersionName(context: Context): String {
    return try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        pInfo.versionName ?: ""
    } catch (_: Exception) {
        ""
    }
}

suspend fun fetchLatestReleaseTag(): String? {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val url = "https://api.github.com/repos/lightStarrr/starSchedule/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                // GitHub 要求有 User-Agent
                .header("User-Agent", "StarScheduleApp")
                .build()
            val resp = client.newCall(request).execute()
            if (!resp.isSuccessful) {
                return@withContext null
            }
            val body = resp.body.string()
            val json = JSONObject(body)
            val tag = json.optString("tag_name", "v1.0.0")
            tag
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

/** 简单比较版本号（假设格式是 vX.Y.Z） */
fun isNewerVersion(latestTag: String, currentVersion: String): Boolean {
    // 去掉前缀 v （如果有）
    val lt = latestTag.trimStart('v', 'V')
    val cv = currentVersion.trimStart('v', 'V')
    val ltParts = lt.split(".")
    val cvParts = cv.split(".")
    val len = maxOf(ltParts.size, cvParts.size)
    for (i in 0 until len) {
        val lNum = ltParts.getOrNull(i)?.toIntOrNull() ?: 0
        val cNum = cvParts.getOrNull(i)?.toIntOrNull() ?: 0
        if (lNum > cNum) return true
        if (lNum < cNum) return false
    }
    return false
}

@Composable
fun UpdateSnackbarHost(snackbarHostState: SnackbarHostState, context: Context) {
    val scope = rememberCoroutineScope()

    SnackbarHost(hostState = snackbarHostState) { data ->
        val messageText = data.visuals.message
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryFixed,
                contentColor = MaterialTheme.colorScheme.onPrimaryFixed
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "发现新版本",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = messageText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.alpha(0.8f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                            }
                        },
                        modifier = Modifier.padding(end = 4.dp).size(40.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Button(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                "https://github.com/lightStarrr/starSchedule/releases".toUri()
                            )
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onPrimary) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = "更新",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "更新",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
