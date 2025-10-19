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

enum class CourseStatus {
    ACTIVE,
    ENDED;
    
    companion object {
        fun fromString(status: String?): CourseStatus {
            return when (status) {
                "已结束" -> ENDED
                else -> ACTIVE
            }
        }
    }
}

val KEY_COURSES_JSON = stringPreferencesKey("courses_json")
val KEY_UPDATE_TIME = stringPreferencesKey("update_time")

private val jsonParser = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = false
}

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
    val location: String = "",
    val teacher: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val status: String? = null,
    val color: String = "primary",
    val courseStatus: CourseStatus = CourseStatus.ACTIVE
)

class TwoDaysWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d("TwoDaysWidget", "provideGlance called for glanceId: $id")

        try {
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

        val widgetData = try {
            if (coursesJson != null) {
                jsonParser.decodeFromString<WidgetCourseData>(coursesJson)
            } else {
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
            if (widgetData.today.courses.isEmpty() && widgetData.tomorrow.courses.isEmpty()) {
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
                Row(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                ) {
                    DayColumn(
                        title = "今天",
                        dayData = widgetData.today,
                        isToday = true,
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(end = 6.dp)
                    )

                    DayColumn(
                        title = "明天",
                        dayData = widgetData.tomorrow,
                        isToday = false,
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(start = 6.dp)
                    )
                }
            }

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
                val activeCourses = dayData.courses.filter { it.courseStatus != CourseStatus.ENDED }
                
                if (activeCourses.isNotEmpty()) {
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

            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(start = 8.dp)
            ) {
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
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        ),
                        maxLines = 1
                    )
                }

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

                val currentTimetableId = dao.getPreferenceFlow(Constants.PREF_CURRENT_TIMETABLE)
                    .firstOrNull()?.toLongOrNull() ?: return

                val timetable = dao.getTimetableFlow(currentTimetableId).firstOrNull() ?: return

                val today = LocalDate.now()
                val startDate = LocalDate.parse(timetable.startDate)
                val currentWeekNumber = today.getWeekOfSemester(startDate)

                val courses = dao.getCoursesFlow(currentTimetableId).firstOrNull() ?: emptyList()
                val lessonTimes = dao.getLessonTimesFlow(currentTimetableId).firstOrNull() ?: emptyList()

                val thisWeekCourses = courses.filter { it.weeks.contains(currentWeekNumber) }

                val todayCourses = thisWeekCourses.filter { it.dayOfWeek == today.dayOfWeek.value }
                val tomorrow = today.plusDays(1)
                val tomorrowCourses = thisWeekCourses.filter { it.dayOfWeek == tomorrow.dayOfWeek.value }

                val now = LocalDateTime.now()
                val currentTime = now.toLocalTime()
                val updateTimeStr = now.format(DateTimeFormatter.ofPattern("HH:mm"))

                val todayCourseItems = todayCourses
                    .sortedBy { it.periods.minOrNull() ?: 0 }
                    .map { course ->
                        val startPeriod = course.periods.minOrNull() ?: 0
                        val endPeriod = course.periods.maxOrNull() ?: 0
                        val startLesson = lessonTimes.find { it.period == startPeriod }
                        val endLesson = lessonTimes.find { it.period == endPeriod }

                        val startTime = startLesson?.startTime ?: ""
                        val endTime = endLesson?.endTime ?: ""
                        val status = getCourseStatus(startTime, endTime, currentTime)
                        
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
                            courseStatus = CourseStatus.ACTIVE
                        )
                    }

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

                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(TwoDaysWidget::class.java)

                if (glanceIds.isEmpty()) return

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
            val validAppWidgetIds = appWidgetIds.filter { appWidgetId ->
                try {
                    appWidgetManager.getAppWidgetInfo(appWidgetId) != null
                } catch (e: Exception) {
                    Log.e("TwoDaysWidgetReceiver", "获取AppWidgetInfo失败", e)
                    false
                }
            }

            if (validAppWidgetIds.isEmpty()) return

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
