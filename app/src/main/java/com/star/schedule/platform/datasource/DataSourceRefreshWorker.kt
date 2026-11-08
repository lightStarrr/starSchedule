package com.star.schedule.platform.datasource

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.star.schedule.ScheduleApplication
import kotlinx.coroutines.CancellationException

class DataSourceRefreshWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        Log.d("DataSourceRefresh", "定时刷新数据源开始")
        (applicationContext as ScheduleApplication)
            .dataSourceDependencies
            .repository
            .refreshAll()
        Result.success()
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Exception) {
        Log.e("DataSourceRefresh", "定时刷新数据源失败", error)
        Result.retry()
    }
}
