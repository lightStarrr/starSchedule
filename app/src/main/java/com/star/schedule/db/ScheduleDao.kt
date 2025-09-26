package com.star.schedule.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.Update
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

// NotificationManager 接口
interface NotificationManagerProvider {
    suspend fun enableRemindersForTimetable(timetableId: Long)
}

// ---------- DAO ----------
@Dao
abstract class ScheduleDao {
    // 将 notificationManager 设为可空变量，通过 setter 注入
    var notificationManager: NotificationManagerProvider? = null

    // ------------------ 偏好设置 ------------------
    @Query("SELECT value FROM preference WHERE prefKey = :prefKey LIMIT 1")
    abstract fun getPreferenceFlow(prefKey: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPreference(preference: PreferenceEntity)

    @Transaction
    open suspend fun setPreference(key: String, value: String) {
        insertPreference(PreferenceEntity(key, value))
    }

    // ------------------ 课程表 ------------------
    @Query("SELECT * FROM timetable ORDER BY id ASC")
    abstract fun getAllTimetables(): Flow<List<TimetableEntity>>

    @Query("SELECT * FROM timetable WHERE id = :id LIMIT 1")
    abstract fun getTimetableFlow(id: Long): Flow<TimetableEntity?>

    @Insert
    abstract suspend fun insertTimetable(timetable: TimetableEntity): Long

    @Update
    abstract suspend fun updateTimetable(timetable: TimetableEntity)

    @Delete
    abstract suspend fun deleteTimetable(timetable: TimetableEntity)

    @Query("SELECT * FROM timetable ORDER BY id ASC")
    abstract suspend fun getAllTimetablesOnce(): List<TimetableEntity>

    // 自动初始化默认课表
    @Transaction
    open suspend fun initializeDefaultTimetable(): Long {
        val prefIdStr = getPreferenceFlow("current_timetable")
            .map { it?.toLongOrNull() }
            .firstOrNull()
        if (prefIdStr != null) return prefIdStr

        val timetables = getAllTimetablesOnce()
        val timetableId = if (timetables.isEmpty()) {
            insertTimetable(
                TimetableEntity(
                    name = "默认课表",
                    showWeekend = true,
                    startDate = LocalDate.now().toString()
                )
            )
        } else {
            timetables.first().id
        }
        setPreference("current_timetable", timetableId.toString())
        return timetableId
    }

    // ------------------ 课时间 ------------------
    @Query("SELECT * FROM lesson_time WHERE timetableId = :timetableId ORDER BY period ASC")
    abstract fun getLessonTimesFlow(timetableId: Long): Flow<List<LessonTimeEntity>>

    @Insert
    abstract suspend fun insertLessonTime(lessonTime: LessonTimeEntity): Long

    @Update
    abstract suspend fun updateLessonTime(lessonTime: LessonTimeEntity)

    @Delete
    abstract suspend fun deleteLessonTime(lessonTime: LessonTimeEntity)

    // ------------------ 课程 ------------------
    @Query("SELECT * FROM course WHERE timetableId = :timetableId ORDER BY dayOfWeek ASC")
    abstract fun getCoursesFlow(timetableId: Long): Flow<List<CourseEntity>>

    @OptIn(ExperimentalCoroutinesApi::class)
    open fun getCoursesForDateFlow(
        timetableId: Long,
        date: LocalDate? = null
    ): Flow<List<CourseEntity>> {
        val timetableFlow = getTimetableFlow(timetableId)
        val coursesFlow = getCoursesFlow(timetableId)
        return combine(timetableFlow, coursesFlow) { timetable, courses ->
            val startDate = timetable?.startDate?.let { LocalDate.parse(it) } ?: LocalDate.now()
            val weekNumber = date?.getWeekOfSemester(startDate) ?: 0
            if (weekNumber == 0) courses else courses.filter { it.weeks.contains(weekNumber) }
        }
    }

    @Insert
    abstract suspend fun insertCourse(course: CourseEntity): Long

    @Update
    abstract suspend fun updateCourse(course: CourseEntity)

    @Delete
    abstract suspend fun deleteCourse(course: CourseEntity)

    // ---------- 提醒 ----------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertReminder(reminder: ReminderEntity)

    @Query("SELECT * FROM reminder")
    abstract suspend fun getAllReminders(): List<ReminderEntity>

    @Query("DELETE FROM reminder WHERE requestCode = :requestCode")
    abstract suspend fun deleteReminder(requestCode: Int)

    @Query("DELETE FROM reminder")
    abstract suspend fun deleteAllReminders()

    // ------------------ 自动排序和提醒 ------------------
    private suspend fun checkAndEnableReminders(timetableId: Long) {
        val currentId = getPreferenceFlow("current_timetable").firstOrNull()?.toLongOrNull()
        android.util.Log.d("ScheduleDao", "checkAndEnableReminders: timetableId=$timetableId, currentId=$currentId, notificationManager=$notificationManager")
        if (currentId == timetableId) {
            notificationManager?.enableRemindersForTimetable(currentId)
        }
    }

    // ---------- 课时操作 ----------
    @Transaction
    open suspend fun insertOrUpdateLessonTimeAutoSort(
        lessonTime: LessonTimeEntity,
        isInsert: Boolean = true
    ): Long {
        val id = if (isInsert) insertLessonTime(lessonTime) else {
            updateLessonTime(lessonTime); lessonTime.id
        }
        val lessonTimes = getLessonTimesFlow(lessonTime.timetableId).firstOrNull() ?: return id
        lessonTimes.sortedBy { it.startTime }.forEachIndexed { index, lesson ->
            val newPeriod = index + 1
            if (lesson.period != newPeriod) updateLessonTime(lesson.copy(period = newPeriod))
        }
        checkAndEnableReminders(lessonTime.timetableId)
        return id
    }

    @Transaction
    open suspend fun deleteLessonTimeAutoSort(lessonTime: LessonTimeEntity) {
        deleteLessonTime(lessonTime)
        val lessonTimes = getLessonTimesFlow(lessonTime.timetableId).firstOrNull() ?: return
        lessonTimes.sortedBy { it.startTime }.forEachIndexed { index, lesson ->
            val newPeriod = index + 1
            if (lesson.period != newPeriod) updateLessonTime(lesson.copy(period = newPeriod))
        }
        checkAndEnableReminders(lessonTime.timetableId)
    }

    // ---------- 课程操作 ----------
    @Transaction
    open suspend fun insertCourseWithReminders(course: CourseEntity): Long {
        val id = insertCourse(course)
        checkAndEnableReminders(course.timetableId)
        return id
    }

    @Transaction
    open suspend fun updateCourseWithReminders(course: CourseEntity) {
        updateCourse(course)
        checkAndEnableReminders(course.timetableId)
    }

    @Transaction
    open suspend fun deleteCourseWithReminders(course: CourseEntity) {
        deleteCourse(course)
        checkAndEnableReminders(course.timetableId)
    }

    // ---------- 课表操作 ----------
    @Transaction
    open suspend fun insertTimetableWithReminders(timetable: TimetableEntity): Long {
        val id = insertTimetable(timetable)
        checkAndEnableReminders(id)
        return id
    }

    @Transaction
    open suspend fun updateTimetableWithReminders(timetable: TimetableEntity) {
        updateTimetable(timetable)
        checkAndEnableReminders(timetable.id)
    }

    @Transaction
    open suspend fun deleteTimetableWithReminders(timetable: TimetableEntity) {
        deleteTimetable(timetable)
        checkAndEnableReminders(timetable.id)
    }
}

// ---------- TypeConverter ----------
class Converters {
    @TypeConverter
    fun fromIntList(list: List<Int>): String = list.joinToString(",")

    @TypeConverter
    fun toIntList(data: String): List<Int> =
        if (data.isBlank()) emptyList() else data.split(",").map { it.toInt() }
}

// ---------- Helper 扩展 ----------
fun LocalDate.getWeekOfYear(): Int {
    val weekFields = WeekFields.of(Locale.getDefault())
    return this.get(weekFields.weekOfWeekBasedYear())
}

fun LocalDate.getWeekOfSemester(startDate: LocalDate): Int {
    val days = java.time.temporal.ChronoUnit.DAYS.between(startDate, this)
    return (days / 7 + 1).toInt()
}
