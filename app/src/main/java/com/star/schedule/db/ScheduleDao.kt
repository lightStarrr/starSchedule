package com.star.schedule.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.Update
import com.star.schedule.Constants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.time.LocalDate

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

    // 1) 若未设置则设为第一条课表
    // 2) 若指向不存在的课表（被删除）则选择删除后的第一条
    @Transaction
    open suspend fun ensureValidCurrentTimetable() {
        val currentId = getPreferenceFlow(Constants.PREF_CURRENT_TIMETABLE)
            .firstOrNull()?.toLongOrNull()
        val timetables = getAllTimetablesOnce()

        if (timetables.isEmpty()) {
            // 无课表：清空偏好（置空字符串，读取时 toLongOrNull 会为 null）
            setPreference(Constants.PREF_CURRENT_TIMETABLE, "")
            return
        }

        val firstId = timetables.first().id
        val exists = currentId != null && timetables.any { it.id == currentId }
        if (!exists) {
            setPreference(Constants.PREF_CURRENT_TIMETABLE, firstId.toString())
        }
    }

    // 自动初始化默认课表
    @Transaction
    open suspend fun initializeDefaultTimetable(): Long {
        val prefIdStr = getPreferenceFlow(Constants.PREF_CURRENT_TIMETABLE)
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
        setPreference(Constants.PREF_CURRENT_TIMETABLE, timetableId.toString())
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
        val currentId = getPreferenceFlow(Constants.PREF_CURRENT_TIMETABLE).firstOrNull()?.toLongOrNull()
        android.util.Log.d(
            "ScheduleDao",
            "checkAndEnableReminders: timetableId=$timetableId, currentId=$currentId, notificationManager=$notificationManager"
        )

        if (currentId == timetableId) {
            // 检查用户是否已经启用了课前提醒
            val enabledTimetableId = getPreferenceFlow(Constants.PREF_REMINDER_ENABLED_TIMETABLE).firstOrNull()
            val isReminderEnabled = enabledTimetableId?.toLongOrNull() == currentId

            if (isReminderEnabled) {
                try {
                    android.util.Log.d(
                        "ScheduleDao",
                        "用户已启用课前提醒，正在为课表ID $timetableId 启用提醒"
                    )
                    notificationManager?.enableRemindersForTimetable(currentId)
                    android.util.Log.d("ScheduleDao", "提醒启用操作完成")
                } catch (e: Exception) {
                    android.util.Log.e("ScheduleDao", "启用提醒时出错", e)
                    // 不抛出异常，因为提醒功能不应该影响主要的数据操作
                }
            } else {
                android.util.Log.d("ScheduleDao", "用户未启用课前提醒，跳过自动启用提醒")
            }
        } else {
            android.util.Log.d(
                "ScheduleDao",
                "当前课表ID($currentId)与操作课表ID($timetableId)不匹配，跳过提醒启用"
            )
        }
    }

    // ---------- 课时操作 ----------
    @Transaction
    open suspend fun insertOrUpdateLessonTimeAutoSort(
        lessonTime: LessonTimeEntity,
        isInsert: Boolean = true
    ): Long {
        android.util.Log.d(
            "ScheduleDao",
            "开始${if (isInsert) "插入" else "更新"}课程时间: $lessonTime"
        )
        val id = if (isInsert) {
            val insertedId = insertLessonTime(lessonTime)
            android.util.Log.d("ScheduleDao", "插入课程时间成功，ID: $insertedId")
            insertedId
        } else {
            updateLessonTime(lessonTime)
            android.util.Log.d("ScheduleDao", "更新课程时间成功，ID: ${lessonTime.id}")
            lessonTime.id
        }

        try {
            val lessonTimes = getLessonTimesFlow(lessonTime.timetableId).firstOrNull() ?: return id
            android.util.Log.d("ScheduleDao", "获取到${lessonTimes.size}个课程时间，开始重新排序")

            lessonTimes.sortedBy { it.startTime }.forEachIndexed { index, lesson ->
                val newPeriod = index + 1
                if (lesson.period != newPeriod) {
                    android.util.Log.d(
                        "ScheduleDao",
                        "更新课程时间 ${lesson.id} 的节次从 ${lesson.period} 到 $newPeriod"
                    )
                    updateLessonTime(lesson.copy(period = newPeriod))
                }
            }

            checkAndEnableReminders(lessonTime.timetableId)
            android.util.Log.d("ScheduleDao", "课程时间操作完成")
        } catch (e: Exception) {
            android.util.Log.e("ScheduleDao", "课程时间排序过程中出错", e)
            throw e
        }

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
        android.util.Log.d("ScheduleDao", "开始插入课程: $course")
        val id = insertCourse(course)
        android.util.Log.d("ScheduleDao", "插入课程成功，ID: $id")
        checkAndEnableReminders(course.timetableId)
        return id
    }

    @Transaction
    open suspend fun updateCourseWithReminders(course: CourseEntity) {
        android.util.Log.d("ScheduleDao", "开始更新课程: $course")
        try {
            updateCourse(course)
            android.util.Log.d("ScheduleDao", "更新课程成功，ID: ${course.id}")
            checkAndEnableReminders(course.timetableId)
            android.util.Log.d("ScheduleDao", "课程更新操作完成")
        } catch (e: Exception) {
            android.util.Log.e("ScheduleDao", "更新课程失败", e)
            throw e
        }
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
        // 若当前未选定课表，则在新增后回落到第一条课表
        ensureValidCurrentTimetable()
        return id
    }

    @Transaction
    open suspend fun updateTimetableWithReminders(timetable: TimetableEntity) {
        updateTimetable(timetable)
        checkAndEnableReminders(timetable.id)
        // 更新后也校验一次，防止异常状态
        ensureValidCurrentTimetable()
    }

    @Transaction
    open suspend fun deleteTimetableWithReminders(timetable: TimetableEntity) {
        deleteTimetable(timetable)
        checkAndEnableReminders(timetable.id)
        // 若删除了当前课表，则回退到第一条课表或清空
        ensureValidCurrentTimetable()
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
fun LocalDate.getWeekOfSemester(startDate: LocalDate): Int {
    val days = java.time.temporal.ChronoUnit.DAYS.between(startDate, this)
    return (days / 7 + 1).toInt()
}
