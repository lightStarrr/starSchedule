package com.star.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.settings.data.RoomSettingsRepository
import com.star.schedule.feature.timetable.data.RoomTimetableRepository
import com.star.schedule.feature.timetable.presentation.ManualCreateTimetableRoute
import com.star.schedule.feature.timetable.presentation.ManualCreateTimetableViewModel
import com.star.schedule.feature.timetable.presentation.ManualCreateTimetableViewModelFactory
import com.star.schedule.notification.UnifiedNotificationManager
import com.star.schedule.platform.systembar.installEdgeToEdge
import java.time.LocalDate

/**
 * 手动创建课表：填名称和开学日期，开学日期会按节假日数据给出推荐值。
 */
class ManualCreateTimetableActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatabaseProvider.init(this)
        installEdgeToEdge()

        val timetableRepository = RoomTimetableRepository(DatabaseProvider.dao())
        val settingsRepository = RoomSettingsRepository(
            context = this,
            dao = DatabaseProvider.dao(),
            notificationManager = UnifiedNotificationManager(this),
        )
        val holidayStore =
            (application as ScheduleApplication).dataSourceDependencies.holidayStore

        val viewModel = ViewModelProvider(
            this,
            ManualCreateTimetableViewModelFactory(
                timetableRepository = timetableRepository,
                holidayStore = holidayStore,
                selectTimetable = settingsRepository::selectTimetable,
                semesterName = ::semesterName,
            ),
        )[ManualCreateTimetableViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                ManualCreateTimetableRoute(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onCreated = {
                        setResult(RESULT_OK)
                        finish()
                    },
                )
            }
        }
    }

    /** 根据开学日期取「YYYY 春季/秋季」作为默认课表名，2~7 月开学的算春季。 */
    private fun semesterName(date: LocalDate): String =
        getString(
            if (date.monthValue in 2..7) {
                R.string.manual_create_semester_spring
            } else {
                R.string.manual_create_semester_fall
            },
            date.year,
        )
}
