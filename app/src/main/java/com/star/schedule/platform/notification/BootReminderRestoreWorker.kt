package com.star.schedule.platform.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.star.schedule.core.database.DatabaseProvider
import kotlinx.coroutines.CancellationException

class BootReminderRestoreWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        DatabaseProvider.init(applicationContext)
        DatabaseProvider.ensureValidCurrentTimetable()
        UnifiedNotificationManager(applicationContext).restoreRemindersAfterBoot()
        Result.success()
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Exception) {
        Log.e("BootReminderRestore", "重启后恢复提醒失败", error)
        Result.retry()
    }
}
