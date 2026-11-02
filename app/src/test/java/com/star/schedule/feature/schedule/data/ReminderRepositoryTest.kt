package com.star.schedule.feature.schedule.data

import com.star.schedule.core.common.Constants
import com.star.schedule.core.database.LessonTimeEntity
import com.star.schedule.core.database.PreferenceEntity
import com.star.schedule.core.database.StubScheduleDao
import com.star.schedule.core.database.TimetableEntity
import com.star.schedule.feature.timetable.data.RoomTimetableRepository
import com.star.schedule.feature.timetable.domain.ReminderScheduler
import com.star.schedule.platform.notification.ReminderRefresher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReminderRepositoryTest {
    private val dao = ReminderTestDao()
    private val snapshots = mutableListOf<List<LessonTimeEntity>>()
    private val scheduler = object : ReminderScheduler {
        override suspend fun enableRemindersForTimetable(timetableId: Long) {
            error("Refreshing must not enable reminders")
        }

        override suspend fun refreshRemindersForTimetable(timetableId: Long) {
            snapshots += dao.lessonTimes.toList()
        }

        override suspend fun disableRemindersForTimetable(timetableId: Long) {
            dao.events += "disable"
            dao.preferences[Constants.PREF_REMINDER_ENABLED_TIMETABLE] = ""
        }
    }
    private val refresher = ReminderRefresher(dao, scheduler)
    private val repository = RoomScheduleRepository(dao, refresher)
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
        dao.preferences[Constants.PREF_REMINDER_ENABLED_TIMETABLE] = ""
        repository.insertLessonTime(lesson)

        assertEquals(listOf(lesson), dao.lessonTimes)
        assertTrue(snapshots.isEmpty())
        assertEquals("", dao.preferences[Constants.PREF_REMINDER_ENABLED_TIMETABLE])
    }

    @Test
    fun `editing another timetable does not reschedule the active timetable`() = runTest {
        repository.insertLessonTime(lesson.copy(timetableId = 2L))

        assertTrue(snapshots.isEmpty())
    }

    @Test
    fun `deleting the enabled timetable cancels reminders before deleting data`() = runTest {
        RoomTimetableRepository(dao, scheduler).deleteTimetableWithReminders(dao.timetables.first())

        assertEquals(listOf("disable", "delete:1"), dao.events)
        assertEquals("", dao.preferences[Constants.PREF_REMINDER_ENABLED_TIMETABLE])
        assertEquals("2", dao.preferences[Constants.PREF_CURRENT_TIMETABLE])
    }

    @Test
    fun `deleting another timetable keeps active reminders`() = runTest {
        RoomTimetableRepository(dao, scheduler).deleteTimetableWithReminders(dao.timetables.last())

        assertEquals(listOf("delete:2"), dao.events)
        assertEquals("1", dao.preferences[Constants.PREF_REMINDER_ENABLED_TIMETABLE])
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
            ReminderRefresher(dao, cancellingScheduler).refreshIfCurrent(1L)
            fail("Cancellation should propagate")
        } catch (_: CancellationException) {
            // 取消不能转成一次普通的刷新失败后继续执行。
        }
    }
}

private class ReminderTestDao : StubScheduleDao() {
    val preferences = mutableMapOf(
        Constants.PREF_CURRENT_TIMETABLE to "1",
        Constants.PREF_REMINDER_ENABLED_TIMETABLE to "1",
    )
    val lessonTimes = mutableListOf<LessonTimeEntity>()
    val timetables = mutableListOf(
        TimetableEntity(1L, "课表一", true, "2026-09-01"),
        TimetableEntity(2L, "课表二", true, "2026-09-01"),
    )
    val events = mutableListOf<String>()

    override fun getPreferenceFlow(prefKey: String) = flowOf(preferences[prefKey])

    override suspend fun insertPreference(preference: PreferenceEntity) {
        preferences[preference.prefKey] = preference.value
    }

    override suspend fun insertOrUpdateLessonTimeAutoSort(lessonTime: LessonTimeEntity, isInsert: Boolean): Long {
        lessonTimes.removeAll { it.id == lessonTime.id }
        lessonTimes += lessonTime
        return lessonTime.id
    }

    override suspend fun deleteLessonTimeAutoSort(lessonTime: LessonTimeEntity) {
        lessonTimes.removeAll { it.id == lessonTime.id }
    }

    override suspend fun getAllTimetablesOnce() = timetables.toList()

    override suspend fun deleteTimetable(timetable: TimetableEntity) {
        events += "delete:${timetable.id}"
        timetables.remove(timetable)
    }
}
