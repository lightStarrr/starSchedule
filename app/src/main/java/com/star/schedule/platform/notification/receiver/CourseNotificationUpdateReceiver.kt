package com.star.schedule.platform.notification.receiver

import com.star.schedule.platform.notification.UnifiedNotificationManager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.star.schedule.core.database.DatabaseProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class CourseNotificationUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val courseName = intent.getStringExtra("course_name") ?: return
        val location = intent.getStringExtra("course_location") ?: ""
        val startTime = intent.getStringExtra("course_time") ?: ""
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        Log.d("CourseUpdateReceiver", "收到更新通知广播: $courseName")

        receiverScope.launch {
            try {
                withTimeout(9_000) {
                    DatabaseProvider.init(appContext)
                    DatabaseProvider.ensureValidCurrentTimetable()
                    UnifiedNotificationManager(appContext).showCourseNotificationImmediate(
                        courseName = courseName,
                        location = location,
                        startTime = startTime,
                        finish = true,
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                Log.e("CourseUpdateReceiver", "更新课程通知失败", error)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private companion object {
        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
