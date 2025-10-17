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
import com.star.schedule.widget.TwoDaysWidget.Companion.updateWidgetContent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Widget状态键
val KEY_COURSES_DATA = stringPreferencesKey("courses_data")
val KEY_UPDATE_TIME = stringPreferencesKey("update_time")
val KEY_COURSES_JSON = stringPreferencesKey("courses_json")

// JSON解析器
private val jsonParser = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

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

// JSON数据模型
@Serializable
data class WidgetCourseData(
    val today: DayCourses,
    val tomorrow: DayCourses,
    val updateTime: String
)

@Serializable
data class DayCourses(
    val hasCourses: Boolean,
    val courses: List<CourseItem>,
    val noCourseMessage: String = ""
)

@Serializable
data class CourseItem(
    val name: String,
    val location: String,
    val teacher: String,
    val startTime: String,
    val endTime: String,
    val status: String? = null,
    val statusColor: String = "primary"
)

class TwoDaysWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d("TwoDaysWidget", "provideGlance called for glanceId: $id")

        try {
            // 确保数据库已初始化（不在协程作用域内执行延迟操作）
            if (!DatabaseProvider.isInitialized()) {
                DatabaseProvider.init(context)
                Log.d("TwoDaysWidget", "Database initialized for glanceId: $id")
            }

            provideContent {
                GlanceTheme {
                    Content()
                }
            }

        } catch (e: Exception) {
            Log.e("TwoDaysWidget", "Error in provideGlance for glanceId: $id", e)

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
        val coursesJson = prefs[KEY_COURSES_JSON]
        val lastUpdate = prefs[KEY_UPDATE_TIME] ?: ""

        // 解析JSON数据
        val widgetData = try {
            if (coursesJson != null) {
                jsonParser.decodeFromString<WidgetCourseData>(coursesJson)
            } else {
                // 如果没有JSON数据，尝试回退到旧的字符串格式
                val coursesData = prefs[KEY_COURSES_DATA] ?: "暂无课程信息"
                parseLegacyData(coursesData, lastUpdate)
            }
        } catch (e: Exception) {
            // JSON解析失败，回退到旧格式
            val coursesData = prefs[KEY_COURSES_DATA] ?: "暂无课程信息"
            parseLegacyData(coursesData, lastUpdate)
        }

        // 顶层 Column：上面是两列内容（占满剩余高度），下面是更新时间（固定在底部）
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.background)
                .padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (!widgetData.today.hasCourses && !widgetData.tomorrow.hasCourses) {
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
                        .defaultWeight(),
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

                        if (widgetData.today.hasCourses) {
                            var first = true
                            LazyColumn(modifier = GlanceModifier.fillMaxHeight()) {
                                items(widgetData.today.courses.size) { index ->
                                    val course = widgetData.today.courses[index]
                                    if (course.status == "已结束") {
                                        return@items
                                    }
                                    val topPadding = if (first) {
                                        0.dp
                                    } else {
                                        first = false
                                        8.dp
                                    }

                                    Row(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .padding(top = topPadding)
                                    ) {
                                        Box(
                                            modifier = GlanceModifier
                                                .width(5.dp)
                                                .fillMaxHeight()
                                                .background(GlanceTheme.colors.primary)
                                                .cornerRadius(5.dp),
                                            content = {},
                                        )
                                        Box(
                                            modifier = GlanceModifier
                                                .fillMaxWidth()
                                                .padding(8.dp)
                                                .cornerRadius(12.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = course.name,
                                                    style = TextStyle(
                                                        fontSize = 14.sp,
                                                        color = GlanceTheme.colors.onSurface,
                                                        fontWeight = FontWeight.Medium
                                                    ),
                                                    maxLines = 1
                                                )
                                                if (course.location.isNotBlank()) {
                                                    Text(
                                                        text = course.location,
                                                        style = TextStyle(
                                                            fontSize = 12.sp,
                                                            color = GlanceTheme.colors.onSurfaceVariant
                                                        ),
                                                        maxLines = 1
                                                    )
                                                }
                                                if (course.startTime.isNotBlank() && course.endTime.isNotBlank()) {
                                                    Text(
                                                        text = "${course.startTime} - ${course.endTime}",
                                                        style = TextStyle(
                                                            fontSize = 12.sp,
                                                            color = GlanceTheme.colors.onSurfaceVariant
                                                        ),
                                                        maxLines = 1
                                                    )
                                                } else {
                                                    Text(
                                                        text = "未设置时间",
                                                        style = TextStyle(
                                                            fontSize = 12.sp,
                                                            color = GlanceTheme.colors.onSurfaceVariant
                                                        ),
                                                        maxLines = 1
                                                    )

                                                }

                                                if (course.status != null) {
                                                    Text(
                                                        text = course.status,
                                                        style = TextStyle(
                                                            fontSize = 12.sp,
                                                            color = GlanceTheme.colors.primary,
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        maxLines = 1
                                                    )
                                                }
                                            }
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

                        if (widgetData.tomorrow.hasCourses) {
                            var first = true
                            LazyColumn(modifier = GlanceModifier.fillMaxHeight()) {
                                items(widgetData.tomorrow.courses.size) { index ->
                                    val course = widgetData.tomorrow.courses[index]
                                    val topPadding = if (first) {
                                        0.dp
                                    } else {
                                        first = false
                                        8.dp
                                    }

                                    Row(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .padding(top = topPadding)
                                    ) {
                                        Box(
                                            modifier = GlanceModifier
                                                .width(5.dp)
                                                .fillMaxHeight()
                                                .background(GlanceTheme.colors.secondary)
                                                .cornerRadius(5.dp),
                                            content = {},
                                        )
                                        Box(
                                            modifier = GlanceModifier
                                                .fillMaxWidth()
                                                .padding(8.dp)
                                                .cornerRadius(12.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = course.name,
                                                    style = TextStyle(
                                                        fontSize = 14.sp,
                                                        color = GlanceTheme.colors.onSurface,
                                                        fontWeight = FontWeight.Medium
                                                    ),
                                                    maxLines = 1
                                                )
                                                if (course.location.isNotBlank()) {
                                                    Text(
                                                        text = course.location,
                                                        style = TextStyle(
                                                            fontSize = 12.sp,
                                                            color = GlanceTheme.colors.onSurfaceVariant
                                                        ),
                                                        maxLines = 1
                                                    )
                                                }
                                                if (course.startTime.isNotBlank() && course.endTime.isNotBlank()) {
                                                    Text(
                                                        text = "${course.startTime} - ${course.endTime}",
                                                        style = TextStyle(
                                                            fontSize = 12.sp,
                                                            color = GlanceTheme.colors.onSurfaceVariant
                                                        ),
                                                        maxLines = 1
                                                    )
                                                } else {
                                                    Text(
                                                        text = "未设置时间",
                                                        style = TextStyle(
                                                            fontSize = 12.sp,
                                                            color = GlanceTheme.colors.onSurfaceVariant
                                                        ),
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
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
                if (widgetData.updateTime.isNotBlank()) "最后更新时间：${widgetData.updateTime}" else "最后更新时间：--:--"
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

    private fun parseLegacyData(coursesData: String, updateTime: String): WidgetCourseData {
        // 拆分今天和明天课程（保持原有逻辑）
        val lines = coursesData.lines()
        val todayCourses = mutableListOf<String>()
        val tomorrowCourses = mutableListOf<String>()
        var currentDay = ""
        lines.forEach { line ->
            when {
                line.startsWith("今天:") -> {
                    currentDay = "today"
                    if (todayCourses.isNotEmpty()) todayCourses.clear()
                }

                line.startsWith("明天:") -> {
                    currentDay = "tomorrow"
                    if (tomorrowCourses.isNotEmpty()) tomorrowCourses.clear()
                }

                line.isNotBlank() -> {
                    if (currentDay == "today" && !line.startsWith("今天:") && !line.startsWith("明天:")) {
                        todayCourses.add(line)
                    } else if (currentDay == "tomorrow" && !line.startsWith("今天:") && !line.startsWith(
                            "明天:"
                        )
                    ) {
                        tomorrowCourses.add(line)
                    }
                }
            }
        }

        val hasTodayCourses =
            todayCourses.isNotEmpty() && todayCourses.firstOrNull() != "今天无课" && !todayCourses.all { it == "今天无课" }
        val hasTomorrowCourses =
            tomorrowCourses.isNotEmpty() && tomorrowCourses.firstOrNull() != "明天无课" && !tomorrowCourses.all { it == "明天无课" }

        val todayCourseItems = if (hasTodayCourses) {
            todayCourses.map { line ->
                val parts = line.split(" ")
                val timeRange = parts.getOrElse(0) { "" }
                val courseName = parts.getOrElse(1) { "" }
                val location = parts.find { part -> part.startsWith("@") }?.substring(1) ?: ""
                val teacher = parts.find { part -> part.startsWith("(") && part.endsWith(")") }
                    ?.let { p -> p.substring(1, p.length - 1) } ?: ""
                val status = parts.find { part -> part.startsWith("【") && part.endsWith("】") }
                    ?.let { p -> p.substring(1, p.length - 1) }

                CourseItem(
                    name = courseName,
                    location = location,
                    teacher = teacher,
                    startTime = timeRange.split("-").getOrElse(0) { "" },
                    endTime = timeRange.split("-").getOrElse(1) { "" },
                    status = status
                )
            }
        } else {
            emptyList()
        }

        val tomorrowCourseItems = if (hasTomorrowCourses) {
            tomorrowCourses.map { line ->
                val parts = line.split(" ")
                val timeRange = parts.getOrElse(0) { "" }
                val courseName = parts.getOrElse(1) { "" }
                val location = parts.find { part -> part.startsWith("@") }?.substring(1) ?: ""
                val teacher = parts.find { part -> part.startsWith("(") && part.endsWith(")") }
                    ?.let { p -> p.substring(1, p.length - 1) } ?: ""

                CourseItem(
                    name = courseName,
                    location = location,
                    teacher = teacher,
                    startTime = timeRange.split("-").getOrElse(0) { "" },
                    endTime = timeRange.split("-").getOrElse(1) { "" }
                )
            }
        } else {
            emptyList()
        }

        return WidgetCourseData(
            today = DayCourses(
                hasCourses = hasTodayCourses,
                courses = todayCourseItems,
                noCourseMessage = "今天没课哟~"
            ),
            tomorrow = DayCourses(
                hasCourses = hasTomorrowCourses,
                courses = tomorrowCourseItems,
                noCourseMessage = "明天没课哟~"
            ),
            updateTime = updateTime
        )
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
                val updateTimeStr = now.format(DateTimeFormatter.ofPattern("HH:mm"))

                // 构建今天的课程数据
                val todayCourseItems = if (todayCourses.isNotEmpty()) {
                    todayCourses.sortedBy { it.periods.minOrNull() ?: 0 }.map { course ->
                        val startPeriod = course.periods.minOrNull() ?: 0
                        val endPeriod = course.periods.maxOrNull() ?: 0
                        val startLesson = lessonTimes.find { it.period == startPeriod }
                        val endLesson = lessonTimes.find { it.period == endPeriod }

                        val startTime = startLesson?.startTime ?: ""
                        val endTime = endLesson?.endTime ?: ""

                        val status = getCourseStatus(course, startTime, endTime, currentTime)
                        val statusText = when (status) {
                            is CourseStatus.BeforeClass -> status.timeUntilStart
                            is CourseStatus.DuringClass -> status.timeUntilEnd
                            is CourseStatus.AfterClass -> "已结束"
                            else -> null
                        }

                        CourseItem(
                            name = course.name,
                            location = course.location,
                            teacher = course.teacher,
                            startTime = startTime,
                            endTime = endTime,
                            status = statusText,
                            statusColor = when (status) {
                                is CourseStatus.BeforeClass -> "primary"
                                is CourseStatus.DuringClass -> "secondary"
                                is CourseStatus.AfterClass -> "tertiary"
                                else -> "primary"
                            }
                        )
                    }
                } else {
                    emptyList()
                }

                // 构建明天的课程数据（限制显示2门课程）
                val tomorrowCourseItems = if (tomorrowCourses.isNotEmpty()) {
                    tomorrowCourses.sortedBy { it.periods.minOrNull() ?: 0 }.take(2).map { course ->
                        val startPeriod = course.periods.minOrNull() ?: 0
                        val endPeriod = course.periods.maxOrNull() ?: 0
                        val startLesson = lessonTimes.find { it.period == startPeriod }
                        val endLesson = lessonTimes.find { it.period == endPeriod }

                        val startTime = startLesson?.startTime ?: ""
                        val endTime = endLesson?.endTime ?: ""

                        CourseItem(
                            name = course.name,
                            location = course.location,
                            teacher = course.teacher,
                            startTime = startTime,
                            endTime = endTime
                        )
                    }
                } else {
                    emptyList()
                }

                // 构建JSON数据
                val widgetData = WidgetCourseData(
                    today = DayCourses(
                        hasCourses = todayCourseItems.isNotEmpty(),
                        courses = todayCourseItems,
                        noCourseMessage = "今天没课哟~"
                    ),
                    tomorrow = DayCourses(
                        hasCourses = tomorrowCourseItems.isNotEmpty(),
                        courses = tomorrowCourseItems,
                        noCourseMessage = "明天没课哟~"
                    ),
                    updateTime = updateTimeStr
                )

                val coursesJson =
                    jsonParser.encodeToString(WidgetCourseData.serializer(), widgetData)

                // 为了向后兼容，同时生成旧的字符串格式
                val widgetContent = buildString {
                    if (todayCourseItems.isNotEmpty()) {
                        appendLine("今天:")
                        todayCourseItems.forEach { course ->
                            val baseInfo = "${course.startTime}-${course.endTime} ${course.name}"
                            val locationInfo =
                                if (course.location.isNotBlank()) " @${course.location}" else ""
                            val teacherInfo =
                                if (course.teacher.isNotBlank()) " (${course.teacher})" else ""
                            val statusInfo = course.status ?: ""
                            appendLine("$baseInfo$locationInfo$teacherInfo $statusInfo")
                        }
                    }
                    appendLine()
                    if (tomorrowCourseItems.isNotEmpty()) {
                        appendLine("明天:")
                        tomorrowCourseItems.forEach { course ->
                            val baseInfo = "${course.startTime}-${course.endTime} ${course.name}"
                            val locationInfo =
                                if (course.location.isNotBlank()) " @${course.location}" else ""
                            val teacherInfo =
                                if (course.teacher.isNotBlank()) " (${course.teacher})" else ""
                            appendLine("$baseInfo$locationInfo$teacherInfo")
                        }
                    }
                }

                // 更新所有widget实例
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(TwoDaysWidget::class.java)

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
                            mutable[KEY_COURSES_DATA] = widgetContent  // 保持旧格式兼容
                            mutable[KEY_COURSES_JSON] = coursesJson  // 新的JSON格式
                            mutable[KEY_UPDATE_TIME] = updateTimeStr
                            mutable
                        }
                    } catch (e: Exception) {
                        // 单个widget实例更新失败，继续处理其他实例
                        e.printStackTrace()
                    }
                }

                try {
                    TwoDaysWidget().updateAll(context)
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

class TwoDaysWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TwoDaysWidget()

    @OptIn(DelicateCoroutinesApi::class)
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        GlobalScope.launch(Dispatchers.IO) {
            updateWidgetContent(context)
        }
        if (com.star.schedule.service.WidgetUpdateJobService.isJobScheduled(context)) {
            Log.d("TwoDaysWidgetReceiver", "Widget update job is already scheduled.")
        } else {
            Log.d("TwoDaysWidgetReceiver", "Scheduling widget update job.")
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
            "TwoDaysWidgetReceiver",
            "onUpdate called with ${appWidgetIds.size} widget IDs: ${appWidgetIds.joinToString()}"
        )

        try {
            // 过滤掉无效的小组件ID
            val validAppWidgetIds = appWidgetIds.filter { appWidgetId ->
                try {
                    val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
                    val isValid = appWidgetInfo != null
                    Log.d(
                        "TwoDaysWidgetReceiver",
                        "Widget ID $appWidgetId validity check: $isValid"
                    )
                    isValid
                } catch (e: Exception) {
                    Log.w(
                        "TwoDaysWidgetReceiver",
                        "Error checking widget ID $appWidgetId validity",
                        e
                    )
                    false
                }
            }

            Log.d(
                "TwoDaysWidgetReceiver",
                "Valid widget IDs: ${validAppWidgetIds.size} out of ${appWidgetIds.size}"
            )

            // 异步处理每个小组件的更新，避免阻塞
            validAppWidgetIds.forEach { appWidgetId ->
                try {
                    Log.d("TwoDaysWidgetReceiver", "Starting async update for widget: $appWidgetId")

                    // 为每个小组件创建独立的更新任务
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            // 确保数据库已初始化
                            if (!DatabaseProvider.isInitialized()) {
                                DatabaseProvider.init(context)
                                Log.d(
                                    "TwoDaysWidgetReceiver",
                                    "Database initialized for widget: $appWidgetId"
                                )
                            }

                            // 更新小组件内容
                            updateWidgetContent(context)

                            Log.d(
                                "TwoDaysWidgetReceiver",
                                "Async update completed for widget: $appWidgetId"
                            )
                        } catch (e: Exception) {
                            Log.e(
                                "TwoDaysWidgetReceiver",
                                "Async update failed for widget: $appWidgetId",
                                e
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(
                        "TwoDaysWidgetReceiver",
                        "Failed to start async update for widget: $appWidgetId",
                        e
                    )
                }
            }

            // 只对有效的小组件ID调用父类方法
            if (validAppWidgetIds.isNotEmpty()) {
                Log.d(
                    "TwoDaysWidgetReceiver",
                    "Calling super.onUpdate with ${validAppWidgetIds.size} valid widget IDs"
                )
                super.onUpdate(context, appWidgetManager, validAppWidgetIds.toIntArray())
                Log.d("TwoDaysWidgetReceiver", "super.onUpdate completed successfully")
            } else {
                // 如果没有有效的小组件ID，跳过更新操作
                Log.w("TwoDaysWidgetReceiver", "No valid app widget IDs found, skipping update")
            }
        } catch (e: Exception) {
            Log.e("TwoDaysWidgetReceiver", "Error in onUpdate", e)
        }

        try {
            if (!com.star.schedule.service.WidgetUpdateJobService.isJobScheduled(context)) {
                Log.d("TwoDaysWidgetReceiver", "Scheduling JobScheduler task")
                com.star.schedule.service.WidgetUpdateJobService.scheduleJob(context)
            } else {
                Log.d("TwoDaysWidgetReceiver", "JobScheduler task already scheduled")
            }
        } catch (e: Exception) {
            Log.e("TwoDaysWidgetReceiver", "Error scheduling JobScheduler task", e)
        }

        Log.d("TwoDaysWidgetReceiver", "onUpdate completed")
    }
}
