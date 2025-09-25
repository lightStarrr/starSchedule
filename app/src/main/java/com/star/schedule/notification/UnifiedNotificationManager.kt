package com.star.schedule.notification

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import com.star.schedule.MainActivity
import com.star.schedule.R
import com.star.schedule.db.CourseEntity
import com.star.schedule.db.DatabaseProvider
import com.star.schedule.db.LessonTimeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * 统一通知管理器
 * 自动根据手机厂商判断选择普通通知还是魅族的实况通知
 * 负责管理课程提醒通知的创建、取消和调度
 */
class UnifiedNotificationManager(private val context: Context) {
    
    private val notificationManager = context.getSystemService<NotificationManager>()!!
    private val alarmManager = context.getSystemService<AlarmManager>()!!
    
    companion object {
        const val CHANNEL_ID = "course_reminder"
        const val CHANNEL_NAME = "课程提醒"
        const val LIVE_CHANNEL_ID = "live_notification_channel"
        const val LIVE_CHANNEL_NAME = "实况通知"
        const val NOTIFICATION_REQUEST_CODE = 1000
        const val REMINDER_MINUTES = 15L // 课前15分钟提醒
        const val NOTIFICATION_ID = 1001
        
        // 用于存储当前启用提醒的课表ID的偏好设置键
        const val PREF_REMINDER_ENABLED_TIMETABLE = "reminder_enabled_timetable"
    }
    
    init {
        createNotificationChannels()
    }

    /**
     * 检查是否为魅族设备
     */
    private fun isMeizuDevice(): Boolean {
        return Build.MANUFACTURER.equals("meizu", ignoreCase = true)
    }
    
    /**
     * 创建通知渠道（包括普通通知和实况通知）
     */
    private fun createNotificationChannels() {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
            
        // 普通通知渠道
        val normalChannel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "提醒您即将上课"
            enableLights(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 250, 250)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setBypassDnd(true)
            setShowBadge(true)
            setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, audioAttributes)
        }
        
        // 魅族实况通知渠道
        val liveChannel = NotificationChannel(
            LIVE_CHANNEL_ID,
            LIVE_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "魅族实况通知频道"
            enableLights(true)
            enableVibration(true)
            setBypassDnd(true)
            setShowBadge(true)
        }
        
        notificationManager.createNotificationChannel(normalChannel)
        notificationManager.createNotificationChannel(liveChannel)
    }
    
    /**
     * 检查是否有通知权限
     */
    fun hasNotificationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 检查并记录通知设置状态（用于调试）
     */
    fun logNotificationStatus() {
        Log.d("UnifiedNotification", "通知权限: ${hasNotificationPermission()}")
        Log.d("UnifiedNotification", "设备厂商: ${Build.MANUFACTURER}")
        Log.d("UnifiedNotification", "使用魅族实况通知: ${isMeizuDevice()}")

        val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
        Log.d("UnifiedNotification", "通知渠道重要性: ${channel?.importance}")
    }
    
    /**
     * 发送课程通知（自动选择通知类型）
     * @param courseName 课程名称
     * @param location 上课地点
     * @param startTime 开始时间
     * @param minutesBefore 提前多少分钟提醒
     */
    fun showCourseNotification(
        courseName: String = "课程提醒",
        location: String = "",
        startTime: String = "",
        minutesBefore: Int = 15
    ) {
        if (!hasNotificationPermission()) {
            Log.w("UnifiedNotification", "没有通知权限，无法发送通知")
            return
        }
        
        if (isMeizuDevice()) {
            Log.d("UnifiedNotification", "检测到魅族设备，使用实况通知")
            try {
                showMeizuLiveNotification(courseName, location, startTime, minutesBefore)
            } catch (e: Exception) {
                Log.e("UnifiedNotification", "魅族实况通知发送失败，使用普通通知", e)
                showNormalNotification(courseName, location, startTime, minutesBefore)
            }
        } else {
            Log.d("UnifiedNotification", "非魅族设备，使用普通通知")
            showNormalNotification(courseName, location, startTime, minutesBefore)
        }
    }
    
    /**
     * 显示魅族实况通知
     */
    private fun showMeizuLiveNotification(
        courseName: String,
        location: String,
        startTime: String,
        minutesBefore: Int
    ) {
        try {
            // 创建胶囊信息
            val capsuleBundle = Bundle().apply {
                putInt("notification.live.capsuleStatus", 1)
                putInt("notification.live.capsuleType", 3)
                putString("notification.live.capsuleContent", "新提醒")
                
                // 设置胶囊图标
                val drawable = ContextCompat.getDrawable(context, R.drawable.ic_notification)?.mutate()
                if (drawable != null) {
                    drawable.setTint("#BF360C".toColorInt())
                    val icon = Icon.createWithBitmap(drawable.toBitmap())
                    putParcelable("notification.live.capsuleIcon", icon)
                } else {
                    Log.e("UnifiedNotification", "找不到通知图标")
                }
                
                putInt("notification.live.capsuleBgColor", "#FFE082".toColorInt())
                putInt("notification.live.capsuleContentColor", "#BF360C".toColorInt())
            }

            // 创建实况通知配置
            val liveBundle = Bundle().apply {
                putBoolean("is_live", true)
                putInt("notification.live.operation", 0)
                putInt("notification.live.type", 10)
                putBundle("notification.live.capsule", capsuleBundle)
                putInt("notification.live.contentColor", "#FFE082".toColorInt())
            }

            // 创建自定义RemoteViews
            val contentRemoteViews = RemoteViews(context.packageName, R.layout.live_notification_card).apply {
                setTextViewText(R.id.live_title, courseName)
                setTextViewText(R.id.location, location)
            }

            // 创建点击Intent
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 构建通知
            val notification = Notification.Builder(context, LIVE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(courseName)
                .setContentText("${minutesBefore}分钟后开始上课")
                .setContentIntent(pendingIntent)
                .addExtras(liveBundle)
                .setCustomContentView(contentRemoteViews)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d("UnifiedNotification", "魅族实况通知已发送: $courseName")
            
        } catch (e: Exception) {
            Log.e("UnifiedNotification", "发送魅族实况通知失败", e)
            throw e
        }
    }
    
    /**
     * 显示普通通知
     */
    private fun showNormalNotification(
        courseName: String,
        location: String,
        startTime: String,
        minutesBefore: Int
    ) {
        try {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val contentText = if (location.isNotEmpty()) {
                "$courseName 将在${minutesBefore}分钟后开始\n地点: $location"
            } else {
                "$courseName 将在${minutesBefore}分钟后开始"
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("课程提醒")
                .setContentText("$courseName 将在${minutesBefore}分钟后开始")
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    if (startTime.isNotEmpty()) {
                        "$contentText\n时间: $startTime"
                    } else {
                        contentText
                    }
                ))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVibrate(longArrayOf(0, 250, 250, 250))
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d("UnifiedNotification", "普通通知已发送: $courseName")
            
        } catch (e: Exception) {
            Log.e("UnifiedNotification", "发送普通通知失败", e)
        }
    }
    
    /**
     * 发送测试通知（用于验证功能）
     */
    fun sendTestNotification() {
        showCourseNotification(
            courseName = "测试课程",
            location = "测试教室A101",
            startTime = "08:00",
            minutesBefore = 15
        )
    }
    
    /**
     * 取消通知
     */
    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
        Log.d("UnifiedNotification", "通知已取消")
    }
    
    /**
     * 为指定课表启用课前提醒
     * @param timetableId 课表ID
     */
    suspend fun enableRemindersForTimetable(timetableId: Long) {
        val dao = DatabaseProvider.dao()
        
        // 保存当前启用提醒的课表ID
        dao.setPreference(PREF_REMINDER_ENABLED_TIMETABLE, timetableId.toString())
        
        // 取消所有之前的提醒
        cancelAllReminders()
        
        // 为该课表的所有课程设置提醒
        scheduleRemindersForTimetable(timetableId)
    }
    
    /**
     * 禁用课前提醒
     */
    suspend fun disableReminders() {
        val dao = DatabaseProvider.dao()
        
        // 清除启用提醒的课表ID
        dao.setPreference(PREF_REMINDER_ENABLED_TIMETABLE, "")
        
        // 取消所有提醒
        cancelAllReminders()
    }
    
    /**
     * 检查指定课表是否启用了提醒
     */
    fun isReminderEnabledForTimetableSync(timetableId: Long): Boolean {
        return try {
            val dao = DatabaseProvider.dao()
            // 使用runBlocking来同步获取值，避免LiveEdit问题
            kotlinx.coroutines.runBlocking {
                val enabledTimetableId = dao.getPreferenceFlow(PREF_REMINDER_ENABLED_TIMETABLE).first()
                enabledTimetableId == timetableId.toString()
            }
        } catch (e: Exception) {
            Log.e("UnifiedNotification", "检查提醒状态失败", e)
            false
        }
    }
    
    /**
     * 检查指定课表是否启用了提醒（suspend版本）
     */
    suspend fun isReminderEnabledForTimetable(timetableId: Long): Boolean {
        return try {
            val dao = DatabaseProvider.dao()
            val enabledTimetableId = dao.getPreferenceFlow(PREF_REMINDER_ENABLED_TIMETABLE).first()
            enabledTimetableId == timetableId.toString()
        } catch (e: Exception) {
            Log.e("UnifiedNotification", "检查提醒状态失败", e)
            false
        }
    }
    
    /**
     * 获取当前启用提醒的课表ID
     */
    suspend fun getEnabledReminderTimetableId(): Long? {
        val dao = DatabaseProvider.dao()
        val enabledTimetableId = dao.getPreferenceFlow(PREF_REMINDER_ENABLED_TIMETABLE).first()
        return enabledTimetableId?.toLongOrNull()
    }
    
    /**
     * 为指定课表安排提醒
     */
    private suspend fun scheduleRemindersForTimetable(timetableId: Long) {
        val dao = DatabaseProvider.dao()
        
        // 获取课表信息
        val timetable = dao.getTimetableFlow(timetableId).first() ?: return
        
        // 获取课程和课时信息
        val courses = dao.getCoursesFlow(timetableId).first()
        val lessonTimes = dao.getLessonTimesFlow(timetableId).first()
        
        val startDate = try {
            LocalDate.parse(timetable.startDate)
        } catch (e: Exception) {
            LocalDate.now()
        }
        
        // 为未来两周的课程安排提醒
        val currentDate = LocalDate.now()
        val endDate = currentDate.plusWeeks(2)
        
        var date = currentDate
        while (!date.isAfter(endDate)) {
            val weekNumber = getWeekOfSemester(date, startDate)
            val dayOfWeek = date.dayOfWeek.value // 1=Monday, 7=Sunday
            
            // 查找该日期的课程
            val todayCourses = courses.filter { course ->
                course.dayOfWeek == dayOfWeek && course.weeks.contains(weekNumber)
            }
            
            // 为每门课程安排提醒
            todayCourses.forEach { course ->
                course.periods.forEach { period ->
                    val lessonTime = lessonTimes.find { it.period == period }
                    if (lessonTime != null) {
                        scheduleReminderForCourse(course, lessonTime, date)
                    }
                }
            }
            
            date = date.plusDays(1)
        }
    }
    
    /**
     * 为单个课程安排提醒
     */
    private fun scheduleReminderForCourse(course: CourseEntity, lessonTime: LessonTimeEntity, date: LocalDate) {
        try {
            val startTime = LocalTime.parse(lessonTime.startTime, DateTimeFormatter.ofPattern("HH:mm"))
            val courseDateTime = LocalDateTime.of(date, startTime)
            val reminderDateTime = courseDateTime.minusMinutes(REMINDER_MINUTES)
            
            // 只为未来的课程设置提醒
            if (reminderDateTime.isAfter(LocalDateTime.now())) {
                val intent = Intent(context, CourseReminderReceiver::class.java).apply {
                    putExtra("course_name", course.name)
                    putExtra("course_location", course.location)
                    putExtra("course_time", lessonTime.startTime)
                }
                
                val requestCode = generateRequestCode(course.id, date, lessonTime.period)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                val triggerTime = reminderDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * 取消所有提醒
     */
    private fun cancelAllReminders() {
        // 由于无法枚举所有已设置的闹钟，这里采用简单的方式
        // 实际应用中可能需要保存所有已设置的闹钟请求码
        for (i in 0..10000) {
            val intent = Intent(context, CourseReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                i,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }
    
    /**
     * 生成唯一的请求码
     */
    private fun generateRequestCode(courseId: Long, date: LocalDate, period: Int): Int {
        return ("$courseId${date.toEpochDay()}$period").hashCode().and(0x7FFFFFFF)
    }
    
    /**
     * 计算日期在学期中的周数
     */
    private fun getWeekOfSemester(date: LocalDate, startDate: LocalDate): Int {
        val days = java.time.temporal.ChronoUnit.DAYS.between(startDate, date)
        return (days / 7 + 1).toInt()
    }
}

/**
 * 课程提醒广播接收器
 */
class CourseReminderReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val courseName = intent.getStringExtra("course_name") ?: return
        val courseLocation = intent.getStringExtra("course_location") ?: ""
        val courseTime = intent.getStringExtra("course_time") ?: ""
        
        showNotification(context, courseName, courseLocation, courseTime)
    }
    
    private fun showNotification(context: Context, courseName: String, location: String, time: String) {
        // 使用统一通知管理器发送通知
        val unifiedNotificationManager = UnifiedNotificationManager(context)
        unifiedNotificationManager.showCourseNotification(
            courseName = courseName,
            location = location,
            startTime = time,
            minutesBefore = 15
        )
    }
}