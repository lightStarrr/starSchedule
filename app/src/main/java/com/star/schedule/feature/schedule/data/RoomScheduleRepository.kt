package com.star.schedule.feature.schedule.data

import com.star.schedule.core.common.Constants
import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.DayNoteEntity
import com.star.schedule.core.database.LessonTimeEntity
import com.star.schedule.core.database.ScheduleDao
import com.star.schedule.core.database.TimetableEntity
import com.star.schedule.feature.schedule.domain.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomScheduleRepository(
    private val dao: ScheduleDao
) : ScheduleRepository {
    override fun observeCurrentTimetableId(): Flow<Long?> =
        dao.getPreferenceFlow(Constants.PREF_CURRENT_TIMETABLE)
            .map { it?.toLongOrNull() }

    override fun observeTimetable(timetableId: Long): Flow<TimetableEntity?> =
        dao.getTimetableFlow(timetableId)

    override fun observeCourses(timetableId: Long): Flow<List<CourseEntity>> =
        dao.getCoursesFlow(timetableId)

    override fun observeLessonTimes(timetableId: Long): Flow<List<LessonTimeEntity>> =
        dao.getLessonTimesFlow(timetableId)

    // 写入统一走 DAO 的 AutoSort 事务：节次号永远等于按开始时间排序后的位置，
    // 时间列从上到下才不会出现「第 3 节比第 2 节还早」。
    override suspend fun insertLessonTime(lessonTime: LessonTimeEntity): Long =
        dao.insertOrUpdateLessonTimeAutoSort(lessonTime, isInsert = true)

    override suspend fun updateLessonTime(lessonTime: LessonTimeEntity) {
        dao.insertOrUpdateLessonTimeAutoSort(lessonTime, isInsert = false)
    }

    override suspend fun deleteLessonTime(lessonTime: LessonTimeEntity) {
        dao.deleteLessonTimeAutoSort(lessonTime)
    }

    override suspend fun getLessonTimeTemplateIdByName(name: String): Long? =
        dao.getLessonTimeTemplateByNameOnce(name)?.id

    override suspend fun saveLessonTimeTemplateFromTimetable(
        timetableId: Long,
        name: String,
        overwrite: Boolean,
    ): Long = dao.saveLessonTimeTemplateFromTimetable(timetableId, name, overwrite)

    override fun observeDayNotes(timetableId: Long): Flow<List<DayNoteEntity>> =
        dao.getDayNotesFlow(timetableId)

    override suspend fun upsertDayNote(note: DayNoteEntity): Long =
        dao.upsertDayNote(note)

    override suspend fun deleteDayNote(timetableId: Long, date: String) {
        dao.deleteDayNote(timetableId, date)
    }
}
