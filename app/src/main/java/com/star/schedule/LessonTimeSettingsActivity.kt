package com.star.schedule

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.schedule.data.RoomScheduleRepository
import com.star.schedule.feature.timetable.presentation.LessonTimeSettingsRoute
import com.star.schedule.feature.timetable.presentation.LessonTimeSettingsViewModel
import com.star.schedule.feature.timetable.presentation.LessonTimeSettingsViewModelFactory
import com.star.schedule.platform.systembar.installEdgeToEdge

/**
 * 节次时间：列出当前课表的每一节，可增删改；顶栏右侧进模板页。
 */
class LessonTimeSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatabaseProvider.init(this)
        installEdgeToEdge()

        val repository = RoomScheduleRepository(DatabaseProvider.dao())
        val viewModel = ViewModelProvider(
            this,
            LessonTimeSettingsViewModelFactory(repository),
        )[LessonTimeSettingsViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                LessonTimeSettingsRoute(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onOpenTemplates = {
                        startActivity(Intent(this, LessonTimeTemplateActivity::class.java))
                    },
                )
            }
        }
    }
}
