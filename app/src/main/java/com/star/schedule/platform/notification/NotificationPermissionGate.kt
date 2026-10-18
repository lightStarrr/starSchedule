package com.star.schedule.platform.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.star.schedule.R

/**
 * 执行依赖通知能力的前置条件检查：通知权限、精确闹钟权限、实况通知开关。
 *
 * 只负责「补齐条件后再执行 [request] 传入的动作」，权限被拒或条件不满足时只提示不执行。
 * 需要注册 ActivityResultLauncher，因此必须在 Activity.onCreate 之前完成实例化（作为字段持有）。
 */
class NotificationPermissionGate(private val activity: ComponentActivity) {

    private var pendingAction: (() -> Unit)? = null

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingAction
        pendingAction = null
        if (granted && action != null) {
            request(action)
        }
    }

    fun request(action: () -> Unit) {
        if (!hasNotificationPermission()) {
            pendingAction = action
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        val alarmManager = activity.getSystemService(AlarmManager::class.java)
        if (alarmManager?.canScheduleExactAlarms() != true) {
            activity.startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = "package:${activity.packageName}".toUri()
                },
            )
            Toast.makeText(
                activity,
                R.string.exact_alarm_permission_required,
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        if (Build.VERSION.SDK_INT >= 36 &&
            !Build.MANUFACTURER.equals("meizu", ignoreCase = true) &&
            activity.getSystemService(NotificationManager::class.java)
                ?.canPostPromotedNotifications() == false
        ) {
            Toast.makeText(
                activity,
                R.string.live_notification_not_enabled,
                Toast.LENGTH_LONG,
            ).show()
        }

        action()
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
}
