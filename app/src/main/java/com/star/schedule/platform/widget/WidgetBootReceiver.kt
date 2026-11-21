package com.star.schedule.platform.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 系统启动广播接收器，处理系统重启、应用更新后的 JobScheduler 重新调度。
 */
class WidgetBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WidgetBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val shouldSchedule = when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> true
            Intent.ACTION_PACKAGE_REPLACED ->
                intent.data?.schemeSpecificPart == context.packageName
            else -> false
        }
        if (!shouldSchedule) return

        Log.d(TAG, "Rescheduling widget update job on ${intent?.action}")
        WidgetUpdateJobService.scheduleJob(context.applicationContext)
    }
}
