package com.star.schedule

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.settings.data.RoomSettingsRepository
import com.star.schedule.feature.timetable.presentation.TimetableSettingsRoute
import com.star.schedule.feature.timetable.presentation.TimetableSettingsViewModel
import com.star.schedule.feature.timetable.presentation.TimetableSettingsViewModelFactory
import com.star.schedule.notification.UnifiedNotificationManager
import com.star.schedule.platform.systembar.installEdgeToEdge

class TimetableSettingsActivity : ComponentActivity() {
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
            TimetableSettingsViewModelFactory(settingsRepository),
        )[TimetableSettingsViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                TimetableSettingsRoute(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onOpenRowHeight = {
                        startActivity(Intent(this, RowHeightActivity::class.java))
                    },
                    onCreateTimetable = {
                        startActivity(Intent(this, NewTimetableActivity::class.java))
                    },
                )
            }
        }
    }
}
