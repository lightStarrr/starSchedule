package com.star.schedule.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.star.schedule.platform.notification.BootReminderRestoreWorker

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val shouldRestore = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> true
            Intent.ACTION_PACKAGE_REPLACED ->
                intent.data?.schemeSpecificPart == context.packageName
            else -> false
        }
        if (!shouldRestore) return

        Log.d("BootCompleted", "收到系统启动/更新广播: ${intent.action}")
        // 两周全量闹钟重建可能超过广播时限，统一交给持久化 WorkManager 任务执行。
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            "course_reminder_boot_restore",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BootReminderRestoreWorker>().build(),
        )
    }
}
