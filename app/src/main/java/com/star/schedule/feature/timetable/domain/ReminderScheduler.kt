package com.star.schedule.feature.timetable.domain

interface ReminderScheduler {
    suspend fun enableRemindersForTimetable(timetableId: Long)
}
