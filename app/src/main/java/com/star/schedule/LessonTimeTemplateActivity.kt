package com.star.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.timetable.presentation.LessonTimeTemplateRoute
import com.star.schedule.platform.systembar.installEdgeToEdge

/**
 * 课程时间模板：页面还没有内容，先只把入口和骨架放出来。
 */
class LessonTimeTemplateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installEdgeToEdge()

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                LessonTimeTemplateRoute(onBack = ::finish)
            }
        }
    }
}
