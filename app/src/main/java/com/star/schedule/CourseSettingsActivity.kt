package com.star.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.timetable.presentation.CourseSettingsRoute
import com.star.schedule.platform.systembar.installEdgeToEdge

/**
 * 课程设置：目前只有占位说明，课程增删改还没实现。
 */
class CourseSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installEdgeToEdge()

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                CourseSettingsRoute(onBack = ::finish)
            }
        }
    }
}
