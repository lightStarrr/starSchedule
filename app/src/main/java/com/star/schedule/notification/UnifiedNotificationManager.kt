package com.star.schedule.notification

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.star.schedule.MainActivity
import com.star.schedule.R
import com.star.schedule.Constants
import com.star.schedule.db.CourseEntity
import com.star.schedule.db.DatabaseProvider
import com.star.schedule.db.LessonTimeEntity
import com.star.schedule.db.NotificationManagerProvider
import com.star.schedule.db.ReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class UnifiedNotificationManager(private val context: Context) : NotificationManagerProvider {

    private val notificationManager = context.getSystemService<NotificationManager>()!!
    private val alarmManager = context.getSystemService<AlarmManager>()!!

    companion object {
        const val CHANNEL_ID = "course_reminder"
        const val CHANNEL_NAME = "课程提醒"
        const val LIVE_CHANNEL_ID = "live_notification_channel"
        const val LIVE_CHANNEL_NAME = "实况通知"
        const val NOTIFICATION_ID = 1001

        // 定期更新提醒的请求码
        const val DAILY_UPDATE_REQUEST_CODE = 9999
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()

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

    private fun getFlymeVersion(): Int {
        val display = Build.DISPLAY ?: return -1
        val regex = Regex("Flyme\\s*([0-9]+)")
        val match = regex.find(display)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
    }

    fun isFlymeLiveNotificationEnabled(context: Context): Boolean {
        try {
        } catch (_: java.lang.Exception) {
            Log.d("LiveUtil", "isNotificationAllowed  error")
        }
        if (context.checkSelfPermission("flyme.permission.READ_NOTIFICATION_LIVE_STATE") != PackageManager.PERMISSION_GRANTED) {
            Log.e("LiveUtil", "Missing permission: flyme.permission.READ_NOTIFICATION_LIVE_STATE")
            return false
        }
        val call: Bundle? = context.contentResolver.call(
            "content://com.android.systemui.notification.provider".toUri(),
            "isNotificationLiveEnabled",
            null as String?,
            null as Bundle?
        )
        Log.d("LiveUtil", "result=" + call + ", context package=" + context.getPackageName())
        if (call != null) {
            val z = call.getBoolean("result", false)
            Log.d("LiveUtil", "result1 = $z")
            return z
        }
        return false
    }

    fun showCourseNotificationImmediate(
        courseName: String = "课程提醒",
        location: String = "",
        startTime: String = "",
        minutesBefore: Int = 15
    ) {
        when (Build.MANUFACTURER) {
            "meizu" -> {
                if (getFlymeVersion() >= 11 && isFlymeLiveNotificationEnabled(context)) {
                    showMeizuLiveNotification(courseName, location, startTime, minutesBefore)
                } else {
                    showNormalNotification(courseName, location, startTime, minutesBefore)
                }
            }

            else -> {
                showNormalNotification(courseName, location, startTime, minutesBefore)
            }
        }
    }

    fun cancelCourseNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun showCourseNotification(
        courseName: String = "课程提醒",
        location: String = "",
        startTime: String = "",
        minutesBefore: Int = 15
    ) {
        showCourseNotificationImmediate(courseName, location, startTime, minutesBefore)

        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            val courseStart = LocalTime.parse(startTime, formatter)
            val triggerTime = courseStart.atDate(LocalDate.now())
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val updateIntent = Intent(context, CourseNotificationUpdateReceiver::class.java).apply {
                putExtra("course_name", courseName)
                putExtra("course_location", location)
                putExtra("course_time", startTime)
            }

            val updatePendingIntent = PendingIntent.getBroadcast(
                context,
                courseName.hashCode(),
                updateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                updatePendingIntent
            )

            Log.d("UnifiedNotification", "已设置更新通知闹钟，时间: $startTime")

            // 🔹 安排“取消通知”闹钟（课程开始后1分钟触发）
            val cancelIntent = Intent(context, CourseNotificationCancelReceiver::class.java)
            val cancelPendingIntent = PendingIntent.getBroadcast(
                context,
                (courseName + "_cancel").hashCode(),
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime + 60_000,
                cancelPendingIntent
            )

            Log.d("UnifiedNotification", "已设置取消通知闹钟，延迟1分钟触发")

        } catch (e: Exception) {
            Log.e("UnifiedNotification", "设置更新/取消闹钟失败", e)
        }
    }



    private fun showMeizuLiveNotification(
        courseName: String,
        location: String,
        startTime: String,
        minutesBefore: Int
    ) {
        // 获取数据库实例
        val dao = DatabaseProvider.dao()
        
        // 获取自定义背景颜色，默认为原来的颜色
        val capsuleBgColor = runBlocking {
            val colorPref = dao.getPreferenceFlow(Constants.PREF_LIVE_CAPSULE_BG_COLOR).first()
            colorPref ?: "#FFE082"
        }
        fun autoContentColorFor(background: Color): Color {
            return if (background.luminance() > 0.7f) Color.Black else Color.White
        }
        val textColor = autoContentColorFor(Color(capsuleBgColor.toColorInt()))
        
        val capsuleBundle = Bundle().apply {
            putInt("notification.live.capsuleStatus", 1)
            putInt("notification.live.capsuleType", 3)
            putString("notification.live.capsuleContent", courseName)

            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_notification)?.mutate()
            if (drawable != null) {
                drawable.setTint(textColor.toArgb())
                val icon = Icon.createWithBitmap(drawable.toBitmap())
                putParcelable("notification.live.capsuleIcon", icon)
            }
            putInt("notification.live.capsuleBgColor", capsuleBgColor.toColorInt())
            putInt("notification.live.capsuleContentColor", textColor.toArgb())
        }

        val liveBundle = Bundle().apply {
            putBoolean("is_live", true)
            putInt("notification.live.operation", 0)
            putInt("notification.live.type", 10)
            putBundle("notification.live.capsule", capsuleBundle)
            putInt("notification.live.contentColor", textColor.toArgb())
        }

        val layout = if(minutesBefore >0) RemoteViews(context.packageName, R.layout.live_notification_card)
        else RemoteViews(context.packageName, R.layout.live_notification_card_ok)

        val contentRemoteViews =
            layout.apply {
                setTextViewText(R.id.live_title, courseName)
                setTextViewText(R.id.location, location)
                setTextViewText(R.id.live_time, startTime)
                setImageViewResource(R.id.live_icon, R.drawable.star)
            }

        val notification = Notification.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(courseName)
            .setContentText("${startTime}开始上课")
            .addExtras(liveBundle)
            .setCustomContentView(contentRemoteViews)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }


    private fun showNormalNotification(
        courseName: String,
        location: String,
        startTime: String,
        minutesBefore: Int
    ) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (minutesBefore > 0) {
            if (location.isNotEmpty()) {
                "$courseName 即将上课\n地点: $location"
            } else {
                "$courseName 即将上课"
            }
        } else {
            if (location.isNotEmpty()) {
                "$courseName 已上课\n地点: $location"
            } else {
                "$courseName 已上课"
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("课程提醒")
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (startTime.isNotEmpty()) {
                        "$contentText\n时间: $startTime"
                    } else {
                        contentText
                    }
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override suspend fun enableRemindersForTimetable(timetableId: Long) {
        val dao = DatabaseProvider.dao()
        dao.setPreference(Constants.PREF_REMINDER_ENABLED_TIMETABLE, timetableId.toString())

        cancelAllReminders()
        scheduleRemindersForTimetable(timetableId)
    }

    suspend fun disableReminders() {
        val dao = DatabaseProvider.dao()
        dao.setPreference(Constants.PREF_REMINDER_ENABLED_TIMETABLE, "")
        cancelAllReminders()
    }

    private suspend fun scheduleRemindersForTimetable(timetableId: Long) {
        val dao = DatabaseProvider.dao()
        val timetable = dao.getTimetableFlow(timetableId).first() ?: return
        val courses = dao.getCoursesFlow(timetableId).first()
        val lessonTimes = dao.getLessonTimesFlow(timetableId).first()

        // 检查是否只在连续课程的第一节课前发送通知
        val notifyOnlyForFirstContinuousClassPref = dao.getPreferenceFlow(Constants.PREF_NOTIFY_ONLY_FOR_FIRST_CONTINUOUS_CLASS).first()
        val notifyOnlyForFirstContinuousClass = notifyOnlyForFirstContinuousClassPref == "true"

        val startDate =
            runCatching { LocalDate.parse(timetable.startDate) }.getOrElse { LocalDate.now() }
        val currentDate = LocalDate.now()
        val endDate = currentDate.plusWeeks(2)

        var date = currentDate
        while (!date.isAfter(endDate)) {
            val weekNumber = getWeekOfSemester(date, startDate)
            val dayOfWeek = date.dayOfWeek.value
            val todayCourses =
                courses.filter { it.dayOfWeek == dayOfWeek && it.weeks.contains(weekNumber) }

            todayCourses.forEach { course ->
                course.periods.forEach { period ->
                    // 如果启用了"只在连续课程的第一节课前发送通知"，则需要检查当前课程是否是连续课程的第一节
                    if (notifyOnlyForFirstContinuousClass) {
                        // 检查是否有与当前课程连续的前一节课
                        val previousContinuousClass = todayCourses.find { c ->
                            c.periods.contains(period - 1)
                        }
                        
                        // 如果有连续的前一节课，并且是同一门课（课程名称和地点都相同），则不为当前课程设置提醒
                        if (previousContinuousClass != null && 
                            previousContinuousClass.name == course.name && 
                            previousContinuousClass.location == course.location) {
                            Log.d("UnifiedNotification", "跳过同一门连续课程的后续课程提醒: ${course.name}, 节数: $period")
                            return@forEach
                        }
                    }
                    
                    val lessonTime = lessonTimes.find { it.period == period }
                    if (lessonTime != null) {
                        scheduleReminderForCourse(course, lessonTime, date, timetable.reminderTime)
                    }
                }
            }
            date = date.plusDays(1)
        }
    }

    private fun scheduleReminderForCourse(
        course: CourseEntity,
        lessonTime: LessonTimeEntity,
        date: LocalDate,
        reminderTime: Int = 15
    ) {
        val startTime = LocalTime.parse(lessonTime.startTime, DateTimeFormatter.ofPattern("HH:mm"))
        val courseDateTime = LocalDateTime.of(date, startTime)
        val reminderDateTime = courseDateTime.minusMinutes(reminderTime.toLong())

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

            val triggerTime =
                reminderDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            try {

                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )

                // ⚡️ 存储提醒记录
                CoroutineScope(Dispatchers.IO).launch {
                    DatabaseProvider.dao().insertReminder(
                        ReminderEntity(requestCode, course.id, date.toString(), lessonTime.period)
                    )
                }
            } catch (_: SecurityException) {
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        context,
                        "无法设置精确闹钟，请检查系统设置",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }


    // ⚡️ 精确取消
    private suspend fun cancelAllReminders() {
        val reminders = DatabaseProvider.dao().getAllReminders()
        reminders.forEach { reminder ->
            val intent = Intent(context, CourseReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminder.requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        DatabaseProvider.dao().deleteAllReminders()
        Log.d("UnifiedNotification", "已取消 ${reminders.size} 个提醒")
    }

    private fun generateRequestCode(courseId: Long, date: LocalDate, period: Int): Int =
        ("$courseId${date.toEpochDay()}$period").hashCode().and(0x7FFFFFFF)

    private fun getWeekOfSemester(date: LocalDate, startDate: LocalDate): Int {
        val days = java.time.temporal.ChronoUnit.DAYS.between(startDate, date)
        return (days / 7 + 1).toInt()
    }

    fun sendTestNotification() {
        val startTime = LocalTime.now().plusMinutes(15)
            .format(DateTimeFormatter.ofPattern("HH:mm"))

        showCourseNotification(
            courseName = "测试课程",
            location = "测试教室A101",
            startTime = startTime,
            minutesBefore = 1
        )

        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(
                context,
                "测试通知已设置，更新时间为1分钟",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun scheduleTestReminder() {
        val startTime = LocalTime.now().plusMinutes(15)
            .format(DateTimeFormatter.ofPattern("HH:mm"))

        val intent = Intent(context, CourseReminderReceiver::class.java).apply {
            putExtra("course_name", "测试课程")
            putExtra("course_location", "测试教室A101")
            putExtra("course_time", startTime)
        }

        val requestCode = "test_reminder_${System.currentTimeMillis()}".hashCode().and(0x7FFFFFFF)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + 10 * 1000L

        try {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )

                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        context,
                        "测试提醒已设置，将在10秒后推送通知",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                // 如果无法设置精确闹钟，引导用户开启权限
                val settingsIntent =
                    Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                context.startActivity(settingsIntent)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(
                        context,
                        "请允许应用使用精确闹钟，然后重试",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (_: SecurityException) {
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(
                    context,
                    "无法设置提醒，请检查系统设置",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // 保留 isReminderEnabledForTimetableSync
    fun isReminderEnabledForTimetableSync(timetableId: Long): Boolean {
        return try {
            val dao = DatabaseProvider.dao()
            runBlocking {
                val enabledTimetableId =
                    dao.getPreferenceFlow(Constants.PREF_REMINDER_ENABLED_TIMETABLE).first()
                enabledTimetableId == timetableId.toString()
            }
        } catch (e: Exception) {
            Log.e("UnifiedNotification", "检查提醒状态失败", e)
            false
        }
    }

    /**
     * 在应用启动时检查并更新提醒
     * 这个方法应该在 MainActivity.onCreate() 中调用
     */
    suspend fun initializeOnAppStart() {
        try {
            Log.d("UnifiedNotification", "应用启动，初始化提醒系统")

            // 检查是否有启用的课表提醒
            val dao = DatabaseProvider.dao()
            val enabledTimetableId = dao.getPreferenceFlow(Constants.PREF_REMINDER_ENABLED_TIMETABLE).first()

            enabledTimetableId?.let { idString ->
                if (idString.isNotEmpty()) {
                    val timetableId = idString.toLongOrNull()
                    if (timetableId != null) {
                        Log.d("UnifiedNotification", "检测到启用的课表提醒: $timetableId")

                        // 检查并更新过期的提醒
                        cleanupExpiredReminders()

                        // 重新设置提醒（以防系统清理或其他原因导致丢失）
                        scheduleRemindersForTimetable(timetableId)

                        // 设置定期更新机制
                        scheduleDailyUpdate()

                        Log.d("UnifiedNotification", "提醒系统初始化完成")
                    }
                }
            } ?: Log.d("UnifiedNotification", "未检测到启用的课表提醒")
        } catch (e: Exception) {
            Log.e("UnifiedNotification", "初始化提醒系统失败", e)
        }
    }

    /**
     * 设备重启后恢复提醒
     */
    suspend fun restoreRemindersAfterBoot() {
        try {
            Log.d("UnifiedNotification", "设备重启，恢复提醒设置")

            // 确保数据库已初始化
            if (!DatabaseProvider.isInitialized()) {
                DatabaseProvider.init(context)
            }

            val dao = DatabaseProvider.dao()
            val enabledTimetableId = dao.getPreferenceFlow(Constants.PREF_REMINDER_ENABLED_TIMETABLE).first()

            enabledTimetableId?.let { idString ->
                if (idString.isNotEmpty()) {
                    val timetableId = idString.toLongOrNull()
                    if (timetableId != null) {
                        Log.d("UnifiedNotification", "重启后恢复课表提醒: $timetableId")

                        // 清理数据库中的记录（因为重启后所有闹钟都会被清除）
                        dao.deleteAllReminders()

                        // 重新设置所有提醒
                        scheduleRemindersForTimetable(timetableId)

                        // 重新设置定期更新
                        scheduleDailyUpdate()

                        Log.d("UnifiedNotification", "重启后提醒恢复完成")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UnifiedNotification", "重启后恢复提醒失败", e)
        }
    }

    /**
     * 定期更新即将到期的提醒
     * 清理过期提醒，添加新的提醒
     */
    suspend fun updateUpcomingReminders() {
        try {
            Log.d("UnifiedNotification", "开始定期更新提醒")

            val dao = DatabaseProvider.dao()
            val enabledTimetableId = dao.getPreferenceFlow(Constants.PREF_REMINDER_ENABLED_TIMETABLE).first()

            enabledTimetableId?.let { idString ->
                if (idString.isNotEmpty()) {
                    val timetableId = idString.toLongOrNull()
                    if (timetableId != null) {
                        // 清理过期的提醒
                        cleanupExpiredReminders()

                        // 重新设置未来两周的提醒
                        scheduleRemindersForTimetable(timetableId)

                        Log.d("UnifiedNotification", "定期更新提醒完成")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UnifiedNotification", "定期更新提醒失败", e)
        }
    }

    /**
     * 设置定期更新机制
     * 每天凌晨2点执行一次更新
     */
    private fun scheduleDailyUpdate() {
        try {
            val intent = Intent(context, DailyUpdateReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                DAILY_UPDATE_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 计算下一次凌晨2点的时间
            val now = LocalDateTime.now()
            val next2AM = if (now.hour < 2) {
                now.toLocalDate().atTime(2, 0)
            } else {
                now.toLocalDate().plusDays(1).atTime(2, 0)
            }

            val triggerTime = next2AM.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            if (alarmManager.canScheduleExactAlarms()) {
                // 先取消已存在的闹钟，防止重复设置
                alarmManager.cancel(pendingIntent)

                // 设置重复的闹钟，每24小时执行一次
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
                )

                Log.d("UnifiedNotification", "已设置定期更新，下次执行: $next2AM")
            } else {
                Log.w("UnifiedNotification", "无法设置精确闹钟，跳过定期更新设置")
            }
        } catch (e: Exception) {
            Log.e("UnifiedNotification", "设置定期更新失败", e)
        }
    }

    /**
     * 清理过期的提醒
     */
    private suspend fun cleanupExpiredReminders() {
        try {
            val dao = DatabaseProvider.dao()
            val allReminders = dao.getAllReminders()
            val currentDate = LocalDate.now()

            val expiredReminders = allReminders.filter { reminder ->
                val reminderDate = LocalDate.parse(reminder.date)
                reminderDate.isBefore(currentDate)
            }

            // 取消过期的闹钟
            expiredReminders.forEach { reminder ->
                val intent = Intent(context, CourseReminderReceiver::class.java)
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    reminder.requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }

                // 从数据库中删除
                dao.deleteReminder(reminder.requestCode)
            }

            if (expiredReminders.isNotEmpty()) {
                Log.d("UnifiedNotification", "已清理 ${expiredReminders.size} 个过期提醒")
            }
        } catch (e: Exception) {
            Log.e("UnifiedNotification", "清理过期提醒失败", e)
        }
    }

}

class CourseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val courseName = intent.getStringExtra("course_name") ?: return
        val courseLocation = intent.getStringExtra("course_location") ?: ""
        val courseTime = intent.getStringExtra("course_time") ?: ""

        // 确保数据库已初始化
        if (!DatabaseProvider.isInitialized()) {
            DatabaseProvider.init(context)
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = DatabaseProvider.dao()
                
                // 从数据库获取当前启用的课表ID（使用正确的偏好键）
                val enabledTimetableId = dao.getPreferenceFlow(Constants.PREF_REMINDER_ENABLED_TIMETABLE).first()
                
                if (enabledTimetableId.isNullOrEmpty()) {
                    Log.w("UnifiedNotification", "未找到启用的课表提醒设置")
                    // 如果没有启用的课表，使用默认值
                    withContext(Dispatchers.Main) {
                        UnifiedNotificationManager(context).showCourseNotification(
                            courseName = courseName,
                            location = courseLocation,
                            startTime = courseTime,
                            minutesBefore = 15
                        )
                    }
                    return@launch
                }

                val timetableId = enabledTimetableId.toLongOrNull()
                if (timetableId == null) {
                    Log.e("UnifiedNotification", "启用的课表ID格式错误: $enabledTimetableId")
                    // 如果ID格式错误，使用默认值
                    withContext(Dispatchers.Main) {
                        UnifiedNotificationManager(context).showCourseNotification(
                            courseName = courseName,
                            location = courseLocation,
                            startTime = courseTime,
                            minutesBefore = 15
                        )
                    }
                    return@launch
                }

                // 获取课前提醒时间设置
                val timetable = dao.getTimetableFlow(timetableId).first()
                val reminderTime = if (timetable != null) {
                    Log.d("UnifiedNotification", "课前提醒时间设置: ${timetable.reminderTime} 分钟")
                    timetable.reminderTime
                } else {
                    Log.w("UnifiedNotification", "未找到课表ID $timetableId 的详细信息，使用默认提醒时间")
                    15 // 默认15分钟
                }

                UnifiedNotificationManager(context).showCourseNotification(
                    courseName = courseName,
                    location = courseLocation,
                    startTime = courseTime,
                    minutesBefore = reminderTime
                )
            } catch (e: Exception) {
                Log.e("UnifiedNotification", "处理课程提醒时出错", e)
                withContext(Dispatchers.Main) {
                    UnifiedNotificationManager(context).showCourseNotification(
                        courseName = courseName,
                        location = courseLocation,
                        startTime = courseTime,
                        minutesBefore = 15
                    )
                }
            }
        }
    }
}

/**
 * 系统启动广播接收器
 * 监听设备重启，重新设置所有闹钟提醒
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == Intent.ACTION_PACKAGE_REPLACED
        ) {

            Log.d("BootCompleted", "收到系统启动广播: ${intent.action}")

            // 保证异步任务不会被系统中断
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val notificationManager = UnifiedNotificationManager(context)
                    notificationManager.restoreRemindersAfterBoot()
                } catch (e: Exception) {
                    Log.e("BootCompleted", "重新设置提醒失败", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}


/**
 * 定期更新提醒的广播接收器
 * 每天检查并更新即将到期的提醒
 */
class DailyUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("DailyUpdate", "执行定期提醒更新")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val notificationManager = UnifiedNotificationManager(context)
                notificationManager.updateUpcomingReminders()
            } catch (e: Exception) {
                Log.e("DailyUpdate", "定期更新提醒失败", e)
            }
        }
    }
}

/**
 * 课程开始时更新通知内容（从“即将上课”→“已上课”）
 */
class CourseNotificationUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val courseName = intent.getStringExtra("course_name") ?: return
        val location = intent.getStringExtra("course_location") ?: ""
        val startTime = intent.getStringExtra("course_time") ?: ""

        Log.d("CourseUpdateReceiver", "收到更新通知广播: $courseName")

        UnifiedNotificationManager(context).showCourseNotificationImmediate(
            courseName, location, startTime, minutesBefore = 0
        )
    }
}

/**
 * 课程开始1分钟后取消通知
 */
class CourseNotificationCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("CourseCancelReceiver", "收到取消通知广播")
        UnifiedNotificationManager(context).cancelCourseNotification()
    }
}