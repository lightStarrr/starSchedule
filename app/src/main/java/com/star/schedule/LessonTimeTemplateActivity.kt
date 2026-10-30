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
import com.star.schedule.notification.UnifiedNotificationManager
import com.star.schedule.platform.notification.ReminderRefresher
import com.star.schedule.platform.systembar.installEdgeToEdge

/**
 * 节次时间模板：全局时间方案列表，左滑删除，删完可以撤销。
 */
class LessonTimeTemplateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatabaseProvider.init(this)
        installEdgeToEdge()

        val dao = DatabaseProvider.dao()
        // 应用模板会重排当前课表的节次，落库后要让提醒跟着重排。
        val repository = RoomScheduleRepository(
            dao = dao,
            reminderRefresher = ReminderRefresher(dao, UnifiedNotificationManager(this)),
        )
        val viewModel = ViewModelProvider(
            this,
            LessonTimeTemplatesViewModelFactory(repository),
        )[LessonTimeTemplatesViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                LessonTimeTemplatesRoute(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onOpenTemplate = { row ->
                        startActivity(LessonTimeTemplateEditActivity.intent(this, row.id))
                    },
                )
            }
        }
    }
}
