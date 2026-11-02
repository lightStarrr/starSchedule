package com.star.schedule.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.star.schedule.core.common.Constants
import com.star.schedule.core.database.DatabaseProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class CourseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val courseName = intent.getStringExtra("course_name") ?: return
        val courseLocation = intent.getStringExtra("course_location").orEmpty()
        val courseTime = intent.getStringExtra("course_time").orEmpty()
        val timetableId = intent.getLongExtra(EXTRA_TIMETABLE_ID, -1L)
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeout(9_000) {
                    DatabaseProvider.init(appContext)
                    // 已投递的旧广播也要检查课表，防止关闭或切换后仍显示提醒。
                    // 测试提醒与旧版本闹钟没有此字段，保持原来的兼容行为。
                    if (timetableId != -1L) {
                        val enabledId = DatabaseProvider.dao()
                            .getPreferenceFlow(Constants.PREF_REMINDER_ENABLED_TIMETABLE)
                            .first()
                            ?.toLongOrNull()
                        if (enabledId != timetableId) return@withTimeout
                    }
                    UnifiedNotificationManager(appContext).showCourseNotification(
                        courseName = courseName,
                        location = courseLocation,
                        startTime = courseTime,
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                Log.e("UnifiedNotification", "处理课程提醒时出错", error)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    internal companion object {
        const val EXTRA_TIMETABLE_ID = "timetable_id"
    }
}
