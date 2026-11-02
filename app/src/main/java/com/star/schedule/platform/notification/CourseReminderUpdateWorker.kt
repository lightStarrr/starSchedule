package com.star.schedule.platform.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.star.schedule.notification.UnifiedNotificationManager
import kotlinx.coroutines.CancellationException

class CourseReminderUpdateWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        UnifiedNotificationManager(applicationContext).updateUpcomingReminders()
        Result.success()
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Exception) {
        Log.e("CourseReminderUpdate", "定期更新提醒失败", error)
        Result.retry()
    }
}
