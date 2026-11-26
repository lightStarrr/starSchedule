package com.star.schedule.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.timetable.data.RoomTimetableRepository
import com.star.schedule.feature.timetable.presentation.CourseSettingsRoute
import com.star.schedule.feature.timetable.presentation.CourseSettingsViewModel
import com.star.schedule.feature.timetable.presentation.CourseSettingsViewModelFactory
import com.star.schedule.platform.notification.UnifiedNotificationManager
import com.star.schedule.platform.systembar.installEdgeToEdge

/**
 * 课程设置：展示当前课表的课程主表及其各上课时段，支持课程重命名、删整门课、单时段左滑撤销删除以及跳转时段编辑页。
 */
class CourseSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val timetableId = intent.getLongExtra(EXTRA_TIMETABLE_ID, -1L).takeIf { it > 0 }
        if (timetableId == null) {
            finish()
            return
        }

        DatabaseProvider.init(this)
        installEdgeToEdge()

        val repository = RoomTimetableRepository(
            database = DatabaseProvider.db,
            reminderScheduler = UnifiedNotificationManager(applicationContext),
        )
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
                    onAddCourse = {
                        startActivity(CourseEditActivity.newCourseIntent(this, timetableId))
                    },
                    onAddSession = { group ->
                        startActivity(
                            CourseEditActivity.newSessionIntent(
                                context = this,
                                timetableId = timetableId,
                                courseId = group.course.id,
                            ),
                        )
                    },
                    onEditSession = { courseId, sessionId ->
                        startActivity(
                            CourseEditActivity.editSessionIntent(
                                context = this,
                                timetableId = timetableId,
                                courseId = courseId,
                                sessionId = sessionId,
                            ),
                        )
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
