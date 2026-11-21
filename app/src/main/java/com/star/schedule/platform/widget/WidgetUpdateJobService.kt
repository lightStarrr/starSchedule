package com.star.schedule.platform.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * JobScheduler 服务类，用于定时更新小组件数据。
 * 支持系统重启、应用更新等场景下的自动重新调度。
 */
class WidgetUpdateJobService : JobService() {

    companion object {
        private const val TAG = "WidgetUpdateJobService"
        private const val JOB_ID = 1001

        fun scheduleJob(context: Context) {
            val appContext = context.applicationContext
            val jobScheduler =
                appContext.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler

            val componentName = ComponentName(appContext, WidgetUpdateJobService::class.java)
            val jobInfo = JobInfo.Builder(JOB_ID, componentName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                .setPersisted(true)
                .setPeriodic(JobInfo.getMinPeriodMillis())
                .build()

            val result = jobScheduler.schedule(jobInfo)

            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.d(TAG, "Job scheduled successfully")
            } else {
                Log.e(TAG, "Failed to schedule job")
            }
        }

        fun cancelJob(context: Context) {
            val jobScheduler =
                context.applicationContext.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(JOB_ID)
            Log.d(TAG, "Job cancelled")
        }

        fun isJobScheduled(context: Context): Boolean {
            val jobScheduler =
                context.applicationContext.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            val jobs = jobScheduler.allPendingJobs
            return jobs.any { it.id == JOB_ID }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "Job started")
        job?.cancel()
        job = serviceScope.launch {
            try {
                WidgetRefreshManager.refreshWidgetImmediately(applicationContext)
                Log.d(TAG, "Widget updated successfully")
                jobFinished(params, false)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                Log.e(TAG, "Error updating widget: ${e.message}", e)
                jobFinished(params, true)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d(TAG, "Job stopped")
        job?.cancel()
        return true
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
