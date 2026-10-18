package com.star.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.settings.data.RoomSettingsRepository
import com.star.schedule.feature.settings.presentation.ReminderSettingsRoute
import com.star.schedule.feature.settings.presentation.ReminderSettingsViewModel
import com.star.schedule.feature.settings.presentation.ReminderSettingsViewModelFactory
import com.star.schedule.notification.UnifiedNotificationManager
import com.star.schedule.platform.notification.NotificationPermissionGate
import com.star.schedule.platform.systembar.installEdgeToEdge

/**
 * 课前提醒的独立设置页：顶部用仿 AOSP 的主开关条控制开关，下面是与提醒相关的选项。
 * 与 SettingsActivity 一样是独立 Activity，返回时直接 finish 回到设置页。
 */
class ReminderActivity : ComponentActivity() {
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
        val viewModel = ViewModelProvider(
            this,
            ReminderSettingsViewModelFactory(settingsRepository),
        )[ReminderSettingsViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                ReminderSettingsRoute(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onRequestNotificationPermission = permissionGate::request,
                )
            }
        }
    }
}
