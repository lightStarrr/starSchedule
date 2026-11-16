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

        // 页面作用的对象是「打开时指定的课表」，不是已应用的那张；没带 id 就没有目标。
        val timetableId = intent.getLongExtra(EXTRA_TIMETABLE_ID, -1L).takeIf { it > 0 }
        if (timetableId == null) {
            finish()
            return
        }

        DatabaseProvider.init(this)
        installEdgeToEdge()

        val repository = RoomScheduleRepository(DatabaseProvider.dao())
        val viewModel = ViewModelProvider(
            this,
            CourseSettingsViewModelFactory(repository, timetableId),
        )[CourseSettingsViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                CourseSettingsRoute(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onOpenLessonTimes = {
                        startActivity(LessonTimeSettingsActivity.intent(this, timetableId))
                    },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TIMETABLE_ID = "extra_timetable_id"

        fun intent(context: Context, timetableId: Long): Intent =
            Intent(context, CourseSettingsActivity::class.java)
                .putExtra(EXTRA_TIMETABLE_ID, timetableId)
    }
}
