package com.star.schedule.feature.timetable.domain

interface ReminderScheduler {
    suspend fun enableRemindersForTimetable(timetableId: Long)

    suspend fun refreshRemindersForTimetable(timetableId: Long)

    suspend fun disableRemindersForTimetable(timetableId: Long)
}
