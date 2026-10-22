package com.star.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.timetable.presentation.LessonTimeSettingsRoute
import com.star.schedule.platform.systembar.installEdgeToEdge

/**
 * 节次时间：页面还没有内容，先只把入口和骨架放出来。
 */
class LessonTimeSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installEdgeToEdge()

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                LessonTimeSettingsRoute(onBack = ::finish)
            }
        }
    }
}
