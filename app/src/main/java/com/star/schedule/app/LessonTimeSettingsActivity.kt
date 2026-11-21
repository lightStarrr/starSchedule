package com.star.schedule.app

import android.content.Context
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
import com.star.schedule.platform.notification.UnifiedNotificationManager
import com.star.schedule.platform.notification.ReminderRefresher
import com.star.schedule.platform.systembar.installEdgeToEdge

/**
 * 节次时间：列出当前课表的每一节，可增删改；顶栏右侧进模板页。
 */
class LessonTimeSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 页面作用的对象是「打开时指定的课表」，不是已应用的那张；没带 id 就没有目标。
        val timetableId = intent.getLongExtra(EXTRA_TIMETABLE_ID, -1L).takeIf { it > 0 }
        if (timetableId == null) {
            finish()
            return
        }

        DatabaseProvider.init(this)
        installEdgeToEdge()

        val db = DatabaseProvider.db
        val repository = RoomScheduleRepository(
            database = db,
            reminderRefresher = ReminderRefresher(db.preferenceDao(), UnifiedNotificationManager(applicationContext)),
        )
        val viewModel = ViewModelProvider(
            this,
            LessonTimeSettingsViewModelFactory(repository, timetableId),
        )[LessonTimeSettingsViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                LessonTimeSettingsRoute(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onOpenTemplates = {
                        startActivity(LessonTimeTemplateActivity.intent(this, timetableId))
                    },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TIMETABLE_ID = "extra_timetable_id"

        fun intent(context: Context, timetableId: Long): Intent =
            Intent(context, LessonTimeSettingsActivity::class.java)
                .putExtra(EXTRA_TIMETABLE_ID, timetableId)
    }
}
