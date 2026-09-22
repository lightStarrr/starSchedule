package com.star.schedule.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.star.schedule.core.database.DatabaseProvider

class CourseNotificationUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val courseName = intent.getStringExtra("course_name") ?: return
        val location = intent.getStringExtra("course_location") ?: ""
        val startTime = intent.getStringExtra("course_time") ?: ""

        if (!DatabaseProvider.isInitialized()) {
            DatabaseProvider.init(context)
        }

        Log.d("CourseUpdateReceiver", "收到更新通知广播: $courseName")

        UnifiedNotificationManager(context).showCourseNotificationImmediate(
            courseName,
            location,
            startTime,
            true,
        )
    }
}
