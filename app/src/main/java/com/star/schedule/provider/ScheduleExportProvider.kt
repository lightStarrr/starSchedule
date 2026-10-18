package com.star.schedule.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.net.toUri
import androidx.room.InvalidationTracker
import com.star.schedule.core.common.Constants
import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.core.database.LessonTimeEntity
import com.star.schedule.core.database.TimetableEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class ScheduleExportProvider : ContentProvider() {
    private val handler = Handler(Looper.getMainLooper())
    private val secondRefresh = Runnable { notifyWakeUpRefresh() }
    private val boundaryRefresh = Runnable { notifyWakeUpRefresh() }
    private val firstRefresh = Runnable {
        notifyWakeUpRefresh()
        handler.postDelayed(secondRefresh, SECOND_REFRESH_DELAY_MS)
    }

    private lateinit var databaseObserver: InvalidationTracker.Observer

    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return false
        DatabaseProvider.init(appContext)
        databaseObserver = object : InvalidationTracker.Observer(
            "course",
            "lesson_time",
            "timetable",
            "preference"
        ) {
            override fun onInvalidated(tables: Set<String>) {
                handler.removeCallbacks(firstRefresh)
                handler.removeCallbacks(secondRefresh)
                handler.postDelayed(firstRefresh, REFRESH_DEBOUNCE_MS)
            }
        }
        DatabaseProvider.db.invalidationTracker.addObserver(databaseObserver)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = when (uri.pathSegments.firstOrNull()) {
        "has_init" -> oneRow(if (isWakeUpSimulationEnabled()) "{\"has_init\":true}" else "{\"has_init\":false}")
        "show_table_id" -> oneRow(if (isWakeUpSimulationEnabled()) {
            databaseJson("{\"table_id\":1}") {
                "{\"table_id\":${currentTimetableId()}}"
            }
        } else "{\"table_id\":0}")
        "table_list" -> oneRow(if (isWakeUpSimulationEnabled()) {
            databaseJson("[{\"id\":1,\"tableName\":\"星课程表\"}]") {
                buildJsonArray {
                    DatabaseProvider.dao().getAllTimetablesOnce().forEach { timetable ->
                        add(buildJsonObject {
                            put("id", timetable.id)
                            put("tableName", timetable.name)
                        })
                    }
                }.toString()
            }
        } else "[]")
        "course_list" -> oneRow(if (isWakeUpSimulationEnabled()) courseJson(uri, tomorrow = false) else "[]")
        "next_course_list" -> oneRow(if (isWakeUpSimulationEnabled()) courseJson(uri, tomorrow = true) else "[]")
        "refresh" -> null
        else -> null
    }

    private fun courseJson(uri: Uri, tomorrow: Boolean): String = databaseJson("[]") {
        val dao = DatabaseProvider.dao()
        val timetableId = currentTimetableId()
        val timetable = dao.getTimetableFlow(timetableId).first()
        val courses = dao.getCoursesFlow(timetableId).first()
        val lessonTimes = dao.getLessonTimesFlow(timetableId).first()
        val zoneId = ZoneId.systemDefault()
        val now = LocalDateTime.now(zoneId)
        val date = requestedDate(uri).let { if (tomorrow) it.plusDays(1) else it }
        // 边界按「今天」算，与本次请求的是哪一天无关：course_list 与 next_course_list 都会走到这里，
        // 用请求日期算会互相把对方排好的刷新取消掉。今天没有课了就是 null，等于清掉旧的回调。
        scheduleBoundaryRefresh(
            WakeUpScheduleMapper.nextEndTime(
                date = LocalDate.now(zoneId),
                timetable = timetable,
                courses = courses,
                lessonTimes = lessonTimes,
                zoneId = zoneId,
                now = now
            )
        )
        WakeUpScheduleMapper.toJson(date, timetable, courses, lessonTimes, zoneId, now)
    }

    /**
     * 下课不会改数据库，系统不会自己重查，已经结束的课会一直挂在胶囊里；
     * 这里按最近的一次下课时刻补一发刷新，让它重新拉取。
     */
    private fun scheduleBoundaryRefresh(endAt: LocalDateTime?) {
        handler.removeCallbacks(boundaryRefresh)
        endAt ?: return
        val zoneId = ZoneId.systemDefault()
        val delayMs = Duration.between(ZonedDateTime.now(zoneId), endAt.atZone(zoneId)).toMillis()
        if (delayMs < 0) return
        handler.postDelayed(boundaryRefresh, delayMs + BOUNDARY_SLACK_MS)
    }

    private fun requestedDate(uri: Uri): LocalDate {
        val suffix = uri.pathSegments.drop(1).lastOrNull() ?: return LocalDate.now()
        if (suffix.any { !it.isDigit() }) return LocalDate.now()
        return runCatching {
            when (suffix.length) {
                8 -> LocalDate.parse(suffix, DateTimeFormatter.BASIC_ISO_DATE)
                10 -> Instant.ofEpochSecond(suffix.toLong()).atZone(ZoneId.systemDefault()).toLocalDate()
                13 -> Instant.ofEpochMilli(suffix.toLong()).atZone(ZoneId.systemDefault()).toLocalDate()
                else -> LocalDate.now()
            }
        }.getOrDefault(LocalDate.now())
    }

    private suspend fun currentTimetableId(): Long {
        val dao = DatabaseProvider.dao()
        return dao.getPreferenceFlow(Constants.PREF_CURRENT_TIMETABLE).first()?.toLongOrNull()
            ?: dao.getAllTimetablesOnce().firstOrNull()?.id
            ?: 1L
    }

    private fun isWakeUpSimulationEnabled(): Boolean = runCatching {
        runBlocking(Dispatchers.IO) {
            DatabaseProvider.dao()
                .getPreferenceFlow(Constants.PREF_WAKEUP_SIMULATION_ENABLED)
                .first() == "true"
        }
    }.getOrDefault(false)

    private fun databaseJson(fallback: String, block: suspend () -> String): String = try {
        runBlocking(Dispatchers.IO) { block() }
    } catch (error: Exception) {
        Log.e(TAG, "读取课程数据失败", error)
        fallback
    }

    private fun oneRow(data: String): Cursor = MatrixCursor(arrayOf("code", "data")).apply {
        addRow(arrayOf<Any>(0, data))
    }

    private fun notifyWakeUpRefresh() {
        val resolver = context?.contentResolver ?: return
        val providerExists = context?.packageManager
            ?.resolveContentProvider(WAKE_UP_AUTHORITY, 0) != null
        if (!providerExists) {
            Log.d(TAG, "WakeUp 代理未安装，跳过刷新通知")
            return
        }
        try {
            resolver.notifyChange(WAKE_UP_REFRESH_URI, null)
        } catch (error: SecurityException) {
            Log.w(TAG, "WakeUp 代理不可用，跳过刷新通知", error)
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri =
        throw UnsupportedOperationException("read-only provider")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("read-only provider")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = throw UnsupportedOperationException("read-only provider")

    companion object {
        private const val TAG = "ScheduleExportProvider"
        private const val REFRESH_DEBOUNCE_MS = 250L
        private const val SECOND_REFRESH_DELAY_MS = 1_000L
        private const val BOUNDARY_SLACK_MS = 1_000L
        private const val WAKE_UP_AUTHORITY = "com.suda.yzune.wakeupschedule.provider"
        private val WAKE_UP_REFRESH_URI = "content://$WAKE_UP_AUTHORITY/refresh".toUri()
    }
}

internal object WakeUpScheduleMapper {
    private const val COLOR = "#ff3f8cff"
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

    fun toJson(
        date: LocalDate,
        timetable: TimetableEntity?,
        courses: List<CourseEntity>,
        lessonTimes: List<LessonTimeEntity>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        now: LocalDateTime = LocalDateTime.now(zoneId)
    ): String {
        return buildJsonArray {
            exportedCourses(date, timetable, courses, lessonTimes, now).forEach { course ->
                add(buildJsonObject {
                    put("id", course.id)
                    put("courseName", course.name)
                    put("room", course.room)
                    put("teacher", course.teacher)
                    put("startTime", course.startTime.format(TIME_FORMATTER))
                    put("endTime", course.endTime.format(TIME_FORMATTER))
                    put("color", COLOR)
                    put("extra", "")
                    put("startTimestamp", date.atTime(course.startTime).atZone(zoneId).toEpochSecond())
                    put("endTimestamp", date.atTime(course.endTime).atZone(zoneId).toEpochSecond())
                })
            }
        }.toString()
    }

    /**
     * 今天还没下课的课程里最早的结束时刻，用来在边界发一次刷新；非今天的日期没有边界可等，返回 null。
     */
    fun nextEndTime(
        date: LocalDate,
        timetable: TimetableEntity?,
        courses: List<CourseEntity>,
        lessonTimes: List<LessonTimeEntity>,
        zoneId: ZoneId = ZoneId.systemDefault(),
        now: LocalDateTime = LocalDateTime.now(zoneId)
    ): LocalDateTime? {
        if (date != now.toLocalDate()) return null
        return exportedCourses(date, timetable, courses, lessonTimes, now)
            .minByOrNull { it.endTime }
            ?.let { date.atTime(it.endTime) }
    }

    /**
     * 只有当天的课表会剔除已经结束的课，保证模拟数据里只剩「正在上」和「还没上」的课；
     * 未来日期整天的课都还没开始，原样返回。
     */
    private fun exportedCourses(
        date: LocalDate,
        timetable: TimetableEntity?,
        courses: List<CourseEntity>,
        lessonTimes: List<LessonTimeEntity>,
        now: LocalDateTime
    ): List<ExportedCourse> {
        val startDate = timetable?.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return emptyList()
        val week = Math.floorDiv(ChronoUnit.DAYS.between(startDate, date), 7L).toInt() + 1
        if (week < 1) return emptyList()

        val isToday = date == now.toLocalDate()
        val nowTime = now.toLocalTime()
        val timesByPeriod = lessonTimes.associateBy { it.period }
        return courses.asSequence()
            .filter { it.dayOfWeek == date.dayOfWeek.value && week in it.weeks }
            .flatMap { course ->
                contiguousGroups(course.periods).mapNotNull { periods ->
                    exportCourse(course, periods, timesByPeriod)
                }.asSequence()
            }
            .filter { !isToday || it.endTime.isAfter(nowTime) }
            .sortedWith(compareBy<ExportedCourse> { it.startTime }.thenBy { it.id })
            .toList()
    }

    private fun exportCourse(
        course: CourseEntity,
        periods: List<Int>,
        timesByPeriod: Map<Int, LessonTimeEntity>
    ): ExportedCourse? {
        if (periods.any { it !in timesByPeriod }) return null
        val startTime = runCatching { LocalTime.parse(timesByPeriod.getValue(periods.first()).startTime) }
            .getOrNull() ?: return null
        val endTime = runCatching { LocalTime.parse(timesByPeriod.getValue(periods.last()).endTime) }
            .getOrNull() ?: return null
        if (!endTime.isAfter(startTime)) return null

        val stableId = runCatching {
            Math.addExact(Math.multiplyExact(course.id, 1_000L), periods.first().toLong())
        }.getOrNull() ?: return null
        return ExportedCourse(
            id = stableId,
            name = course.name,
            room = course.location,
            teacher = course.teacher,
            startTime = startTime,
            endTime = endTime
        )
    }

    private fun contiguousGroups(periods: List<Int>): List<List<Int>> {
        val sorted = periods.distinct().sorted()
        if (sorted.isEmpty()) return emptyList()
        val groups = mutableListOf<MutableList<Int>>()
        sorted.forEach { period ->
            val current = groups.lastOrNull()
            if (current == null || period != current.last() + 1) {
                groups += mutableListOf(period)
            } else {
                current += period
            }
        }
        return groups
    }

    private data class ExportedCourse(
        val id: Long,
        val name: String,
        val room: String,
        val teacher: String,
        val startTime: LocalTime,
        val endTime: LocalTime
    )
}
