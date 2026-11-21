package com.star.schedule.platform.widget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.star.schedule.widget.TwoDaysWidget
import kotlinx.coroutines.CancellationException

class TwoDaysWidgetSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = try {
        TwoDaysWidget.updateWidgetContent(applicationContext)
        Result.success()
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Exception) {
        Log.e(TAG, "后台刷新小组件数据失败", error)
        Result.retry()
    }

    companion object {
        private const val TAG = "TwoDaysWidgetSync"
        private const val WORK_NAME = "two_days_widget_sync"

        fun enqueue(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<TwoDaysWidgetSyncWorker>().build(),
            )
        }
    }
}
