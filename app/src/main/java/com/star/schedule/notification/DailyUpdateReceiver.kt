package com.star.schedule.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DailyUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("DailyUpdate", "执行定期提醒更新")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                UnifiedNotificationManager(context).updateUpcomingReminders()
            } catch (exception: Exception) {
                Log.e("DailyUpdate", "定期更新提醒失败", exception)
            }
        }
    }
}
