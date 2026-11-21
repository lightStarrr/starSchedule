package com.star.schedule

import android.content.Context
import android.content.Intent
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

        // 「应用模板」的目标是打开时指定的课表，不是已应用的那张；没带 id 就没有目标。
        val timetableId = intent.getLongExtra(EXTRA_TIMETABLE_ID, -1L).takeIf { it > 0 }
        if (timetableId == null) {
            finish()
            return
        }

        DatabaseProvider.init(this)
        installEdgeToEdge()

        val db = DatabaseProvider.db
        // 应用模板会重排指定课表的节次，落库后要让提醒跟着重排。
        val repository = RoomScheduleRepository(
            database = db,
            reminderRefresher = ReminderRefresher(db.preferenceDao(), UnifiedNotificationManager(this)),
        )
        val viewModel = ViewModelProvider(
            this,
            LessonTimeTemplatesViewModelFactory(repository, timetableId),
        )[LessonTimeTemplatesViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                LessonTimeTemplatesRoute(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onOpenTemplate = { row ->
                        startActivity(LessonTimeTemplateEditActivity.intent(this, row.id))
                    },
                    onCreateTemplate = {
                        startActivity(LessonTimeTemplateEditActivity.newTemplateIntent(this))
                    },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TIMETABLE_ID = "extra_timetable_id"

        fun intent(context: Context, timetableId: Long): Intent =
            Intent(context, LessonTimeTemplateActivity::class.java)
                .putExtra(EXTRA_TIMETABLE_ID, timetableId)
    }
}
