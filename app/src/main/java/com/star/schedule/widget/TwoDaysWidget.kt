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
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.star.schedule.Constants
import com.star.schedule.db.CourseEntity
import com.star.schedule.db.DatabaseProvider
import com.star.schedule.db.getWeekOfSemester
import com.star.schedule.service.WidgetUpdateJobService
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// 课程状态枚举
enum class CourseStatus {
    ACTIVE,     // 活跃课程（未开始或进行中）
    ENDED,      // 已结束
    UPCOMING;   // 即将开始（可选的扩展状态）
    
    companion object {
        fun fromString(status: String?): CourseStatus {
            return when (status) {
                "已结束" -> ENDED
                else -> ACTIVE
            }
        }
    }
}

// Widget状态键
val KEY_COURSES_JSON = stringPreferencesKey("courses_json")
val KEY_UPDATE_TIME = stringPreferencesKey("update_time")

// JSON解析器 - 优化配置
private val jsonParser = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = false  // 不编码默认值，减少JSON大小
    prettyPrint = true     // 美化输出
    prettyPrintIndent = "  "  // 使用2个空格缩进
}

// JSON数据模型 - 简化结构
@Serializable
data class WidgetCourseData(
    val today: DayCourses,
    val tomorrow: DayCourses,
    val updateTime: String
)

@Serializable
data class DayCourses(
    val courses: List<CourseItem>
)

@Serializable
data class CourseItem(
    val name: String,
    val location: String = "",      // 默认为空字符串
    val teacher: String = "",      // 默认为空字符串
    val startTime: String = "",      // 默认为空字符串
    val endTime: String = "",        // 默认为空字符串
    val status: String? = null,      // 状态信息（显示文本）
    val color: String = "primary",   // 简化字段名
    val courseStatus: CourseStatus = CourseStatus.ACTIVE  // 课程状态枚举
)

class TwoDaysWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d("TwoDaysWidget", "provideGlance called for glanceId: $id")

        try {
            // 确保数据库已初始化
            if (!DatabaseProvider.isInitialized()) {
                DatabaseProvider.init(context)
            }

            provideContent {
                GlanceTheme {
                    Content()
                }
            }
        } catch (e: Exception) {
            Log.e("TwoDaysWidget", "Error in provideGlance", e)
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
        val coursesJson = prefs[KEY_COURSES_JSON]
        val lastUpdate = prefs[KEY_UPDATE_TIME] ?: ""

        // 解析JSON数据
        val widgetData = try {
            if (coursesJson != null) {
                jsonParser.decodeFromString<WidgetCourseData>(coursesJson)
            } else {
                // 返回空数据结构
                WidgetCourseData(
                    today = DayCourses(emptyList()),
                    tomorrow = DayCourses(emptyList()),
                    updateTime = lastUpdate
                )
            }
        } catch (e: Exception) {
            Log.e("TwoDaysWidget", "JSON解析失败", e)
            WidgetCourseData(
                today = DayCourses(emptyList()),
                tomorrow = DayCourses(emptyList()),
                updateTime = lastUpdate
            )
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .padding(12.dp)
        ) {
            // 主要内容区域
            if (widgetData.today.courses.isEmpty() && widgetData.tomorrow.courses.isEmpty()) {
                // 两天都没课
                Box(
                    modifier = GlanceModifier.fillMaxSize().defaultWeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "今明两天都没课~",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                }
            } else {
                // 显示课程列表
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                ) {
                    // 今天课程
                    DayColumn(
                        title = "今天",
                        dayData = widgetData.today,
                        isToday = true,
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(end = 6.dp)
                    )

                    // 明天课程
                    DayColumn(
                        title = "明天",
                        dayData = widgetData.tomorrow,
                        isToday = false,
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(start = 6.dp)
                    )
                }
            }

            // 更新时间
            Text(
                text = "更新: ${widgetData.updateTime}",
                modifier = GlanceModifier.padding(top = 8.dp).fillMaxWidth(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp
                )
            )
        }
    }

    @Composable
    private fun DayColumn(
        title: String,
        dayData: DayCourses,
        isToday: Boolean,
        modifier: GlanceModifier
    ) {
        Column(modifier = modifier) {
            // 标题
            Text(
                text = title,
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                modifier = GlanceModifier.padding(bottom = 8.dp)
            )

            if (dayData.courses.isNotEmpty()) {
                // 过滤掉已结束的课程
                val activeCourses = dayData.courses.filter { it.courseStatus != CourseStatus.ENDED }
                
                if (activeCourses.isNotEmpty()) {
                    // 显示未结束的课程列表
                    LazyColumn(modifier = GlanceModifier.fillMaxHeight()) {
                        items(activeCourses.size) { index ->
                            val course = activeCourses[index]
                            CourseItem(
                                course = course,
                                isToday = isToday,
                                isFirst = index == 0
                            )
                        }
                    }
                } else {
                    // 所有课程都已结束 - 显示今日已结束提示
                    Box(
                        modifier = GlanceModifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isToday) "今天的课都上完了~" else "${title}没课~",
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 16.sp
                            )
                        )
                    }
                }
            } else {
                // 无课提示
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${title}没课~",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    )
                }
            }
        }
    }

    @Composable
    private fun CourseItem(
        course: CourseItem,
        isToday: Boolean,
        isFirst: Boolean
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(top = if (isFirst) 0.dp else 8.dp)
        ) {
            // 左侧彩色条
            Box(
                modifier = GlanceModifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        if (isToday) GlanceTheme.colors.primary 
                        else GlanceTheme.colors.secondary
                    )
                    .cornerRadius(2.dp),
                content = {}
            )

            // 课程信息
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(start = 8.dp)
            ) {
                // 课程名称
                Text(
                    text = course.name,
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = GlanceTheme.colors.onSurface,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )

                // 地点
                if (course.location.isNotBlank()) {
                    Text(
                        text = course.location,
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        ),
                        maxLines = 1
                    )
                }

                // 时间
                if (course.startTime.isNotBlank() && course.endTime.isNotBlank()) {
                    Text(
                        text = "${course.startTime} - ${course.endTime}",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        ),
                        maxLines = 1
                    )
                }

                // 状态
                course.status?.let { status ->
                    Text(
                        text = status,
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.primary,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }
            }
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
                val lessonTimes = dao.getLessonTimesFlow(currentTimetableId).firstOrNull() ?: emptyList()

                // 过滤本周课程
                val thisWeekCourses = courses.filter { it.weeks.contains(currentWeekNumber) }

                // 获取今天和明天的课程
                val todayCourses = thisWeekCourses.filter { it.dayOfWeek == today.dayOfWeek.value }
                val tomorrow = today.plusDays(1)
                val tomorrowCourses = thisWeekCourses.filter { it.dayOfWeek == tomorrow.dayOfWeek.value }

                // 获取当前时间
                val now = LocalDateTime.now()
                val currentTime = now.toLocalTime()
                val updateTimeStr = now.format(DateTimeFormatter.ofPattern("HH:mm"))

                // 构建今天的课程数据
                val todayCourseItems = todayCourses
                    .sortedBy { it.periods.minOrNull() ?: 0 }
                    .map { course ->
                        val startPeriod = course.periods.minOrNull() ?: 0
                        val endPeriod = course.periods.maxOrNull() ?: 0
                        val startLesson = lessonTimes.find { it.period == startPeriod }
                        val endLesson = lessonTimes.find { it.period == endPeriod }

                        val startTime = startLesson?.startTime ?: ""
                        val endTime = endLesson?.endTime ?: ""
                        val status = getCourseStatus(course, startTime, endTime, currentTime)
                        
                        CourseItem(
                            name = course.name,
                            location = course.location,
                            teacher = course.teacher,
                            startTime = startTime,
                            endTime = endTime,
                            status = status,
                            color = if (status != null) "secondary" else "primary",
                            courseStatus = CourseStatus.fromString(status)
                        )
                    }

                // 构建明天的课程数据（限制显示2门课程）
                val tomorrowCourseItems = tomorrowCourses
                    .sortedBy { it.periods.minOrNull() ?: 0 }
                    .take(2)
                    .map { course ->
                        val startPeriod = course.periods.minOrNull() ?: 0
                        val endPeriod = course.periods.maxOrNull() ?: 0
                        val startLesson = lessonTimes.find { it.period == startPeriod }
                        val endLesson = lessonTimes.find { it.period == endPeriod }

                        CourseItem(
                            name = course.name,
                            location = course.location,
                            teacher = course.teacher,
                            startTime = startLesson?.startTime ?: "",
                            endTime = endLesson?.endTime ?: "",
                            courseStatus = CourseStatus.ACTIVE  // 明天的课程默认活跃
                        )
                    }

                // 构建JSON数据
                val widgetData = WidgetCourseData(
                    today = DayCourses(
                        courses = todayCourseItems
                    ),
                    tomorrow = DayCourses(
                        courses = tomorrowCourseItems
                    ),
                    updateTime = updateTimeStr
                )

                val coursesJson = jsonParser.encodeToString(WidgetCourseData.serializer(), widgetData)

                // 更新所有widget实例
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(TwoDaysWidget::class.java)

                if (glanceIds.isEmpty()) return

                // 过滤有效的小组件ID
                val validGlanceIds = glanceIds.filter { id ->
                    try {
                        val appWidgetId = manager.getAppWidgetId(id)
                        val appWidgetManager = AppWidgetManager.getInstance(context)
                        appWidgetManager.getAppWidgetInfo(appWidgetId) != null
                    } catch (_: Exception) {
                        false
                    }
                }

                if (validGlanceIds.isEmpty()) return

                // 更新每个小组件
                validGlanceIds.forEach { id ->
                    try {
                        updateAppWidgetState(
                            context,
                            PreferencesGlanceStateDefinition,
                            id
                        ) { prefs: Preferences ->
                            prefs.toMutablePreferences().apply {
                                this[KEY_COURSES_JSON] = coursesJson
                                this[KEY_UPDATE_TIME] = updateTimeStr
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("TwoDaysWidget", "更新小组件失败", e)
                    }
                }

                // 刷新所有小组件
                try {
                    TwoDaysWidget().updateAll(context)
                } catch (e: Exception) {
                    Log.e("TwoDaysWidget", "刷新小组件失败", e)
                }

            } catch (e: Exception) {
                Log.e("TwoDaysWidget", "更新小组件内容失败", e)
            }
        }

        private fun getCourseStatus(
            course: CourseEntity,
            startTime: String,
            endTime: String,
            currentTime: LocalTime
        ): String? {
            try {
                if (startTime.isBlank() || endTime.isBlank()) return null

                val timePattern = Regex("^(0?[0-9]|1[0-9]|2[0-3]):[0-5][0-9]$")
                if (!startTime.matches(timePattern) || !endTime.matches(timePattern)) return null

                val start = LocalTime.parse(startTime)
                val end = LocalTime.parse(endTime)

                return when {
                    currentTime.isBefore(start) -> {
                        val minutes = java.time.Duration.between(currentTime, start).toMinutes()
                        when {
                            minutes < 60 -> "${minutes}分钟后"
                            minutes < 1440 -> "${minutes / 60}小时后"
                            else -> "明天"
                        }
                    }
                    currentTime.isAfter(end) -> "已结束"
                    currentTime.isAfter(start) && currentTime.isBefore(end) -> {
                        val minutes = java.time.Duration.between(currentTime, end).toMinutes()
                        "${minutes}分钟后结束"
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Log.e("TwoDaysWidget", "获取课程状态失败", e)
                return null
            }
        }
    }
}

class TwoDaysWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TwoDaysWidget()

    @OptIn(DelicateCoroutinesApi::class)
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        GlobalScope.launch(Dispatchers.IO) {
            TwoDaysWidget.updateWidgetContent(context)
        }
        
        // 调度更新任务
        try {
            if (!WidgetUpdateJobService.isJobScheduled(context)) {
                WidgetUpdateJobService.scheduleJob(context)
            }
        } catch (e: Exception) {
            Log.e("TwoDaysWidgetReceiver", "调度更新任务失败", e)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        try {
            WidgetUpdateJobService.cancelJob(context)
        } catch (e: Exception) {
            Log.e("TwoDaysWidgetReceiver", "取消更新任务失败", e)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Log.d("TwoDaysWidgetReceiver", "onUpdate: ${appWidgetIds.size} widgets")

        try {
            // 过滤有效的小组件ID
            val validAppWidgetIds = appWidgetIds.filter { appWidgetId ->
                try {
                    appWidgetManager.getAppWidgetInfo(appWidgetId) != null
                } catch (e: Exception) {
                    false
                }
            }

            if (validAppWidgetIds.isEmpty()) return

            // 异步更新内容
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    if (!DatabaseProvider.isInitialized()) {
                        DatabaseProvider.init(context)
                    }
                    TwoDaysWidget.updateWidgetContent(context)
                } catch (e: Exception) {
                    Log.e("TwoDaysWidgetReceiver", "更新失败", e)
                }
            }

            // 调度更新任务
            try {
                if (!WidgetUpdateJobService.isJobScheduled(context)) {
                    WidgetUpdateJobService.scheduleJob(context)
                }
            } catch (e: Exception) {
                Log.e("TwoDaysWidgetReceiver", "调度任务失败", e)
            }

        } catch (e: Exception) {
            Log.e("TwoDaysWidgetReceiver", "onUpdate失败", e)
        }
    }
}
