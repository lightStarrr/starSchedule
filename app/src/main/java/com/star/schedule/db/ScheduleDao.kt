package com.star.schedule.db

import androidx.room.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.*

// ---------- DAO ----------
@Dao
interface ScheduleDao {
    // ------------------ 偏好设置 ------------------
    @Query("SELECT value FROM preference WHERE prefKey = :prefKey LIMIT 1")
    fun getPreferenceFlow(prefKey: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreference(preference: PreferenceEntity)

    @Transaction
    suspend fun setPreference(key: String, value: String) {
        insertPreference(PreferenceEntity(key, value))
    }

    // ------------------ 课程表 ------------------
    @Query("SELECT * FROM timetable ORDER BY id ASC")
    fun getAllTimetables(): Flow<List<TimetableEntity>>

    @Query("SELECT * FROM timetable WHERE id = :id LIMIT 1")
    fun getTimetableFlow(id: Long): Flow<TimetableEntity?>

    @Insert
    suspend fun insertTimetable(timetable: TimetableEntity): Long

    @Update
    suspend fun updateTimetable(timetable: TimetableEntity)

    @Delete
    suspend fun deleteTimetable(timetable: TimetableEntity)

    // 自动初始化：如果没有课程表，则创建一个默认课程表
    @Transaction
    suspend fun initializeDefaultTimetable(): Long {
        // 尝试获取用户设置的当前课表id
        val prefIdStr = getPreferenceFlow("current_timetable")
            .map { it?.toLongOrNull() }
            .firstOrNull() // 因为这是 Flow，需要获取一次值

        if (prefIdStr != null) {
            return prefIdStr
        }

        // 数据库已有课表但用户没设置偏好
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

        // 同步写入用户偏好
        setPreference("current_timetable", timetableId.toString())
        return timetableId
    }

    // Helper: 获取 List 不使用 Flow
    @Query("SELECT * FROM timetable ORDER BY id ASC")
    suspend fun getAllTimetablesOnce(): List<TimetableEntity>

    // ------------------ 课时间 ------------------
    @Query("SELECT * FROM lesson_time WHERE timetableId = :timetableId ORDER BY period ASC")
    fun getLessonTimesFlow(timetableId: Long): Flow<List<LessonTimeEntity>>

    @Insert
    suspend fun insertLessonTime(lessonTime: LessonTimeEntity): Long

    @Update
    suspend fun updateLessonTime(lessonTime: LessonTimeEntity)

    @Delete
    suspend fun deleteLessonTime(lessonTime: LessonTimeEntity)

    // 新增：根据时间自动重新排序课时
    @Transaction
    suspend fun updateLessonTimeWithAutoSort(lessonTime: LessonTimeEntity) {
        // 更新当前课时
        updateLessonTime(lessonTime)
        
        // 获取同一课表的所有课时
        val lessonTimes = getLessonTimesFlow(lessonTime.timetableId).firstOrNull() ?: return
        
        // 按开始时间排序
        val sortedByTime = lessonTimes.sortedBy { it.startTime }
        
        // 更新所有课时的节次以匹配时间顺序
        sortedByTime.forEachIndexed { index, lesson ->
            val newPeriod = index + 1
            if (lesson.period != newPeriod) {
                updateLessonTime(lesson.copy(period = newPeriod))
            }
        }
    }

    // 新增：插入课时并自动排序
    @Transaction
    suspend fun insertLessonTimeWithAutoSort(lessonTime: LessonTimeEntity): Long {
        // 先插入课时
        val id = insertLessonTime(lessonTime)
        
        // 获取同一课表的所有课时
        val lessonTimes = getLessonTimesFlow(lessonTime.timetableId).firstOrNull() ?: return id
        
        // 按开始时间排序
        val sortedByTime = lessonTimes.sortedBy { it.startTime }
        
        // 更新所有课时的节次以匹配时间顺序
        sortedByTime.forEachIndexed { index, lesson ->
            val newPeriod = index + 1
            if (lesson.period != newPeriod) {
                updateLessonTime(lesson.copy(period = newPeriod))
            }
        }
        
        return id
    }

    // 新增：删除课时并自动排序
    @Transaction
    suspend fun deleteLessonTimeWithAutoSort(lessonTime: LessonTimeEntity) {
        // 删除课时
        deleteLessonTime(lessonTime)
        
        // 获取同一课表的所有课时
        val lessonTimes = getLessonTimesFlow(lessonTime.timetableId).firstOrNull() ?: return
        
        // 按开始时间排序
        val sortedByTime = lessonTimes.sortedBy { it.startTime }
        
        // 更新所有课时的节次以匹配时间顺序
        sortedByTime.forEachIndexed { index, lesson ->
            val newPeriod = index + 1
            if (lesson.period != newPeriod) {
                updateLessonTime(lesson.copy(period = newPeriod))
            }
        }
    }

    // ------------------ 课程 ------------------
    @Query("SELECT * FROM course WHERE timetableId = :timetableId ORDER BY dayOfWeek ASC")
    fun getCoursesFlow(timetableId: Long): Flow<List<CourseEntity>>

    // 获取指定日期（可选）对应周的课程
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getCoursesForDateFlow(timetableId: Long, date: LocalDate? = null): Flow<List<CourseEntity>> {
        val timetableFlow = getTimetableFlow(timetableId)
        val coursesFlow = getCoursesFlow(timetableId)

        return combine(timetableFlow, coursesFlow) { timetable, courses ->
            val startDate = timetable?.startDate?.let { dateStr ->
                try {
                    LocalDate.parse(dateStr)
                } catch (e: Exception) {
                    // 日期解析失败时使用当前日期
                    LocalDate.now()
                }
            } ?: LocalDate.now()
            val weekNumber = date?.getWeekOfSemester(startDate) ?: 0
            if (weekNumber == 0) courses else courses.filter { it.weeks.contains(weekNumber) }
        }
    }

    @Insert
    suspend fun insertCourse(course: CourseEntity): Long

    @Update
    suspend fun updateCourse(course: CourseEntity)

    @Delete
    suspend fun deleteCourse(course: CourseEntity)
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
    return (days / 7 + 1).toInt()  // 第1周从 startDate 开始算
}
