package com.star.schedule.feature.schedule.data

import com.star.schedule.core.common.Constants
import com.star.schedule.core.database.dao.CourseDao
import com.star.schedule.core.database.dao.DayNoteDao
import com.star.schedule.core.database.dao.LessonTimeDao
import com.star.schedule.core.database.dao.LessonTimeTemplateDao
import com.star.schedule.core.database.dao.PreferenceDao
import com.star.schedule.core.database.dao.TimetableDao
import com.star.schedule.core.database.entity.CourseEntity
import com.star.schedule.core.database.entity.CourseSessionEntity
import com.star.schedule.core.database.entity.CourseWithSessions
import com.star.schedule.core.database.entity.DayNoteEntity
import com.star.schedule.core.database.entity.LessonTimeEntity
import com.star.schedule.core.database.entity.LessonTimeTemplateEntity
import com.star.schedule.core.database.entity.LessonTimeTemplateItemEntity
import com.star.schedule.core.database.entity.PreferenceEntity
import com.star.schedule.core.database.entity.TimetableEntity
import com.star.schedule.feature.timetable.data.RoomTimetableRepository
import com.star.schedule.feature.timetable.domain.ReminderScheduler
import com.star.schedule.platform.notification.ReminderRefresher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReminderRepositoryTest {
    private val preferences = mutableMapOf(
        Constants.PREF_CURRENT_TIMETABLE to "1",
        Constants.PREF_REMINDER_ENABLED_TIMETABLE to "1",
    )
    private val lessonTimes = mutableListOf<LessonTimeEntity>()
    private val timetables = mutableListOf(
        TimetableEntity(1L, "课表一", true, "2026-09-01"),
        TimetableEntity(2L, "课表二", true, "2026-09-01"),
    )
    private val events = mutableListOf<String>()

    private val preferenceDao = object : PreferenceDao() {
        override fun getPreferenceFlow(prefKey: String) = flowOf(preferences[prefKey])
        override fun observeValuesForTimetables(prefix: String): Flow<List<String>> = error("Unexpected read")
        override suspend fun insertPreference(preference: PreferenceEntity) {
            preferences[preference.prefKey] = preference.value
        }
    }
    private val timetableDao = object : TimetableDao() {
        override fun getAllTimetables(): Flow<List<TimetableEntity>> = error("Unexpected read")
        override fun getTimetableFlow(id: Long): Flow<TimetableEntity?> = error("Unexpected read")
        override suspend fun insertTimetable(timetable: TimetableEntity): Long = error("Unexpected insert")
        override suspend fun updateTimetable(timetable: TimetableEntity) = error("Unexpected update")
        override suspend fun getAllTimetablesOnce() = timetables.toList()
        override suspend fun deleteTimetable(timetable: TimetableEntity) {
            events += "delete:${timetable.id}"
            timetables.remove(timetable)
        }
    }
    private val lessonTimeDao = object : LessonTimeDao() {
        override fun getLessonTimesFlow(timetableId: Long): Flow<List<LessonTimeEntity>> = error("Unexpected read")
        override suspend fun getLessonTimesOnce(timetableId: Long): List<LessonTimeEntity> = error("Unexpected read")
        override suspend fun insertLessonTime(lessonTime: LessonTimeEntity): Long = error("Unexpected insert")
        override suspend fun updateLessonTime(lessonTime: LessonTimeEntity) = error("Unexpected update")
        override suspend fun deleteLessonTime(lessonTime: LessonTimeEntity) = error("Unexpected delete")
        override suspend fun deleteLessonTimesByTimetableId(timetableId: Long) = error("Unexpected delete")
        override suspend fun insertOrUpdateLessonTimeAutoSort(lessonTime: LessonTimeEntity, isInsert: Boolean): Long {
            lessonTimes.removeAll { it.id == lessonTime.id }
            lessonTimes += lessonTime
            return lessonTime.id
        }
        override suspend fun deleteLessonTimeAutoSort(lessonTime: LessonTimeEntity) {
            lessonTimes.removeAll { it.id == lessonTime.id }
        }
    }
    private val courseDao = object : CourseDao() {
        override fun getCoursesWithSessionsFlow(timetableId: Long): Flow<List<CourseWithSessions>> = error("Unexpected read")
        override fun getAllCoursesWithSessionsFlow(): Flow<List<CourseWithSessions>> = error("Unexpected read")
        override suspend fun findCourseByNameOnce(timetableId: Long, name: String): CourseEntity? = error("Unexpected read")
        override suspend fun insertCourse(course: CourseEntity): Long = error("Unexpected insert")
        override suspend fun updateCourse(course: CourseEntity) = error("Unexpected update")
        override suspend fun deleteCourse(course: CourseEntity) = error("Unexpected delete")
        override suspend fun deleteCoursesByTimetableId(timetableId: Long) = error("Unexpected delete")
        override suspend fun insertSession(session: CourseSessionEntity): Long = error("Unexpected insert")
        override suspend fun updateSession(session: CourseSessionEntity) = error("Unexpected update")
        override suspend fun deleteSession(session: CourseSessionEntity) = error("Unexpected delete")
    }
    private val templateDao = object : LessonTimeTemplateDao() {
        override fun getLessonTimeTemplatesFlow(): Flow<List<LessonTimeTemplateEntity>> = error("Unexpected read")
        override suspend fun getLessonTimeTemplateByNameOnce(name: String): LessonTimeTemplateEntity? = error("Unexpected read")
        override suspend fun getLessonTimeTemplateByIdOnce(templateId: Long): LessonTimeTemplateEntity? = error("Unexpected read")
        override suspend fun insertLessonTimeTemplate(template: LessonTimeTemplateEntity): Long = error("Unexpected insert")
        override suspend fun updateLessonTimeTemplate(template: LessonTimeTemplateEntity) = error("Unexpected update")
        override suspend fun deleteLessonTimeTemplate(template: LessonTimeTemplateEntity) = error("Unexpected delete")
        override suspend fun insertLessonTimeTemplateItems(items: List<LessonTimeTemplateItemEntity>) = error("Unexpected insert")
        override suspend fun deleteLessonTimeTemplateItemsByTemplateId(templateId: Long) = error("Unexpected delete")
        override suspend fun getLessonTimeTemplateItemsOnce(templateId: Long): List<LessonTimeTemplateItemEntity> = error("Unexpected read")
        override fun getLessonTimeTemplateItemsFlow(): Flow<List<LessonTimeTemplateItemEntity>> = error("Unexpected read")
    }
    private val dayNoteDao = object : DayNoteDao() {
        override fun getDayNotesFlow(timetableId: Long): Flow<List<DayNoteEntity>> = error("Unexpected read")
        override suspend fun upsertDayNote(note: DayNoteEntity): Long = error("Unexpected write")
        override suspend fun deleteDayNote(timetableId: Long, date: String) = error("Unexpected delete")
    }

    private val snapshots = mutableListOf<List<LessonTimeEntity>>()
    private val scheduler = object : ReminderScheduler {
        override suspend fun enableRemindersForTimetable(timetableId: Long) {
            error("Refreshing must not enable reminders")
        }

        override suspend fun refreshRemindersForTimetable(timetableId: Long) {
            snapshots += lessonTimes.toList()
        }

        override suspend fun disableRemindersForTimetable(timetableId: Long) {
            events += "disable"
            preferences[Constants.PREF_REMINDER_ENABLED_TIMETABLE] = ""
        }
    }
    private val refresher = ReminderRefresher(preferenceDao, scheduler)
    private val repository = RoomScheduleRepository(
        preferenceDao = preferenceDao,
        timetableDao = timetableDao,
        courseDao = courseDao,
        lessonTimeDao = lessonTimeDao,
        lessonTimeTemplateDao = templateDao,
        dayNoteDao = dayNoteDao,
        reminderRefresher = refresher,
    )
    private val timetableRepository = RoomTimetableRepository(
        preferenceDao = preferenceDao,
        timetableDao = timetableDao,
        courseDao = courseDao,
        lessonTimeDao = lessonTimeDao,
        lessonTimeTemplateDao = templateDao,
        reminderScheduler = scheduler,
    )
    private val lesson = LessonTimeEntity(11L, 1L, 1, "08:00", "08:45")

    @Test
    fun `insert update delete and undo refresh the persisted lesson times`() = runTest {
        repository.insertLessonTime(lesson)
        val edited = lesson.copy(startTime = "09:00", endTime = "09:45")
        repository.updateLessonTime(edited)
        repository.deleteLessonTime(edited)
        repository.insertLessonTime(edited)

        assertEquals(listOf(listOf(lesson), listOf(edited), emptyList(), listOf(edited)), snapshots)
    }

    @Test
    fun `editing with reminders disabled does not enable them`() = runTest {
        preferences[Constants.PREF_REMINDER_ENABLED_TIMETABLE] = ""
        repository.insertLessonTime(lesson)

        assertEquals(listOf(lesson), lessonTimes)
        assertTrue(snapshots.isEmpty())
        assertEquals("", preferences[Constants.PREF_REMINDER_ENABLED_TIMETABLE])
    }

    @Test
    fun `editing another timetable does not reschedule the active timetable`() = runTest {
        repository.insertLessonTime(lesson.copy(timetableId = 2L))

        assertTrue(snapshots.isEmpty())
    }

    @Test
    fun `deleting the enabled timetable cancels reminders before deleting data`() = runTest {
        timetableRepository.deleteTimetableWithReminders(timetables.first())

        assertEquals(listOf("disable", "delete:1"), events)
        assertEquals("", preferences[Constants.PREF_REMINDER_ENABLED_TIMETABLE])
        assertEquals("2", preferences[Constants.PREF_CURRENT_TIMETABLE])
    }

    @Test
    fun `deleting another timetable keeps active reminders`() = runTest {
        timetableRepository.deleteTimetableWithReminders(timetables.last())

        assertEquals(listOf("delete:2"), events)
        assertEquals("1", preferences[Constants.PREF_REMINDER_ENABLED_TIMETABLE])
    }

    @Test
    fun `refresh propagates cancellation`() = runTest {
        val cancellingScheduler = object : ReminderScheduler {
            override suspend fun enableRemindersForTimetable(timetableId: Long) {
                error("Refreshing must not enable reminders")
            }

            override suspend fun refreshRemindersForTimetable(timetableId: Long) {
                throw CancellationException("Stopped")
            }

            override suspend fun disableRemindersForTimetable(timetableId: Long) = Unit
        }
        try {
            ReminderRefresher(preferenceDao, cancellingScheduler).refreshIfCurrent(1L)
            fail("Cancellation should propagate")
        } catch (_: CancellationException) {
            // 取消不能转成一次普通的刷新失败后继续执行。
        }
    }
}
