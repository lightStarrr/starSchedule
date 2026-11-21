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
import com.star.schedule.feature.settings.data.RoomSettingsRepository
import com.star.schedule.feature.timetable.presentation.RowHeightRoute
import com.star.schedule.feature.timetable.presentation.RowHeightViewModel
import com.star.schedule.feature.timetable.presentation.RowHeightViewModelFactory
import com.star.schedule.notification.UnifiedNotificationManager
import com.star.schedule.platform.systembar.installEdgeToEdge

/**
 * 行高调整：整页课表预览 + 底部浮层滑杆。
 */
class RowHeightActivity : ComponentActivity() {
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

        val scheduleRepository = RoomScheduleRepository(DatabaseProvider.db)
        val settingsRepository = RoomSettingsRepository(
            context = this,
            database = DatabaseProvider.db,
            notificationManager = UnifiedNotificationManager(this),
        )
        val viewModel = ViewModelProvider(
            this,
            RowHeightViewModelFactory(scheduleRepository, settingsRepository, timetableId),
        )[RowHeightViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                RowHeightRoute(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onApplied = ::finish,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TIMETABLE_ID = "extra_timetable_id"

        fun intent(context: Context, timetableId: Long): Intent =
            Intent(context, RowHeightActivity::class.java)
                .putExtra(EXTRA_TIMETABLE_ID, timetableId)
    }
}
