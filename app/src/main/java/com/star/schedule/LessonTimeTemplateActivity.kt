package com.star.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.schedule.data.RoomScheduleRepository
import com.star.schedule.feature.timetable.presentation.LessonTimeTemplatesRoute
import com.star.schedule.feature.timetable.presentation.LessonTimeTemplatesViewModel
import com.star.schedule.feature.timetable.presentation.LessonTimeTemplatesViewModelFactory
import com.star.schedule.platform.systembar.installEdgeToEdge

/**
 * 节次时间模板：全局时间方案列表，左滑删除，删完可以撤销。
 */
class LessonTimeTemplateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatabaseProvider.init(this)
        installEdgeToEdge()

        val repository = RoomScheduleRepository(DatabaseProvider.dao())
        val viewModel = ViewModelProvider(
            this,
            LessonTimeTemplatesViewModelFactory(repository),
        )[LessonTimeTemplatesViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                LessonTimeTemplatesRoute(
                    viewModel = viewModel,
                    onBack = ::finish,
                )
            }
        }
    }
}
