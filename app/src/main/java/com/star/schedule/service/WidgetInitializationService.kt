package com.star.schedule.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * 应用初始化服务
 * 在应用启动时确保JobScheduler任务被正确调度
 */
class WidgetInitializationService : Service() {

    companion object {
        private const val TAG = "WidgetInitializationService"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Widget initialization service started")

        if (!WidgetUpdateJobService.isJobScheduled(this)) {
            WidgetUpdateJobService.scheduleJob(this)
            Log.d(TAG, "JobScheduler task scheduled during app startup")
        } else {
            Log.d(TAG, "JobScheduler task already scheduled")
        }

        stopSelf()

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}