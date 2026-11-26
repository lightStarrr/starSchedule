package com.star.schedule.feature.timetable.domain

import com.star.schedule.core.database.entity.CourseEntity
import com.star.schedule.core.database.entity.CourseSessionEntity
import com.star.schedule.core.database.entity.CourseWithSessions
import com.star.schedule.core.database.entity.LessonTimeEntity
import com.star.schedule.core.database.entity.LessonTimeTemplateEntity
import com.star.schedule.core.database.entity.LessonTimeTemplateItemEntity
import com.star.schedule.core.database.entity.TimetableEntity
import kotlinx.coroutines.flow.Flow

interface TimetableRepository {
    fun observeTimetables(): Flow<List<TimetableEntity>>

    fun observeTimetable(timetableId: Long): Flow<TimetableEntity?>

    suspend fun getAllTimetablesOnce(): List<TimetableEntity>

    suspend fun insertTimetableWithReminders(timetable: TimetableEntity): Long

    suspend fun updateTimetableWithReminders(timetable: TimetableEntity)

    suspend fun deleteTimetableWithReminders(timetable: TimetableEntity)

    suspend fun replaceCoursesForTimetable(
        timetableId: Long,
        courses: List<Pair<CourseEntity, List<CourseSessionEntity>>>,
    )

    fun observeLessonTimes(timetableId: Long): Flow<List<LessonTimeEntity>>

    suspend fun insertOrUpdateLessonTimeAutoSort(
        lessonTime: LessonTimeEntity,
        isInsert: Boolean = true
    ): Long

    suspend fun deleteLessonTimeAutoSort(lessonTime: LessonTimeEntity)

    fun observeCoursesWithSessions(timetableId: Long): Flow<List<CourseWithSessions>>

    suspend fun updateCourseWithReminders(course: CourseEntity)

    suspend fun deleteCourseWithReminders(course: CourseEntity)

    suspend fun restoreCourseWithSessionsWithReminders(
        course: CourseEntity,
        sessions: List<CourseSessionEntity>,
    ): Long

    suspend fun insertCourseSessionWithReminders(
        timetableId: Long,
        session: CourseSessionEntity,
    ): Long

    suspend fun updateCourseSessionWithReminders(
        timetableId: Long,
        session: CourseSessionEntity,
    )

    suspend fun deleteCourseSessionWithReminders(
        timetableId: Long,
        session: CourseSessionEntity,
    )

    suspend fun upsertCourseAndInsertSessionWithReminders(
        timetableId: Long,
        courseName: String,
        session: CourseSessionEntity,
    ): Pair<Long, Long>

    fun observeLessonTimeTemplates(): Flow<List<LessonTimeTemplateEntity>>

    suspend fun getLessonTimeTemplateByNameOnce(name: String): LessonTimeTemplateEntity?

    suspend fun getLessonTimeTemplateItemsOnce(templateId: Long): List<LessonTimeTemplateItemEntity>

    suspend fun saveLessonTimeTemplateFromItems(
        templateName: String,
        lessonTimes: List<LessonTimeTemplateItemEntity>,
        overwrite: Boolean = false,
        createdAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis()
    ): Long

    suspend fun saveLessonTimeTemplateFromTimetable(
        timetableId: Long,
        templateName: String,
        overwrite: Boolean = false
    ): Long

    suspend fun deleteLessonTimeTemplate(template: LessonTimeTemplateEntity)
}
