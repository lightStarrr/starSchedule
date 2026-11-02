package com.star.schedule.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.star.schedule.platform.notification.CourseReminderUpdateWorker

class DailyUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 两周的闹钟重排可能超过广播时限，交给持久化后台任务处理。
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "course_reminder_daily_update",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CourseReminderUpdateWorker>().build(),
        )
    }
}
