package com.star.schedule.platform.notification

import android.util.Log
import com.star.schedule.core.common.Constants
import com.star.schedule.core.database.ScheduleDao
import com.star.schedule.feature.timetable.domain.ReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull

/**
 * 节次时间或课程改动后重排提醒。
 *
 * 两个 guard 缺一不可：
 * - 只有「当前课表」才需要重排，别的课表改数据不该动提醒；
 * - 只有「提醒当前是开着的」才重排；调度器会在实际执行前再确认，避免等待期间用户已关闭提醒。
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
            scheduler?.refreshRemindersForTimetable(currentTimetableId)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            Log.e(TAG, "启用课表提醒失败", error)
        }
    }

    suspend fun disableIfEnabled(timetableId: Long) {
        val enabledTimetableId = dao
            .getPreferenceFlow(Constants.PREF_REMINDER_ENABLED_TIMETABLE)
            .firstOrNull()
            ?.toLongOrNull()
        if (enabledTimetableId == timetableId) {
            scheduler?.disableRemindersForTimetable(timetableId)
        }
    }

    private companion object {
        const val TAG = "ReminderRefresher"
    }
}
