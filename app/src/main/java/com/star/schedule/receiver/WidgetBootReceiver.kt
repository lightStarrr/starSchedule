package com.star.schedule.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.star.schedule.service.WidgetUpdateJobService

/**
 * 系统启动广播接收器
 * 处理系统重启后的JobScheduler重新调度
 */
class WidgetBootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "WidgetBootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "System boot completed, rescheduling widget update job")
                
                WidgetUpdateJobService.scheduleJob(context)
            }
            
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                // 检查是否是当前应用被更新
                if (intent.data?.schemeSpecificPart == context.packageName) {
                    Log.d(TAG, "App updated, rescheduling widget update job")
                    
                    // 应用更新后重新调度JobScheduler任务
                    WidgetUpdateJobService.scheduleJob(context)
                }
            }
        }
    }
}