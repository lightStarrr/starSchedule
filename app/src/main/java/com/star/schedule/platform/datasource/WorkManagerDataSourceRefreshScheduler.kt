package com.star.schedule.platform.datasource

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.star.schedule.feature.datasource.domain.DataSourceRefreshScheduler
import java.util.concurrent.TimeUnit

class WorkManagerDataSourceRefreshScheduler(
    private val context: Context,
) : DataSourceRefreshScheduler {

    override fun schedule(intervalDays: Int) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        if (intervalDays <= 0) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<DataSourceRefreshWorker>(
            intervalDays.toLong(),
            TimeUnit.DAYS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "datasource_refresh"
    }
}
