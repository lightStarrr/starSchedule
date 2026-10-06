package com.star.schedule.feature.settings.domain

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeTimetables(): Flow<List<TimetableSummary>>

    fun observePreference(key: String): Flow<String?>

    suspend fun setPreference(key: String, value: String)

    suspend fun selectTimetable(timetableId: Long)

    fun isLiveCapsuleCustomizationAvailable(): Boolean

    fun isWakeUpSimulationAvailable(): Boolean

    fun isWakeUpProxyInstalled(): Boolean

    suspend fun importLiveCapsuleIcon(uriString: String): Result<Unit>

    suspend fun clearLiveCapsuleIcon()

    fun isReminderEnabledForTimetable(timetableId: Long): Boolean

    suspend fun enableRemindersForTimetable(timetableId: Long)

    suspend fun disableReminders()

    fun sendTestNotification()

    fun scheduleTestReminder()
}
