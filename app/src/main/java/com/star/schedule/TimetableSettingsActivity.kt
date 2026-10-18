package com.star.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.timetable.presentation.TimetableSettingsScreen
import com.star.schedule.platform.systembar.installAdaptiveStatusBarAppearance

class TimetableSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatabaseProvider.init(this)
        enableEdgeToEdge()
        installAdaptiveStatusBarAppearance()

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                TimetableSettingsScreen(onBack = ::finish)
            }
        }
    }
}
