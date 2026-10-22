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

    /** 新增一节，节次按开始时间重新排号。 */
    suspend fun insertLessonTime(lessonTime: LessonTimeEntity): Long

    /** 改一节的时间，节次同样按开始时间重新排号。 */
    suspend fun updateLessonTime(lessonTime: LessonTimeEntity)

    /** 删一节，剩下的节次重新排号；课程引用的节次号不会跟着改。 */
    suspend fun deleteLessonTime(lessonTime: LessonTimeEntity)

    fun observeDayNotes(timetableId: Long): Flow<List<DayNoteEntity>>

    suspend fun upsertDayNote(note: DayNoteEntity): Long

    suspend fun deleteDayNote(timetableId: Long, date: String)
}
