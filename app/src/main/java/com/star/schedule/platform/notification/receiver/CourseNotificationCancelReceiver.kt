package com.star.schedule.platform.notification.receiver

import com.star.schedule.platform.notification.UnifiedNotificationManager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class CourseNotificationCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("CourseCancelReceiver", "收到取消通知广播")
        UnifiedNotificationManager(context).cancelCourseNotification()
    }
}
