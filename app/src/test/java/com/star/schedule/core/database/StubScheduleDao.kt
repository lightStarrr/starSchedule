package com.star.schedule.core.database

import kotlinx.coroutines.flow.Flow

/** 只让测试覆盖的 DAO 操作通过，其余调用立即失败，避免无关查询被默认值掩盖。 */
internal abstract class StubScheduleDao : ScheduleDao() {
    override fun getPreferenceFlow(prefKey: String): Flow<String?> = error("Unexpected preference read")
    override suspend fun insertPreference(preference: PreferenceEntity): Unit = error("Unexpected preference write")
    override fun getAllTimetables(): Flow<List<TimetableEntity>> = error("Unexpected timetable read")
    override fun getTimetableFlow(id: Long): Flow<TimetableEntity?> = error("Unexpected timetable read")
    override suspend fun insertTimetable(timetable: TimetableEntity): Long = error("Unexpected timetable insert")
    override suspend fun updateTimetable(timetable: TimetableEntity): Unit = error("Unexpected timetable update")
    override suspend fun deleteTimetable(timetable: TimetableEntity): Unit = error("Unexpected timetable delete")
    override suspend fun getAllTimetablesOnce(): List<TimetableEntity> = error("Unexpected timetable read")
    override fun getLessonTimesFlow(timetableId: Long): Flow<List<LessonTimeEntity>> = error("Unexpected lesson read")
    override suspend fun insertLessonTime(lessonTime: LessonTimeEntity): Long = error("Unexpected lesson insert")
    override suspend fun updateLessonTime(lessonTime: LessonTimeEntity): Unit = error("Unexpected lesson update")
    override suspend fun deleteLessonTime(lessonTime: LessonTimeEntity): Unit = error("Unexpected lesson delete")
    override suspend fun deleteLessonTimesByTimetableId(timetableId: Long): Unit = error("Unexpected lesson delete")
    override suspend fun getLessonTimesOnce(timetableId: Long): List<LessonTimeEntity> = error("Unexpected lesson read")
    override fun getLessonTimeTemplatesFlow(): Flow<List<LessonTimeTemplateEntity>> = error("Unexpected template read")
    override suspend fun getLessonTimeTemplateByNameOnce(name: String): LessonTimeTemplateEntity? =
        error("Unexpected template read")
    override suspend fun getLessonTimeTemplateByIdOnce(templateId: Long): LessonTimeTemplateEntity? =
        error("Unexpected template read")
    override suspend fun insertLessonTimeTemplate(template: LessonTimeTemplateEntity): Long = error("Unexpected template insert")
    override suspend fun updateLessonTimeTemplate(template: LessonTimeTemplateEntity): Unit = error("Unexpected template update")
    override suspend fun deleteLessonTimeTemplate(template: LessonTimeTemplateEntity): Unit = error("Unexpected template delete")
    override suspend fun insertLessonTimeTemplateItems(items: List<LessonTimeTemplateItemEntity>): Unit =
        error("Unexpected template items insert")
    override suspend fun deleteLessonTimeTemplateItemsByTemplateId(templateId: Long): Unit =
        error("Unexpected template items delete")
    override suspend fun getLessonTimeTemplateItemsOnce(templateId: Long): List<LessonTimeTemplateItemEntity> =
        error("Unexpected template items read")
    override fun getLessonTimeTemplateItemsFlow(): Flow<List<LessonTimeTemplateItemEntity>> =
        error("Unexpected template items read")
    override fun getCoursesFlow(timetableId: Long): Flow<List<CourseEntity>> = error("Unexpected course read")
    override fun getAllCoursesFlow(): Flow<List<CourseEntity>> = error("Unexpected course read")
    override suspend fun insertCourse(course: CourseEntity): Long = error("Unexpected course insert")
    override suspend fun updateCourse(course: CourseEntity): Unit = error("Unexpected course update")
    override suspend fun deleteCourse(course: CourseEntity): Unit = error("Unexpected course delete")
    override suspend fun deleteCoursesByTimetableId(timetableId: Long): Unit = error("Unexpected course delete")
    override suspend fun insertReminder(reminder: ReminderEntity): Unit = error("Unexpected reminder insert")
    override suspend fun getAllReminders(): List<ReminderEntity> = error("Unexpected reminder read")
    override suspend fun deleteReminder(requestCode: Int): Unit = error("Unexpected reminder delete")
    override suspend fun deleteAllReminders(): Unit = error("Unexpected reminder delete")
    override fun getDayNotesFlow(timetableId: Long): Flow<List<DayNoteEntity>> = error("Unexpected note read")
    override suspend fun upsertDayNote(note: DayNoteEntity): Long = error("Unexpected note write")
    override suspend fun deleteDayNote(timetableId: Long, date: String): Unit = error("Unexpected note delete")
}
