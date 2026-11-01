package com.star.schedule

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.settings.data.RoomSettingsRepository
import com.star.schedule.feature.settings.presentation.SettingsRoute
import com.star.schedule.feature.settings.presentation.SettingsViewModel
import com.star.schedule.feature.settings.presentation.SettingsViewModelFactory
import com.star.schedule.notification.UnifiedNotificationManager
import com.star.schedule.platform.notification.NotificationPermissionGate
import com.star.schedule.platform.systembar.installEdgeToEdge

class SettingsActivity : ComponentActivity() {
    private val permissionGate = NotificationPermissionGate(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatabaseProvider.init(this)
        installEdgeToEdge()

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
                    onOpenReminderSettings = {
                        startActivity(Intent(this, ReminderActivity::class.java))
                    },
                    onOpenAiSettings = {
                        startActivity(Intent(this, AiSettingsActivity::class.java))
                    },
                    onRequestNotificationPermission = permissionGate::request,
                )
            }
        }
    }
}
