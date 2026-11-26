package com.star.schedule.feature.schedule.data

import androidx.room.withTransaction
import com.star.schedule.core.common.Constants
import com.star.schedule.core.database.AppDatabase
import com.star.schedule.core.database.LessonTimeApplySnapshot
import com.star.schedule.core.database.dao.CourseDao
import com.star.schedule.core.database.dao.DayNoteDao
import com.star.schedule.core.database.dao.LessonTimeDao
import com.star.schedule.core.database.dao.LessonTimeTemplateDao
import com.star.schedule.core.database.dao.PreferenceDao
import com.star.schedule.core.database.dao.TimetableDao
import com.star.schedule.core.database.entity.DayNoteEntity
import com.star.schedule.core.database.entity.LessonTimeEntity
import com.star.schedule.core.database.entity.LessonTimeTemplateEntity
import com.star.schedule.core.database.entity.LessonTimeTemplateItemEntity
import com.star.schedule.core.database.entity.TimetableEntity
import com.star.schedule.core.model.ScheduledCourse
import com.star.schedule.core.model.toScheduledCourses
import com.star.schedule.feature.schedule.domain.ScheduleRepository
import com.star.schedule.feature.timetable.domain.LessonTimeTemplateApply
import com.star.schedule.platform.notification.ReminderRefresher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomScheduleRepository(
    private val preferenceDao: PreferenceDao,
    private val timetableDao: TimetableDao,
    private val courseDao: CourseDao,
    private val lessonTimeDao: LessonTimeDao,
    private val lessonTimeTemplateDao: LessonTimeTemplateDao,
    private val dayNoteDao: DayNoteDao,
    /** 节次写入与模板应用后重排提醒；只读页面可不传。 */
    private val reminderRefresher: ReminderRefresher? = null,
    private val runInTransaction: suspend (suspend () -> Any?) -> Any? = { block -> block() },
) : ScheduleRepository {
    constructor(
        database: AppDatabase,
        reminderRefresher: ReminderRefresher? = null,
    ) : this(
        preferenceDao = database.preferenceDao(),
        timetableDao = database.timetableDao(),
        courseDao = database.courseDao(),
        lessonTimeDao = database.lessonTimeDao(),
        lessonTimeTemplateDao = database.lessonTimeTemplateDao(),
        dayNoteDao = database.dayNoteDao(),
        reminderRefresher = reminderRefresher,
        runInTransaction = { block -> database.withTransaction { block() } },
    )

    override fun observeCurrentTimetableId(): Flow<Long?> =
        preferenceDao.getPreferenceFlow(Constants.PREF_CURRENT_TIMETABLE)
            .map { it?.toLongOrNull() }

    override fun observeTimetable(timetableId: Long): Flow<TimetableEntity?> =
        timetableDao.getTimetableFlow(timetableId)

    override fun observeCourses(timetableId: Long): Flow<List<ScheduledCourse>> =
        courseDao.getCoursesWithSessionsFlow(timetableId).map { it.toScheduledCourses() }

    override fun observeLessonTimes(timetableId: Long): Flow<List<LessonTimeEntity>> =
        lessonTimeDao.getLessonTimesFlow(timetableId)

    // 写入统一走 DAO 的 AutoSort 事务：节次号永远等于按开始时间排序后的位置，
    // 时间列从上到下才不会出现「第 3 节比第 2 节还早」。
    override suspend fun insertLessonTime(lessonTime: LessonTimeEntity): Long =
        lessonTimeDao.insertOrUpdateLessonTimeAutoSort(lessonTime, isInsert = true).also {
            reminderRefresher?.refreshIfCurrent(lessonTime.timetableId)
        }

    override suspend fun updateLessonTime(lessonTime: LessonTimeEntity) {
        lessonTimeDao.insertOrUpdateLessonTimeAutoSort(lessonTime, isInsert = false)
        reminderRefresher?.refreshIfCurrent(lessonTime.timetableId)
    }

    override suspend fun deleteLessonTime(lessonTime: LessonTimeEntity) {
        lessonTimeDao.deleteLessonTimeAutoSort(lessonTime)
        reminderRefresher?.refreshIfCurrent(lessonTime.timetableId)
    }

    override suspend fun getLessonTimeTemplateIdByName(name: String): Long? =
        lessonTimeTemplateDao.getLessonTimeTemplateByNameOnce(name)?.id

    override suspend fun saveLessonTimeTemplateFromTimetable(
        timetableId: Long,
        name: String,
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
            templateName = name,
            lessonTimes = items,
            overwrite = overwrite,
            createdAt = now,
            updatedAt = now,
        )
    } as Long

    override fun observeLessonTimeTemplates(): Flow<List<LessonTimeTemplateEntity>> =
        lessonTimeTemplateDao.getLessonTimeTemplatesFlow()

    override fun observeLessonTimeTemplateItems(): Flow<List<LessonTimeTemplateItemEntity>> =
        lessonTimeTemplateDao.getLessonTimeTemplateItemsFlow()

    override suspend fun getLessonTimeTemplateByIdOnce(templateId: Long): LessonTimeTemplateEntity? =
        lessonTimeTemplateDao.getLessonTimeTemplateByIdOnce(templateId)

    override suspend fun getLessonTimeTemplateItemsOnce(
        templateId: Long,
    ): List<LessonTimeTemplateItemEntity> =
        lessonTimeTemplateDao.getLessonTimeTemplateItemsOnce(templateId)

    override suspend fun saveLessonTimeTemplateFromItems(
        templateName: String,
        lessonTimes: List<LessonTimeTemplateItemEntity>,
        overwrite: Boolean,
    ): Long = lessonTimeTemplateDao.saveLessonTimeTemplateFromItems(
        templateName = templateName,
        lessonTimes = lessonTimes,
        overwrite = overwrite,
    )

    override suspend fun replaceLessonTimeTemplate(
        template: LessonTimeTemplateEntity,
        items: List<LessonTimeTemplateItemEntity>,
    ) {
        lessonTimeTemplateDao.replaceLessonTimeTemplate(template, items)
    }

    override suspend fun applyLessonTimeTemplateToTimetable(
        timetableId: Long,
        templateId: Long,
    ): LessonTimeApplySnapshot? {
        val items = lessonTimeTemplateDao.getLessonTimeTemplateItemsOnce(templateId)
        // 空模板不是「清空课表」，直接拒绝：界面会提示「这个模板还没有节次」。
        if (items.isEmpty()) return null
        val periods = LessonTimeTemplateApply.normalize(items)
        val snapshot = lessonTimeDao.applyLessonTimesToTimetable(
            timetableId = timetableId,
            lessonTimes = periods.map { period ->
                LessonTimeEntity(
                    timetableId = timetableId,
                    period = period.period,
                    startTime = period.startTime,
                    endTime = period.endTime,
                )
            },
        )
        reminderRefresher?.refreshIfCurrent(timetableId)
        return snapshot
    }

    override suspend fun restoreAppliedLessonTimeTemplate(snapshot: LessonTimeApplySnapshot) {
        lessonTimeDao.restoreLessonTimeApplySnapshot(snapshot)
        reminderRefresher?.refreshIfCurrent(snapshot.timetableId)
    }

    override suspend fun deleteLessonTimeTemplate(template: LessonTimeTemplateEntity) {
        lessonTimeTemplateDao.deleteLessonTimeTemplate(template)
    }

    override suspend fun restoreLessonTimeTemplate(
        template: LessonTimeTemplateEntity,
        items: List<LessonTimeTemplateItemEntity>,
    ) {
        lessonTimeTemplateDao.restoreLessonTimeTemplate(template, items)
    }

    override fun observeDayNotes(timetableId: Long): Flow<List<DayNoteEntity>> =
        dayNoteDao.getDayNotesFlow(timetableId)

    override suspend fun upsertDayNote(note: DayNoteEntity): Long =
        dayNoteDao.upsertDayNote(note)

    override suspend fun deleteDayNote(timetableId: Long, date: String) {
        dayNoteDao.deleteDayNote(timetableId, date)
    }
}
