package com.star.schedule.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.star.schedule.Constants
import com.star.schedule.db.CourseEntity
import com.star.schedule.db.DatabaseProvider
import com.star.schedule.db.getWeekOfSemester
import com.star.schedule.widget.MyWidget.Companion.updateWidgetContent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Widget状态键
val KEY_COURSES_DATA = stringPreferencesKey("courses_data")
val KEY_UPDATE_TIME = stringPreferencesKey("update_time")

// 课程数据类
sealed class CourseStatus {
    data class BeforeClass(val course: CourseInfo, val timeUntilStart: String) : CourseStatus()
    data class DuringClass(val course: CourseInfo, val timeUntilEnd: String) : CourseStatus()
    data class AfterClass(val course: CourseInfo) : CourseStatus()
}

data class CourseInfo(
    val name: String,
    val location: String,
    val teacher: String,
    val startTime: String,
    val endTime: String,
    val dayOfWeek: Int
)

class MyWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d("MyWidget", "provideGlance called for glanceId: $id")

        try {
            // 确保数据库已初始化（不在协程作用域内执行延迟操作）
            if (!DatabaseProvider.isInitialized()) {
                DatabaseProvider.init(context)
                Log.d("MyWidget", "Database initialized for glanceId: $id")
            }

            provideContent {
                GlanceTheme {
                    Content()
                }
            }

        } catch (e: Exception) {
            Log.e("MyWidget", "Error in provideGlance for glanceId: $id", e)

            // 提供错误状态的内容
            provideContent {
                GlanceTheme {
                    ErrorContent()
                }
            }
        }
    }

    @Composable
    private fun ErrorContent() {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.errorContainer)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "加载失败",
                style = TextStyle(
                    color = GlanceTheme.colors.onErrorContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                modifier = GlanceModifier.padding(bottom = 8.dp)
            )

            Text(
                text = "请稍后重试",
                style = TextStyle(
                    color = GlanceTheme.colors.onErrorContainer,
                    fontSize = 14.sp
                )
            )
        }
    }

    @Composable
    private fun Content() {
        val prefs = currentState<Preferences>()
        val coursesData = prefs[KEY_COURSES_DATA] ?: "暂无课程信息"
        val lastUpdate = prefs[KEY_UPDATE_TIME] ?: ""

        // 拆分今天和明天课程（保持原有逻辑）
        val lines = coursesData.lines()
        val todayCourses = mutableListOf<String>()
        val tomorrowCourses = mutableListOf<String>()
        var currentDay = ""
        lines.forEach { line ->
            when {
                line.startsWith("今天:") -> {
                    currentDay = "today"
                    // 清空今天的课程列表，避免重复添加
                    if (todayCourses.isNotEmpty()) todayCourses.clear()
                }

                line.startsWith("明天:") -> {
                    currentDay = "tomorrow"
                    // 清空明天的课程列表，避免重复添加
                    if (tomorrowCourses.isNotEmpty()) tomorrowCourses.clear()
                }

                line.isNotBlank() -> {
                    if (currentDay == "today") {
                        // 只添加非标题行到今天的课程列表
                        if (!line.startsWith("今天:") && !line.startsWith("明天:")) {
                            todayCourses.add(line)
                        }
                    } else if (currentDay == "tomorrow") {
                        // 只添加非标题行到明天的课程列表
                        if (!line.startsWith("今天:") && !line.startsWith("明天:")) {
                            tomorrowCourses.add(line)
                        }
                    }
                }
            }
        }

        // 检查今天是否有课程
        val hasTodayCourses =
            todayCourses.isNotEmpty() && todayCourses.firstOrNull() != "今天无课" && !todayCourses.all { it == "今天无课" }
        val hasTomorrowCourses =
            tomorrowCourses.isNotEmpty() && tomorrowCourses.firstOrNull() != "明天无课" && !tomorrowCourses.all { it == "明天无课" }

        // 顶层 Column：上面是两列内容（占满剩余高度），下面是更新时间（固定在底部）
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (!hasTodayCourses && !hasTomorrowCourses) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .defaultWeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "今明两天都没课哟~",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                    )
                }
            } else {
                // 主体：左右两列，设为占用剩余空间
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight() // 使这行占据 Column 的剩余高度，从而把更新时间推到底部
                    ,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ========== 今天 ==========
                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight()
                            .padding(end = 6.dp)
                    ) {
                        Text(
                            text = "今天",
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            modifier = GlanceModifier.padding(bottom = 8.dp)
                        )

                        if (hasTodayCourses) {
                            LazyColumn(modifier = GlanceModifier.fillMaxHeight()) {
                                items(todayCourses.size) { index ->
                                    val line = todayCourses[index]
                                    val topPadding = if (index == 0) 0.dp else 8.dp

                                    // 直接在 items 的 lambda 内输出（不要再嵌套 item { }）
                                    Box(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .padding(top = topPadding) // 外部间距，背景外
                                    ) {
                                        Box(
                                            modifier = GlanceModifier
                                                .fillMaxWidth()
                                                .background(GlanceTheme.colors.primaryContainer)
                                                .padding(8.dp) // 背景内部内边距
                                                .cornerRadius(12.dp)
                                        ) {
                                            Text(
                                                text = line,
                                                style = TextStyle(
                                                    fontSize = 14.sp,
                                                    color = GlanceTheme.colors.onSurface
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // 今天无课时显示居中的"无课"
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "今天没课哟~",
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onSurfaceVariant,
                                        fontSize = 18.sp
                                    )
                                )
                            }
                        }
                    }

                    // ========== 明天 ==========
                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight()
                            .padding(start = 6.dp)
                    ) {
                        Text(
                            text = "明天",
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            modifier = GlanceModifier.padding(bottom = 8.dp)
                        )

                        if (hasTomorrowCourses) {
                            LazyColumn(modifier = GlanceModifier.fillMaxHeight()) {
                                items(tomorrowCourses.size) { index ->
                                    val line = tomorrowCourses[index]
                                    val topPadding = if (index == 0) 0.dp else 8.dp

                                    Box(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .padding(top = topPadding)
                                    ) {
                                        Box(
                                            modifier = GlanceModifier
                                                .fillMaxWidth()
                                                .background(GlanceTheme.colors.secondaryContainer)
                                                .padding(8.dp)
                                                .cornerRadius(12.dp)
                                        ) {
                                            Text(
                                                text = line,
                                                style = TextStyle(
                                                    fontSize = 14.sp,
                                                    color = GlanceTheme.colors.onSurface
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // 明天无课时显示居中的"无课"
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "明天没课哟~",
                                    style = TextStyle(
                                        color = GlanceTheme.colors.onSurfaceVariant,
                                        fontSize = 18.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ========== 底部的更新时间（左下角）==========
            // 使用较小字号和次要色，放在 Column 底部的第 2 个子项，自然位于左下角
            val displayTime =
                if (lastUpdate.isNotBlank()) "最后更新时间：$lastUpdate" else "最后更新时间：--:--"
            Text(
                text = displayTime,
                modifier = GlanceModifier
                    .padding(top = 8.dp) // 与上方内容保持一点间隙
                    .fillMaxWidth(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                )
            )
        }
    }

    companion object {
        suspend fun updateWidgetContent(context: Context) {
            try {
                if (!DatabaseProvider.isInitialized()) {
                    DatabaseProvider.init(context)
                }

                val database = DatabaseProvider.db
                val dao = database.scheduleDao()

                // 获取当前课表ID
                val currentTimetableId = dao.getPreferenceFlow(Constants.PREF_CURRENT_TIMETABLE)
                    .firstOrNull()?.toLongOrNull() ?: return

                // 获取课表信息
                val timetable = dao.getTimetableFlow(currentTimetableId).firstOrNull() ?: return

                // 获取当前日期和周数
                val today = LocalDate.now()
                val startDate = LocalDate.parse(timetable.startDate)
                val currentWeekNumber = today.getWeekOfSemester(startDate)

                // 获取课程和作息时间
                val courses = dao.getCoursesFlow(currentTimetableId).firstOrNull() ?: emptyList()
                val lessonTimes =
                    dao.getLessonTimesFlow(currentTimetableId).firstOrNull() ?: emptyList()

                // 过滤本周课程
                val thisWeekCourses = courses.filter { it.weeks.contains(currentWeekNumber) }

                // 获取今天和明天的课程
                val todayCourses = thisWeekCourses.filter { it.dayOfWeek == today.dayOfWeek.value }
                val tomorrow = today.plusDays(1)
                val tomorrowCourses =
                    thisWeekCourses.filter { it.dayOfWeek == tomorrow.dayOfWeek.value }

                // 获取当前时间
                val now = LocalDateTime.now()
                val currentTime = now.toLocalTime()

                // 构建widget显示内容
                val widgetContent = buildString {
                    // 今天的课程
                    if (todayCourses.isNotEmpty()) {
                        appendLine("今天:")
                        todayCourses.sortedBy { it.periods.minOrNull() ?: 0 }.forEach { course ->
                            val startPeriod = course.periods.minOrNull() ?: 0
                            val endPeriod = course.periods.maxOrNull() ?: 0
                            val startLesson = lessonTimes.find { it.period == startPeriod }
                            val endLesson = lessonTimes.find { it.period == endPeriod }

                            val startTime = startLesson?.startTime ?: ""
                            val endTime = endLesson?.endTime ?: ""

                            val status = getCourseStatus(course, startTime, endTime, currentTime)

                            appendLine(formatCourseInfo(course, startTime, endTime, status))
                        }
                    }

                    appendLine()

                    // 明天的课程
                    if (tomorrowCourses.isNotEmpty()) {
                        appendLine("明天:")
                        tomorrowCourses.sortedBy { it.periods.minOrNull() ?: 0 }.take(2)
                            .forEach { course ->
                                val startPeriod = course.periods.minOrNull() ?: 0
                                val endPeriod = course.periods.maxOrNull() ?: 0
                                val startLesson = lessonTimes.find { it.period == startPeriod }
                                val endLesson = lessonTimes.find { it.period == endPeriod }

                                val startTime = startLesson?.startTime ?: ""
                                val endTime = endLesson?.endTime ?: ""

                                appendLine(formatCourseInfo(course, startTime, endTime, null))
                            }
                    }
                }

                val updateTimeStr = now.format(DateTimeFormatter.ofPattern("HH:mm"))

                // 更新所有widget实例
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(MyWidget::class.java)

                if (glanceIds.isEmpty()) {
                    // 没有有效的小组件实例，跳过更新
                    return
                }

                // 进一步验证每个widget ID的有效性
                val validGlanceIds = glanceIds.filter { id ->
                    try {
                        // 尝试获取widget信息，如果成功则说明widget有效
                        val appWidgetId = manager.getAppWidgetId(id)
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
                        appWidgetInfo != null
                    } catch (_: Exception) {
                        false
                    }
                }

                if (validGlanceIds.isEmpty()) {
                    // 没有有效的小组件实例，跳过更新
                    return
                }

                validGlanceIds.forEach { id ->
                    try {
                        updateAppWidgetState(
                            context,
                            PreferencesGlanceStateDefinition,
                            id
                        ) { prefs: Preferences ->
                            val mutable = prefs.toMutablePreferences()
                            mutable[KEY_COURSES_DATA] = widgetContent
                            mutable[KEY_UPDATE_TIME] = updateTimeStr
                            mutable
                        }
                    } catch (e: Exception) {
                        // 单个widget实例更新失败，继续处理其他实例
                        e.printStackTrace()
                    }
                }

                try {
                    MyWidget().updateAll(context)
                } catch (e: Exception) {
                    // 整体更新失败，但单个实例可能已成功更新
                    e.printStackTrace()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun getCourseStatus(
            course: CourseEntity,
            startTime: String,
            endTime: String,
            currentTime: LocalTime
        ): CourseStatus? {
            try {
                if (startTime.isBlank() || endTime.isBlank()) {
                    return null
                }

                val timePattern = Regex("^(0?[0-9]|1[0-9]|2[0-3]):[0-5][0-9]$")
                if (!startTime.matches(timePattern) || !endTime.matches(timePattern)) {
                    return null
                }

                val start = LocalTime.parse(startTime)
                val end = LocalTime.parse(endTime)

                return when {
                    currentTime.isBefore(start) -> {
                        val duration = Duration.between(currentTime, start)
                        val minutes = duration.toMinutes()
                        val timeStr = when {
                            minutes < 60 -> "${minutes}分钟后"
                            minutes < 1440 -> "${minutes / 60}小时后"
                            else -> "明天"
                        }
                        CourseStatus.BeforeClass(
                            CourseInfo(
                                course.name,
                                course.location,
                                course.teacher,
                                startTime,
                                endTime,
                                course.dayOfWeek
                            ),
                            timeStr
                        )
                    }

                    currentTime.isAfter(end) -> {
                        CourseStatus.AfterClass(
                            CourseInfo(
                                course.name,
                                course.location,
                                course.teacher,
                                startTime,
                                endTime,
                                course.dayOfWeek
                            )
                        )
                    }

                    currentTime.isAfter(start) && currentTime.isBefore(end) -> {
                        val duration = Duration.between(currentTime, end)
                        val minutes = duration.toMinutes()
                        val timeStr = "${minutes}分钟后结束"
                        CourseStatus.DuringClass(
                            CourseInfo(
                                course.name,
                                course.location,
                                course.teacher,
                                startTime,
                                endTime,
                                course.dayOfWeek
                            ),
                            timeStr
                        )
                    }

                    else -> null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        private fun formatCourseInfo(
            course: CourseEntity,
            startTime: String,
            endTime: String,
            status: CourseStatus?
        ): String {
            val timeInfo = if (startTime.isNotBlank() && endTime.isNotBlank()) {
                "${startTime}-${endTime}"
            } else {
                "时间待定"
            }

            val baseInfo = "$timeInfo ${course.name}"
            val locationInfo = if (course.location.isNotBlank()) " @${course.location}" else ""
            val teacherInfo = if (course.teacher.isNotBlank()) " (${course.teacher})" else ""

            return when (status) {
                is CourseStatus.BeforeClass -> "$baseInfo$locationInfo$teacherInfo 【${status.timeUntilStart}】"
                is CourseStatus.DuringClass -> "$baseInfo$locationInfo$teacherInfo 【${status.timeUntilEnd}】"
                is CourseStatus.AfterClass -> "$baseInfo$locationInfo$teacherInfo 【已结束】"
                else -> "$baseInfo$locationInfo$teacherInfo"
            }
        }
    }
}

class MyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MyWidget()

    @OptIn(DelicateCoroutinesApi::class)
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        GlobalScope.launch(Dispatchers.IO) {
            updateWidgetContent(context)
        }
        if (com.star.schedule.service.WidgetUpdateJobService.isJobScheduled(context)) {
            Log.d("MyWidgetReceiver", "Widget update job is already scheduled.")
        } else {
            Log.d("MyWidgetReceiver", "Scheduling widget update job.")
            com.star.schedule.service.WidgetUpdateJobService.scheduleJob(context)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)

        com.star.schedule.service.WidgetUpdateJobService.cancelJob(context)
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(
            "MyWidgetReceiver",
            "onUpdate called with ${appWidgetIds.size} widget IDs: ${appWidgetIds.joinToString()}"
        )

        try {
            // 过滤掉无效的小组件ID
            val validAppWidgetIds = appWidgetIds.filter { appWidgetId ->
                try {
                    val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
                    val isValid = appWidgetInfo != null
                    Log.d("MyWidgetReceiver", "Widget ID $appWidgetId validity check: $isValid")
                    isValid
                } catch (e: Exception) {
                    Log.w("MyWidgetReceiver", "Error checking widget ID $appWidgetId validity", e)
                    false
                }
            }

            Log.d(
                "MyWidgetReceiver",
                "Valid widget IDs: ${validAppWidgetIds.size} out of ${appWidgetIds.size}"
            )

            // 异步处理每个小组件的更新，避免阻塞
            validAppWidgetIds.forEach { appWidgetId ->
                try {
                    Log.d("MyWidgetReceiver", "Starting async update for widget: $appWidgetId")

                    // 为每个小组件创建独立的更新任务
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            // 确保数据库已初始化
                            if (!DatabaseProvider.isInitialized()) {
                                DatabaseProvider.init(context)
                                Log.d(
                                    "MyWidgetReceiver",
                                    "Database initialized for widget: $appWidgetId"
                                )
                            }

                            // 更新小组件内容
                            updateWidgetContent(context)

                            Log.d(
                                "MyWidgetReceiver",
                                "Async update completed for widget: $appWidgetId"
                            )
                        } catch (e: Exception) {
                            Log.e(
                                "MyWidgetReceiver",
                                "Async update failed for widget: $appWidgetId",
                                e
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(
                        "MyWidgetReceiver",
                        "Failed to start async update for widget: $appWidgetId",
                        e
                    )
                }
            }

            // 只对有效的小组件ID调用父类方法
            if (validAppWidgetIds.isNotEmpty()) {
                Log.d(
                    "MyWidgetReceiver",
                    "Calling super.onUpdate with ${validAppWidgetIds.size} valid widget IDs"
                )
                super.onUpdate(context, appWidgetManager, validAppWidgetIds.toIntArray())
                Log.d("MyWidgetReceiver", "super.onUpdate completed successfully")
            } else {
                // 如果没有有效的小组件ID，跳过更新操作
                Log.w("MyWidgetReceiver", "No valid app widget IDs found, skipping update")
            }
        } catch (e: Exception) {
            Log.e("MyWidgetReceiver", "Error in onUpdate", e)
        }

        try {
            if (!com.star.schedule.service.WidgetUpdateJobService.isJobScheduled(context)) {
                Log.d("MyWidgetReceiver", "Scheduling JobScheduler task")
                com.star.schedule.service.WidgetUpdateJobService.scheduleJob(context)
            } else {
                Log.d("MyWidgetReceiver", "JobScheduler task already scheduled")
            }
        } catch (e: Exception) {
            Log.e("MyWidgetReceiver", "Error scheduling JobScheduler task", e)
        }

        Log.d("MyWidgetReceiver", "onUpdate completed")
    }
}
