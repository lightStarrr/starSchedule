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
import com.star.schedule.feature.timetable.presentation.CourseEditRoute
import com.star.schedule.feature.timetable.presentation.CourseEditViewModel
import com.star.schedule.feature.timetable.presentation.CourseEditViewModelFactory
import com.star.schedule.platform.notification.UnifiedNotificationManager
import com.star.schedule.platform.systembar.installEdgeToEdge

/**
 * 独立课程时段编辑页：支持新建课程（同时创建首条时段）、为已有课程添加时段、编辑/切换归属课程或删除单个时段。
 */
class CourseEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val timetableId = intent.getLongExtra(EXTRA_TIMETABLE_ID, -1L).takeIf { it > 0 }
        if (timetableId == null) {
            finish()
            return
        }

        val courseId = intent.getLongExtra(EXTRA_COURSE_ID, UNSET_ID)
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, UNSET_ID)

        DatabaseProvider.init(this)
        installEdgeToEdge()

        val repository = RoomTimetableRepository(
            database = DatabaseProvider.db,
            reminderScheduler = UnifiedNotificationManager(applicationContext),
        )
        val viewModel = ViewModelProvider(
            this,
            CourseEditViewModelFactory(
                repository = repository,
                timetableId = timetableId,
                courseId = courseId,
                sessionId = sessionId,
            ),
        )[CourseEditViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                CourseEditRoute(
                    viewModel = viewModel,
                    onClose = ::finish,
                    onOpenLessonTimes = {
                        startActivity(LessonTimeSettingsActivity.intent(this, timetableId))
                    },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TIMETABLE_ID = "extra_timetable_id"
        private const val EXTRA_COURSE_ID = "extra_course_id"
        private const val EXTRA_SESSION_ID = "extra_session_id"
        private const val UNSET_ID = -1L

        fun newCourseIntent(context: Context, timetableId: Long): Intent =
            Intent(context, CourseEditActivity::class.java)
                .putExtra(EXTRA_TIMETABLE_ID, timetableId)

        fun newSessionIntent(
            context: Context,
            timetableId: Long,
            courseId: Long,
        ): Intent = Intent(context, CourseEditActivity::class.java)
            .putExtra(EXTRA_TIMETABLE_ID, timetableId)
            .putExtra(EXTRA_COURSE_ID, courseId)

        fun editSessionIntent(
            context: Context,
            timetableId: Long,
            courseId: Long,
            sessionId: Long,
        ): Intent = Intent(context, CourseEditActivity::class.java)
            .putExtra(EXTRA_TIMETABLE_ID, timetableId)
            .putExtra(EXTRA_COURSE_ID, courseId)
            .putExtra(EXTRA_SESSION_ID, sessionId)
    }
}
