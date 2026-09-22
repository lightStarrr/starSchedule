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

    override fun observeDayNotes(timetableId: Long): Flow<List<DayNoteEntity>> =
        dao.getDayNotesFlow(timetableId)

    override suspend fun upsertDayNote(note: DayNoteEntity): Long =
        dao.upsertDayNote(note)

    override suspend fun deleteDayNote(timetableId: Long, date: String) {
        dao.deleteDayNote(timetableId, date)
    }
}
