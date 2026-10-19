package com.star.schedule.feature.settings.domain

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeTimetables(): Flow<List<TimetableSummary>>

    fun observeTimetablePreviews(): Flow<List<TimetablePreview>>

    suspend fun setRowHeight(timetableId: Long, rowHeight: Int)

    fun observePreference(key: String): Flow<String?>

    suspend fun setPreference(key: String, value: String)

    suspend fun selectTimetable(timetableId: Long)

    /** 改名只动名字，课程、节次和提醒都不受影响。 */
    suspend fun renameTimetable(timetableId: Long, name: String)

    /** 连课程、节次一起删；删的是当前课表时自动切到剩下的一张。 */
    suspend fun deleteTimetable(timetableId: Long)

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
