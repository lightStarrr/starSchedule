package com.star.schedule

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.schedule.data.RoomScheduleRepository
import com.star.schedule.feature.timetable.presentation.CourseSettingsRoute
import com.star.schedule.feature.timetable.presentation.CourseSettingsViewModel
import com.star.schedule.feature.timetable.presentation.CourseSettingsViewModelFactory
import com.star.schedule.platform.systembar.installEdgeToEdge

/**
 * 课程设置：课程增删改还没做；课表里没有节次时间时，页面会给出提示和跳转按钮。
 */
class CourseSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatabaseProvider.init(this)
        installEdgeToEdge()

        val repository = RoomScheduleRepository(DatabaseProvider.dao())
        val viewModel = ViewModelProvider(
            this,
            CourseSettingsViewModelFactory(repository),
        )[CourseSettingsViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                CourseSettingsRoute(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onOpenLessonTimes = {
                        startActivity(Intent(this, LessonTimeSettingsActivity::class.java))
                    },
                )
            }
        }
    }
}
