package com.star.schedule.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.star.schedule.core.database.LessonTimeApplySnapshot
import com.star.schedule.core.database.entity.LessonTimeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

@Dao
abstract class LessonTimeDao {
    @Query("SELECT * FROM lesson_time WHERE timetableId = :timetableId ORDER BY period ASC")
    abstract fun getLessonTimesFlow(timetableId: Long): Flow<List<LessonTimeEntity>>

    @Query("SELECT * FROM lesson_time WHERE timetableId = :timetableId ORDER BY period ASC")
    abstract suspend fun getLessonTimesOnce(timetableId: Long): List<LessonTimeEntity>

    @Insert
    abstract suspend fun insertLessonTime(lessonTime: LessonTimeEntity): Long

    @Update
    abstract suspend fun updateLessonTime(lessonTime: LessonTimeEntity)

    @Delete
    abstract suspend fun deleteLessonTime(lessonTime: LessonTimeEntity)

    @Query("DELETE FROM lesson_time WHERE timetableId = :timetableId")
    abstract suspend fun deleteLessonTimesByTimetableId(timetableId: Long)

    @Transaction
    open suspend fun replaceLessonTimesForTimetable(
        timetableId: Long,
        lessonTimes: List<LessonTimeEntity>,
    ) {
        deleteLessonTimesByTimetableId(timetableId)
        lessonTimes.forEach { insertLessonTime(it) }
    }

    /**
     * 应用模板：先记下当前节次，再整份替换。
     *
     * 返回的快照留给「撤销」。课程一概不动——课程用到模板之外的节次时只是没有时间显示，
     * 由界面提前告知，不在数据层做裁剪或删除。
     */
    @Transaction
    open suspend fun applyLessonTimesToTimetable(
        timetableId: Long,
        lessonTimes: List<LessonTimeEntity>,
    ): LessonTimeApplySnapshot {
        val before = getLessonTimesOnce(timetableId)
        replaceLessonTimesForTimetable(timetableId, lessonTimes)
        return LessonTimeApplySnapshot(timetableId = timetableId, lessonTimesBefore = before)
    }

    /** 撤销应用：整份换回应用前的节次（行 id 已被释放，按原 id 插回不会冲突）。 */
    @Transaction
    open suspend fun restoreLessonTimeApplySnapshot(snapshot: LessonTimeApplySnapshot) {
        replaceLessonTimesForTimetable(snapshot.timetableId, snapshot.lessonTimesBefore)
    }

    @Transaction
    open suspend fun insertOrUpdateLessonTimeAutoSort(
        lessonTime: LessonTimeEntity,
        isInsert: Boolean = true,
    ): Long {
        val id = if (isInsert) {
            insertLessonTime(lessonTime)
        } else {
            updateLessonTime(lessonTime)
            lessonTime.id
        }
        val lessonTimes = getLessonTimesFlow(lessonTime.timetableId).firstOrNull() ?: return id
        lessonTimes.sortedBy { it.startTime }.forEachIndexed { index, lesson ->
            val newPeriod = index + 1
            if (lesson.period != newPeriod) {
                updateLessonTime(lesson.copy(period = newPeriod))
            }
        }
        return id
    }

    @Transaction
    open suspend fun deleteLessonTimeAutoSort(lessonTime: LessonTimeEntity) {
        deleteLessonTime(lessonTime)
        val lessonTimes = getLessonTimesFlow(lessonTime.timetableId).firstOrNull() ?: return
        lessonTimes.sortedBy { it.startTime }.forEachIndexed { index, lesson ->
            val newPeriod = index + 1
            if (lesson.period != newPeriod) {
                updateLessonTime(lesson.copy(period = newPeriod))
            }
        }
    }
}
