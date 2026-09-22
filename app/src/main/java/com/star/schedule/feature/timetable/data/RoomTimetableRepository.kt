package com.star.schedule.feature.timetable.data

import android.util.Log
import com.star.schedule.core.common.Constants
import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.LessonTimeEntity
import com.star.schedule.core.database.LessonTimeTemplateEntity
import com.star.schedule.core.database.LessonTimeTemplateItemEntity
import com.star.schedule.core.database.ScheduleDao
import com.star.schedule.core.database.TimetableEntity
import com.star.schedule.feature.timetable.domain.TimetableRepository
import com.star.schedule.feature.timetable.domain.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class RoomTimetableRepository(
    private val dao: ScheduleDao,
    private val reminderScheduler: ReminderScheduler? = null
) : TimetableRepository {
    override fun observeTimetables(): Flow<List<TimetableEntity>> =
        dao.getAllTimetables()

    override fun observeTimetable(timetableId: Long): Flow<TimetableEntity?> =
        dao.getTimetableFlow(timetableId)

    override suspend fun getAllTimetablesOnce(): List<TimetableEntity> =
        dao.getAllTimetablesOnce()

    override suspend fun insertTimetableWithReminders(timetable: TimetableEntity): Long {
        val id = dao.insertTimetableWithReminders(timetable)
        enableRemindersIfNeeded(id)
        return id
    }

    override suspend fun updateTimetableWithReminders(timetable: TimetableEntity) {
        dao.updateTimetableWithReminders(timetable)
        enableRemindersIfNeeded(timetable.id)
    }

    override suspend fun deleteTimetableWithReminders(timetable: TimetableEntity) {
        dao.deleteTimetableWithReminders(timetable)
        enableRemindersIfNeeded(timetable.id)
    }

    override suspend fun replaceCoursesForTimetable(
        timetableId: Long,
        courses: List<CourseEntity>
    ) {
        dao.replaceCoursesForTimetable(timetableId, courses)
        enableRemindersIfNeeded(timetableId)
    }

    override fun observeLessonTimes(timetableId: Long): Flow<List<LessonTimeEntity>> =
        dao.getLessonTimesFlow(timetableId)

    override suspend fun insertOrUpdateLessonTimeAutoSort(
        lessonTime: LessonTimeEntity,
        isInsert: Boolean
    ): Long = dao.insertOrUpdateLessonTimeAutoSort(lessonTime, isInsert).also {
        enableRemindersIfNeeded(lessonTime.timetableId)
    }

    override suspend fun deleteLessonTimeAutoSort(lessonTime: LessonTimeEntity) {
        dao.deleteLessonTimeAutoSort(lessonTime)
        enableRemindersIfNeeded(lessonTime.timetableId)
    }

    override fun observeCourses(timetableId: Long): Flow<List<CourseEntity>> =
        dao.getCoursesFlow(timetableId)

    override suspend fun insertCourseWithReminders(course: CourseEntity): Long =
        dao.insertCourseWithReminders(course).also {
            enableRemindersIfNeeded(course.timetableId)
        }

    override suspend fun updateCourseWithReminders(course: CourseEntity) {
        dao.updateCourseWithReminders(course)
        enableRemindersIfNeeded(course.timetableId)
    }

    override suspend fun deleteCourseWithReminders(course: CourseEntity) {
        dao.deleteCourseWithReminders(course)
        enableRemindersIfNeeded(course.timetableId)
    }

    override fun observeLessonTimeTemplates(): Flow<List<LessonTimeTemplateEntity>> =
        dao.getLessonTimeTemplatesFlow()

    override suspend fun getLessonTimeTemplateByNameOnce(name: String): LessonTimeTemplateEntity? =
        dao.getLessonTimeTemplateByNameOnce(name)

    override suspend fun getLessonTimeTemplateItemsOnce(
        templateId: Long
    ): List<LessonTimeTemplateItemEntity> = dao.getLessonTimeTemplateItemsOnce(templateId)

    override suspend fun saveLessonTimeTemplateFromItems(
        templateName: String,
        lessonTimes: List<LessonTimeTemplateItemEntity>,
        overwrite: Boolean,
        createdAt: Long,
        updatedAt: Long
    ): Long = dao.saveLessonTimeTemplateFromItems(
        templateName = templateName,
        lessonTimes = lessonTimes,
        overwrite = overwrite,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    override suspend fun saveLessonTimeTemplateFromTimetable(
        timetableId: Long,
        templateName: String,
        overwrite: Boolean
    ): Long = dao.saveLessonTimeTemplateFromTimetable(timetableId, templateName, overwrite)

    override suspend fun applyLessonTimeTemplateToTimetable(timetableId: Long, templateId: Long) {
        dao.applyLessonTimeTemplateToTimetable(timetableId, templateId)
        enableRemindersIfNeeded(timetableId)
    }

    override suspend fun deleteLessonTimeTemplate(template: LessonTimeTemplateEntity) {
        dao.deleteLessonTimeTemplate(template)
    }

    private suspend fun enableRemindersIfNeeded(timetableId: Long) {
        val currentTimetableId = dao
            .getPreferenceFlow(Constants.PREF_CURRENT_TIMETABLE)
            .firstOrNull()
            ?.toLongOrNull()
        if (currentTimetableId != timetableId) return

        val enabledTimetableId = dao
            .getPreferenceFlow(Constants.PREF_REMINDER_ENABLED_TIMETABLE)
            .firstOrNull()
            ?.toLongOrNull()
        if (enabledTimetableId != currentTimetableId) return

        try {
            reminderScheduler?.enableRemindersForTimetable(currentTimetableId)
        } catch (error: Exception) {
            Log.e(TAG, "启用课表提醒失败", error)
        }
    }

    private companion object {
        const val TAG = "RoomTimetableRepository"
    }
}
