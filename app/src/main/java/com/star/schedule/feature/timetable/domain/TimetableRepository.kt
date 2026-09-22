package com.star.schedule.feature.timetable.domain

import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.LessonTimeEntity
import com.star.schedule.core.database.LessonTimeTemplateEntity
import com.star.schedule.core.database.LessonTimeTemplateItemEntity
import com.star.schedule.core.database.TimetableEntity
import kotlinx.coroutines.flow.Flow

interface TimetableRepository {
    fun observeTimetables(): Flow<List<TimetableEntity>>

    suspend fun getAllTimetablesOnce(): List<TimetableEntity>

    suspend fun insertTimetableWithReminders(timetable: TimetableEntity): Long

    suspend fun updateTimetableWithReminders(timetable: TimetableEntity)

    suspend fun deleteTimetableWithReminders(timetable: TimetableEntity)

    fun observeLessonTimes(timetableId: Long): Flow<List<LessonTimeEntity>>

    suspend fun insertOrUpdateLessonTimeAutoSort(
        lessonTime: LessonTimeEntity,
        isInsert: Boolean = true
    ): Long

    suspend fun deleteLessonTimeAutoSort(lessonTime: LessonTimeEntity)

    fun observeCourses(timetableId: Long): Flow<List<CourseEntity>>

    suspend fun insertCourseWithReminders(course: CourseEntity): Long

    suspend fun updateCourseWithReminders(course: CourseEntity)

    suspend fun deleteCourseWithReminders(course: CourseEntity)

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

    suspend fun applyLessonTimeTemplateToTimetable(timetableId: Long, templateId: Long)

    suspend fun deleteLessonTimeTemplate(template: LessonTimeTemplateEntity)
}
