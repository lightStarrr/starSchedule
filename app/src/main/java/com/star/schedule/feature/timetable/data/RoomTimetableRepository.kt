package com.star.schedule.feature.timetable.data

import androidx.room.withTransaction
import com.star.schedule.core.database.AppDatabase
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.core.database.dao.CourseDao
import com.star.schedule.core.database.dao.LessonTimeDao
import com.star.schedule.core.database.dao.LessonTimeTemplateDao
import com.star.schedule.core.database.dao.PreferenceDao
import com.star.schedule.core.database.dao.TimetableDao
import com.star.schedule.core.database.entity.CourseEntity
import com.star.schedule.core.database.entity.CourseSessionEntity
import com.star.schedule.core.database.entity.CourseWithSessions
import com.star.schedule.core.database.entity.LessonTimeEntity
import com.star.schedule.core.database.entity.LessonTimeTemplateEntity
import com.star.schedule.core.database.entity.LessonTimeTemplateItemEntity
import com.star.schedule.core.database.entity.TimetableEntity
import com.star.schedule.feature.timetable.domain.ReminderScheduler
import com.star.schedule.feature.timetable.domain.TimetableRepository
import com.star.schedule.platform.notification.ReminderRefresher
import kotlinx.coroutines.flow.Flow

class RoomTimetableRepository(
    private val preferenceDao: PreferenceDao,
    private val timetableDao: TimetableDao,
    private val courseDao: CourseDao,
    private val lessonTimeDao: LessonTimeDao,
    private val lessonTimeTemplateDao: LessonTimeTemplateDao,
    reminderScheduler: ReminderScheduler? = null,
    private val runInTransaction: suspend (suspend () -> Any?) -> Any? = { block -> block() },
) : TimetableRepository {
    constructor(
        database: AppDatabase,
        reminderScheduler: ReminderScheduler? = null,
    ) : this(
        preferenceDao = database.preferenceDao(),
        timetableDao = database.timetableDao(),
        courseDao = database.courseDao(),
        lessonTimeDao = database.lessonTimeDao(),
        lessonTimeTemplateDao = database.lessonTimeTemplateDao(),
        reminderScheduler = reminderScheduler,
        runInTransaction = { block -> database.withTransaction { block() } },
    )

    private val reminderRefresher = ReminderRefresher(preferenceDao, reminderScheduler)

    override fun observeTimetables(): Flow<List<TimetableEntity>> =
        timetableDao.getAllTimetables()

    override fun observeTimetable(timetableId: Long): Flow<TimetableEntity?> =
        timetableDao.getTimetableFlow(timetableId)

    override suspend fun getAllTimetablesOnce(): List<TimetableEntity> =
        timetableDao.getAllTimetablesOnce()

    override suspend fun insertTimetableWithReminders(timetable: TimetableEntity): Long {
        val id = runInTransaction {
            val insertedId = timetableDao.insertTimetable(timetable)
            DatabaseProvider.ensureValidCurrentTimetable(preferenceDao, timetableDao)
            insertedId
        } as Long
        reminderRefresher.refreshIfCurrent(id)
        return id
    }

    override suspend fun updateTimetableWithReminders(timetable: TimetableEntity) {
        runInTransaction {
            timetableDao.updateTimetable(timetable)
            DatabaseProvider.ensureValidCurrentTimetable(preferenceDao, timetableDao)
        }
        reminderRefresher.refreshIfCurrent(timetable.id)
    }

    override suspend fun deleteTimetableWithReminders(timetable: TimetableEntity) {
        reminderRefresher.disableIfEnabled(timetable.id)
        runInTransaction {
            timetableDao.deleteTimetable(timetable)
            DatabaseProvider.ensureValidCurrentTimetable(preferenceDao, timetableDao)
        }
    }

    override suspend fun replaceCoursesForTimetable(
        timetableId: Long,
        courses: List<Pair<CourseEntity, List<CourseSessionEntity>>>,
    ) {
        courseDao.replaceCoursesForTimetable(timetableId, courses)
        reminderRefresher.refreshIfCurrent(timetableId)
    }

    override fun observeLessonTimes(timetableId: Long): Flow<List<LessonTimeEntity>> =
        lessonTimeDao.getLessonTimesFlow(timetableId)

    override suspend fun insertOrUpdateLessonTimeAutoSort(
        lessonTime: LessonTimeEntity,
        isInsert: Boolean,
    ): Long = lessonTimeDao.insertOrUpdateLessonTimeAutoSort(lessonTime, isInsert).also {
        reminderRefresher.refreshIfCurrent(lessonTime.timetableId)
    }

    override suspend fun deleteLessonTimeAutoSort(lessonTime: LessonTimeEntity) {
        lessonTimeDao.deleteLessonTimeAutoSort(lessonTime)
        reminderRefresher.refreshIfCurrent(lessonTime.timetableId)
    }

    override fun observeCoursesWithSessions(timetableId: Long): Flow<List<CourseWithSessions>> =
        courseDao.getCoursesWithSessionsFlow(timetableId)

    override suspend fun updateCourseWithReminders(course: CourseEntity) {
        courseDao.updateCourse(course)
        reminderRefresher.refreshIfCurrent(course.timetableId)
    }

    override suspend fun deleteCourseWithReminders(course: CourseEntity) {
        courseDao.deleteCourse(course)
        reminderRefresher.refreshIfCurrent(course.timetableId)
    }

    override suspend fun restoreCourseWithSessionsWithReminders(
        course: CourseEntity,
        sessions: List<CourseSessionEntity>,
    ): Long = courseDao.insertCourseWithSessions(course, sessions).also {
        reminderRefresher.refreshIfCurrent(course.timetableId)
    }

    override suspend fun insertCourseSessionWithReminders(
        timetableId: Long,
        session: CourseSessionEntity,
    ): Long = courseDao.insertSession(session).also {
        reminderRefresher.refreshIfCurrent(timetableId)
    }

    override suspend fun updateCourseSessionWithReminders(
        timetableId: Long,
        session: CourseSessionEntity,
    ) {
        courseDao.updateSession(session)
        reminderRefresher.refreshIfCurrent(timetableId)
    }

    override suspend fun deleteCourseSessionWithReminders(
        timetableId: Long,
        session: CourseSessionEntity,
    ) {
        courseDao.deleteSession(session)
        reminderRefresher.refreshIfCurrent(timetableId)
    }

    override suspend fun upsertCourseAndInsertSessionWithReminders(
        timetableId: Long,
        courseName: String,
        session: CourseSessionEntity,
    ): Pair<Long, Long> = courseDao.upsertCourseAndInsertSession(
        timetableId = timetableId,
        courseName = courseName,
        session = session,
    ).also {
        reminderRefresher.refreshIfCurrent(timetableId)
    }

    override fun observeLessonTimeTemplates(): Flow<List<LessonTimeTemplateEntity>> =
        lessonTimeTemplateDao.getLessonTimeTemplatesFlow()

    override suspend fun getLessonTimeTemplateByNameOnce(name: String): LessonTimeTemplateEntity? =
        lessonTimeTemplateDao.getLessonTimeTemplateByNameOnce(name)

    override suspend fun getLessonTimeTemplateItemsOnce(
        templateId: Long,
    ): List<LessonTimeTemplateItemEntity> =
        lessonTimeTemplateDao.getLessonTimeTemplateItemsOnce(templateId)

    override suspend fun saveLessonTimeTemplateFromItems(
        templateName: String,
        lessonTimes: List<LessonTimeTemplateItemEntity>,
        overwrite: Boolean,
        createdAt: Long,
        updatedAt: Long,
    ): Long = lessonTimeTemplateDao.saveLessonTimeTemplateFromItems(
        templateName = templateName,
        lessonTimes = lessonTimes,
        overwrite = overwrite,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    override suspend fun saveLessonTimeTemplateFromTimetable(
        timetableId: Long,
        templateName: String,
        overwrite: Boolean,
    ): Long = runInTransaction {
        val now = System.currentTimeMillis()
        val items = lessonTimeDao.getLessonTimesOnce(timetableId)
            .sortedBy { it.period }
            .map { time ->
                LessonTimeTemplateItemEntity(
                    templateId = 0L,
                    period = time.period,
                    startTime = time.startTime,
                    endTime = time.endTime,
                )
            }
        lessonTimeTemplateDao.saveLessonTimeTemplateFromItems(
            templateName = templateName,
            lessonTimes = items,
            overwrite = overwrite,
            createdAt = now,
            updatedAt = now,
        )
    } as Long

    override suspend fun deleteLessonTimeTemplate(template: LessonTimeTemplateEntity) {
        lessonTimeTemplateDao.deleteLessonTimeTemplate(template)
    }
}
