package com.star.schedule.feature.schedule.domain

import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.DayNoteEntity
import com.star.schedule.core.database.LessonTimeEntity
import com.star.schedule.core.database.TimetableEntity
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    fun observeCurrentTimetableId(): Flow<Long?>

    fun observeTimetable(timetableId: Long): Flow<TimetableEntity?>

    fun observeCourses(timetableId: Long): Flow<List<CourseEntity>>

    fun observeLessonTimes(timetableId: Long): Flow<List<LessonTimeEntity>>

    fun observeDayNotes(timetableId: Long): Flow<List<DayNoteEntity>>

    suspend fun upsertDayNote(note: DayNoteEntity): Long

    suspend fun deleteDayNote(timetableId: Long, date: String)
}
