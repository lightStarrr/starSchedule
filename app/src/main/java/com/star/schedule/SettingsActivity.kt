package com.star.schedule

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.settings.data.RoomSettingsRepository
import com.star.schedule.feature.settings.presentation.SettingsRoute
import com.star.schedule.feature.settings.presentation.SettingsViewModel
import com.star.schedule.feature.settings.presentation.SettingsViewModelFactory
import com.star.schedule.notification.UnifiedNotificationManager
import com.star.schedule.platform.systembar.installAdaptiveStatusBarAppearance
import androidx.core.net.toUri

class SettingsActivity : ComponentActivity() {
    private var pendingNotificationAction: (() -> Unit)? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingNotificationAction
        pendingNotificationAction = null
        if (granted && action != null) {
            requestNotificationPermissionIfNeeded(action)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatabaseProvider.init(this)
        enableEdgeToEdge()
        installAdaptiveStatusBarAppearance()

        val settingsRepository = RoomSettingsRepository(
            context = this,
            dao = DatabaseProvider.dao(),
            notificationManager = UnifiedNotificationManager(this),
        )
        val settingsViewModel = ViewModelProvider(
            this,
            SettingsViewModelFactory(settingsRepository),
        )[SettingsViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                SettingsRoute(
                    viewModel = settingsViewModel,
                    onBack = ::finish,
                    onRequestNotificationPermission = ::requestNotificationPermissionIfNeeded,
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingNotificationAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        val alarmManager = getSystemService(AlarmManager::class.java)
        if (alarmManager?.canScheduleExactAlarms() != true) {
            startActivity(
                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = "package:$packageName".toUri()
                },
            )
            Toast.makeText(
                this,
                getString(R.string.exact_alarm_permission_required),
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        if (Build.VERSION.SDK_INT >= 36 &&
            !Build.MANUFACTURER.equals("meizu", ignoreCase = true) &&
            getSystemService(NotificationManager::class.java)
                ?.canPostPromotedNotifications() == false
        ) {
            Toast.makeText(
                this,
                getString(R.string.live_notification_not_enabled),
                Toast.LENGTH_LONG,
            ).show()
        }

        action()
    }
}
