package com.star.schedule.feature.settings.data

import com.star.schedule.core.common.Constants
import com.star.schedule.core.database.ScheduleDao
import com.star.schedule.feature.settings.domain.SettingsRepository
import com.star.schedule.feature.settings.domain.TimetableSummary
import com.star.schedule.notification.UnifiedNotificationManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSettingsRepository(
    private val dao: ScheduleDao,
    private val notificationManager: UnifiedNotificationManager,
) : SettingsRepository {
    override fun observeTimetables(): Flow<List<TimetableSummary>> =
        dao.getAllTimetables().map { timetables ->
            timetables.map { timetable ->
                TimetableSummary(
                    id = timetable.id,
                    name = timetable.name,
                    reminderTime = timetable.reminderTime,
                )
            }
        }

    override fun observePreference(key: String): Flow<String?> =
        dao.getPreferenceFlow(key)

    override suspend fun setPreference(key: String, value: String) {
        dao.setPreference(key, value)
    }

    override suspend fun selectTimetable(timetableId: Long) {
        // Switching the active timetable must not leave reminders scheduled for
        // a timetable that is no longer being viewed.
        disableReminders()
        dao.setPreference(Constants.PREF_CURRENT_TIMETABLE, timetableId.toString())
    }

    override fun isLiveCapsuleCustomizationAvailable(): Boolean =
        notificationManager.isLiveCapsuleCustomizationAvailable()

    override fun isReminderEnabledForTimetable(timetableId: Long): Boolean =
        notificationManager.isReminderEnabledForTimetableSync(timetableId)

    override suspend fun enableRemindersForTimetable(timetableId: Long) {
        notificationManager.enableRemindersForTimetable(timetableId)
    }

    override suspend fun disableReminders() {
        notificationManager.disableReminders()
    }

    override fun sendTestNotification() {
        notificationManager.sendTestNotification()
    }

    override fun scheduleTestReminder() {
        notificationManager.scheduleTestReminder()
    }
}
