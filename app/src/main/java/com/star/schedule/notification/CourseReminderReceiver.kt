package com.star.schedule.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.star.schedule.core.common.Constants
import com.star.schedule.core.database.DatabaseProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CourseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val courseName = intent.getStringExtra("course_name") ?: return
        val courseLocation = intent.getStringExtra("course_location") ?: ""
        val courseTime = intent.getStringExtra("course_time") ?: ""

        if (!DatabaseProvider.isInitialized()) {
            DatabaseProvider.init(context)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = DatabaseProvider.dao()
                val enabledTimetableId =
                    dao.getPreferenceFlow(Constants.PREF_REMINDER_ENABLED_TIMETABLE).first()

                if (enabledTimetableId.isNullOrEmpty()) {
                    Log.w("UnifiedNotification", "未找到启用的课表提醒设置")
                    withContext(Dispatchers.Main) {
                        UnifiedNotificationManager(context).showCourseNotification(
                            courseName = courseName,
                            location = courseLocation,
                            startTime = courseTime,
                        )
                    }
                    return@launch
                }

                val timetableId = enabledTimetableId.toLongOrNull()
                if (timetableId == null) {
                    Log.e("UnifiedNotification", "启用的课表ID格式错误: $enabledTimetableId")
                    withContext(Dispatchers.Main) {
                        UnifiedNotificationManager(context).showCourseNotification(
                            courseName = courseName,
                            location = courseLocation,
                            startTime = courseTime,
                        )
                    }
                    return@launch
                }

                UnifiedNotificationManager(context).showCourseNotification(
                    courseName = courseName,
                    location = courseLocation,
                    startTime = courseTime,
                )
            } catch (exception: Exception) {
                Log.e("UnifiedNotification", "处理课程提醒时出错", exception)
                withContext(Dispatchers.Main) {
                    UnifiedNotificationManager(context).showCourseNotification(
                        courseName = courseName,
                        location = courseLocation,
                        startTime = courseTime,
                    )
                }
            }
        }
    }
}
