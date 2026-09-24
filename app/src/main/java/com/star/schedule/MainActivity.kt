package com.star.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.star.schedule.app.StarScheduleApp
import com.star.schedule.platform.systembar.installAdaptiveStatusBarAppearance

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        installAdaptiveStatusBarAppearance()
        setContent {
            StarScheduleApp()
        }
    }
}
