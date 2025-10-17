package com.star.schedule.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * JobScheduler服务类，用于定时更新小组件数据
 * 支持系统重启、应用更新等场景下的自动重新调度
 */
class WidgetUpdateJobService : JobService() {

    companion object {
        private const val TAG = "WidgetUpdateJobService"
        private const val JOB_ID = 1001

        /**
         * 调度定时任务
         */
        fun scheduleJob(context: Context) {
            val jobScheduler =
                context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler

            val componentName = ComponentName(context, WidgetUpdateJobService::class.java)
            val jobInfo = JobInfo.Builder(JOB_ID, componentName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE) // 不需要网络
                .setPersisted(true) // 系统重启后保持
                .setPeriodic(JobInfo.getMinPeriodMillis())
                .build()

            val result = jobScheduler.schedule(jobInfo)

            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.d(TAG, "Job scheduled successfully")
            } else {
                Log.e(TAG, "Failed to schedule job")
            }
        }

        /**
         * 取消定时任务
         */
        fun cancelJob(context: Context) {
            val jobScheduler =
                context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(JOB_ID)
            Log.d(TAG, "Job cancelled")
        }

        /**
         * 检查任务是否已调度
         */
        fun isJobScheduled(context: Context): Boolean {
            val jobScheduler =
                context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler
            val jobs = jobScheduler.allPendingJobs
            return jobs.any { it.id == JOB_ID }
        }
    }

    private var job: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "Job started")

        // 使用协程异步执行小组件更新
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetRefreshManager.refreshWidgetImmediately(this@WidgetUpdateJobService)
                Log.d(TAG, "Widget updated successfully")

                // 标记任务完成
                jobFinished(params, false)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating widget: ${e.message}", e)
                // 任务失败，需要重新调度
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
}