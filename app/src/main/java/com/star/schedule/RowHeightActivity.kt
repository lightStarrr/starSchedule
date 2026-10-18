package com.star.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.schedule.data.RoomScheduleRepository
import com.star.schedule.feature.settings.data.RoomSettingsRepository
import com.star.schedule.feature.timetable.presentation.RowHeightRoute
import com.star.schedule.feature.timetable.presentation.RowHeightViewModel
import com.star.schedule.feature.timetable.presentation.RowHeightViewModelFactory
import com.star.schedule.notification.UnifiedNotificationManager
import com.star.schedule.platform.systembar.installEdgeToEdge

/**
 * 行高调整：整页课表预览 + 底部浮层滑杆。
 */
class RowHeightActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatabaseProvider.init(this)
        installEdgeToEdge()

        val scheduleRepository = RoomScheduleRepository(DatabaseProvider.dao())
        val settingsRepository = RoomSettingsRepository(
            context = this,
            dao = DatabaseProvider.dao(),
            notificationManager = UnifiedNotificationManager(this),
        )
        val viewModel = ViewModelProvider(
            this,
            RowHeightViewModelFactory(scheduleRepository, settingsRepository),
        )[RowHeightViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                RowHeightRoute(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onApplied = ::finish,
                )
            }
        }
    }
}
