package com.star.schedule.notification

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class CourseNotificationWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val KEY_COURSE_NAME = "course_name"
        const val KEY_LOCATION = "location"
        const val KEY_START_TIME = "start_time"
        const val KEY_MINUTES_BEFORE = "minutes_before"
        const val KEY_STAGE = "stage" // "initial", "update", "cancel"
        const val TAG = "CourseNotificationWorker"

        /**
         * 静态方法，用于调度完整的通知流程
         */
        fun scheduleCourseNotification(
            context: Context,
            courseName: String,
            location: String,
            startTime: String,
            minutesBefore: Int
        ) {
            val initialData = Data.Builder()
                .putString(KEY_COURSE_NAME, courseName)
                .putString(KEY_LOCATION, location)
                .putString(KEY_START_TIME, startTime)
                .putInt(KEY_MINUTES_BEFORE, minutesBefore)
                .putString(KEY_STAGE, "initial")
                .build()

            val initialWorkRequest = OneTimeWorkRequestBuilder<CourseNotificationWorker>()
                .setInputData(initialData)
                .addTag("course_notification_initial")
                .build()

            WorkManager.getInstance(context).enqueue(initialWorkRequest)
            
            Log.d(TAG, "已安排课程通知工作: $courseName, 提前: ${minutesBefore}分钟")
        }
    }

    override fun doWork(): Result {
        val stage = inputData.getString(KEY_STAGE) ?: "initial"
        val courseName = inputData.getString(KEY_COURSE_NAME) ?: ""
        val location = inputData.getString(KEY_LOCATION) ?: ""
        val startTime = inputData.getString(KEY_START_TIME) ?: ""
        val minutesBefore = inputData.getInt(KEY_MINUTES_BEFORE, 15)

        Log.d(TAG, "执行通知工作 - 阶段: $stage, 课程: $courseName")

        return try {
            val notificationManager = UnifiedNotificationManager(applicationContext)

            when (stage) {
                "initial" -> {
                    // 检查必要参数
                    if (courseName.isEmpty()) {
                        Log.e(TAG, "课程名称为空，无法显示通知")
                        return Result.failure()
                    }
                    
                    // 显示初始通知
                    notificationManager.showCourseNotificationImmediate(courseName, location, startTime, minutesBefore)
                    
                    // 安排更新通知的工作
                    if (minutesBefore > 0) {
                        scheduleUpdateNotification(courseName, location, startTime, minutesBefore)
                    }
                }
                "update" -> {
                    // 检查必要参数
                    if (courseName.isEmpty()) {
                        Log.e(TAG, "课程名称为空，无法更新通知")
                        return Result.failure()
                    }
                    
                    // 显示更新后的通知（课程已开始）
                    notificationManager.showCourseNotificationImmediate(courseName, location, startTime, 0)
                    
                    // 安排取消通知的工作
                    scheduleCancelNotification(courseName, location, startTime)
                }
                "cancel" -> {
                    // 取消通知 - 这个阶段不需要课程信息
                    notificationManager.cancelCourseNotification()
                    Log.d(TAG, "通知已取消")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "执行通知工作失败", e)
            Result.failure()
        }
    }

    private fun scheduleUpdateNotification(
        courseName: String,
        location: String,
        startTime: String,
        minutesBefore: Int
    ) {
        val updateData = Data.Builder()
            .putString(KEY_COURSE_NAME, courseName)
            .putString(KEY_LOCATION, location)
            .putString(KEY_START_TIME, startTime)
            .putInt(KEY_MINUTES_BEFORE, 0)
            .putString(KEY_STAGE, "update")
            .build()

        val updateWorkRequest = OneTimeWorkRequestBuilder<CourseNotificationWorker>()
            .setInitialDelay(minutesBefore.toLong(), TimeUnit.MINUTES)
            .setInputData(updateData)
            .addTag("course_notification_update")
            .build()

        WorkManager.getInstance(applicationContext).enqueue(updateWorkRequest)
        
        Log.d(TAG, "已安排更新通知工作，延迟: ${minutesBefore}分钟")
    }

    private fun scheduleCancelNotification(
        courseName: String,
        location: String,
        startTime: String
    ) {
        val cancelData = Data.Builder()
            .putString(KEY_COURSE_NAME, courseName)
            .putString(KEY_LOCATION, location)
            .putString(KEY_START_TIME, startTime)
            .putString(KEY_STAGE, "cancel")
            .build()

        val cancelWorkRequest = OneTimeWorkRequestBuilder<CourseNotificationWorker>()
            .setInitialDelay(1, TimeUnit.MINUTES)
            .setInputData(cancelData)
            .addTag("course_notification_cancel")
            .build()

        WorkManager.getInstance(applicationContext).enqueue(cancelWorkRequest)
        
        Log.d(TAG, "已安排取消通知工作，延迟: 1分钟")
    }
}