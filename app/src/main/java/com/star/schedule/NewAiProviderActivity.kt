package com.star.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.settings.presentation.NewAiProviderRoute
import com.star.schedule.platform.systembar.installEdgeToEdge

class NewAiProviderActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installEdgeToEdge()

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                NewAiProviderRoute(onBack = ::finish)
            }
        }
    }
}
