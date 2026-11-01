package com.star.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.settings.presentation.AboutRoute
import com.star.schedule.platform.systembar.installEdgeToEdge

class AboutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installEdgeToEdge()

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                AboutRoute(onBack = ::finish)
            }
        }
    }
}
