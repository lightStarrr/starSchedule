package com.star.schedule.feature.timetable.data

import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.LessonTimeEntity
import com.star.schedule.core.database.LessonTimeTemplateEntity
import com.star.schedule.core.database.LessonTimeTemplateItemEntity
import com.star.schedule.core.database.ScheduleDao
import com.star.schedule.core.database.TimetableEntity
import com.star.schedule.feature.timetable.domain.TimetableRepository
import kotlinx.coroutines.flow.Flow

class RoomTimetableRepository(
    private val dao: ScheduleDao
) : TimetableRepository {
    override fun observeTimetables(): Flow<List<TimetableEntity>> =
        dao.getAllTimetables()

    override suspend fun getAllTimetablesOnce(): List<TimetableEntity> =
        dao.getAllTimetablesOnce()

    override suspend fun insertTimetableWithReminders(timetable: TimetableEntity): Long =
        dao.insertTimetableWithReminders(timetable)

    override suspend fun updateTimetableWithReminders(timetable: TimetableEntity) {
        dao.updateTimetableWithReminders(timetable)
    }

    override suspend fun deleteTimetableWithReminders(timetable: TimetableEntity) {
        dao.deleteTimetableWithReminders(timetable)
    }

    override fun observeLessonTimes(timetableId: Long): Flow<List<LessonTimeEntity>> =
        dao.getLessonTimesFlow(timetableId)

    override suspend fun insertOrUpdateLessonTimeAutoSort(
        lessonTime: LessonTimeEntity,
        isInsert: Boolean
    ): Long = dao.insertOrUpdateLessonTimeAutoSort(lessonTime, isInsert)

    override suspend fun deleteLessonTimeAutoSort(lessonTime: LessonTimeEntity) {
        dao.deleteLessonTimeAutoSort(lessonTime)
    }

    override fun observeCourses(timetableId: Long): Flow<List<CourseEntity>> =
        dao.getCoursesFlow(timetableId)

    override suspend fun insertCourseWithReminders(course: CourseEntity): Long =
        dao.insertCourseWithReminders(course)

    override suspend fun updateCourseWithReminders(course: CourseEntity) {
        dao.updateCourseWithReminders(course)
    }

    override suspend fun deleteCourseWithReminders(course: CourseEntity) {
        dao.deleteCourseWithReminders(course)
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
    }

    override suspend fun deleteLessonTimeTemplate(template: LessonTimeTemplateEntity) {
        dao.deleteLessonTimeTemplate(template)
    }
}
