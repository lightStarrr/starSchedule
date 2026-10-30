package com.star.schedule.platform.notification

import android.util.Log
import com.star.schedule.core.common.Constants
import com.star.schedule.core.database.ScheduleDao
import com.star.schedule.feature.timetable.domain.ReminderScheduler
import kotlinx.coroutines.flow.firstOrNull

/**
 * 节次时间或课程改动后重排提醒。
 *
 * 两个 guard 缺一不可：
 * - 只有「当前课表」才需要重排，别的课表改数据不该动提醒；
 * - 只有「提醒当前是开着的」才重排，因为 [ReminderScheduler.enableRemindersForTimetable]
 *   会无条件把提醒偏好写成已启用——没有第二道 guard，用户关掉的提醒会被悄悄打开。
 */
class ReminderRefresher(
    private val dao: ScheduleDao,
    private val scheduler: ReminderScheduler?,
) {
    suspend fun refreshIfCurrent(timetableId: Long) {
        val currentTimetableId = dao
            .getPreferenceFlow(Constants.PREF_CURRENT_TIMETABLE)
            .firstOrNull()
            ?.toLongOrNull()
        if (currentTimetableId != timetableId) return

        val enabledTimetableId = dao
            .getPreferenceFlow(Constants.PREF_REMINDER_ENABLED_TIMETABLE)
            .firstOrNull()
            ?.toLongOrNull()
        if (enabledTimetableId != currentTimetableId) return

        try {
            scheduler?.enableRemindersForTimetable(currentTimetableId)
        } catch (error: Exception) {
            Log.e(TAG, "启用课表提醒失败", error)
        }
    }

    private companion object {
        const val TAG = "ReminderRefresher"
    }
}
