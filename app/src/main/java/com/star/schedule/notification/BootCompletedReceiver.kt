package com.star.schedule.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == Intent.ACTION_PACKAGE_REPLACED
        ) {
            Log.d("BootCompleted", "收到系统启动广播: ${intent.action}")

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    UnifiedNotificationManager(context).restoreRemindersAfterBoot()
                } catch (exception: Exception) {
                    Log.e("BootCompleted", "重新设置提醒失败", exception)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
