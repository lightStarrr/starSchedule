package com.star.schedule

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.settings.data.RoomSettingsRepository
import com.star.schedule.feature.timetable.presentation.TimetableSettingsRoute
import com.star.schedule.feature.timetable.presentation.TimetableSettingsViewModel
import com.star.schedule.feature.timetable.presentation.TimetableSettingsViewModelFactory
import com.star.schedule.feature.wallpaper.data.RoomWallpaperRepository
import com.star.schedule.notification.UnifiedNotificationManager
import com.star.schedule.platform.systembar.installEdgeToEdge

class TimetableSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatabaseProvider.init(this)
        installEdgeToEdge()

        val settingsRepository = RoomSettingsRepository(
            context = this,
            dao = DatabaseProvider.dao(),
            notificationManager = UnifiedNotificationManager(this),
        )
        val viewModel = ViewModelProvider(
            this,
            TimetableSettingsViewModelFactory(
                settingsRepository,
                RoomWallpaperRepository(this, DatabaseProvider.dao()),
            ),
        )[TimetableSettingsViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                TimetableSettingsRoute(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onOpenRowHeight = { timetableId ->
                        startActivity(RowHeightActivity.intent(this, timetableId))
                    },
                    onOpenCourses = { timetableId ->
                        startActivity(CourseSettingsActivity.intent(this, timetableId))
                    },
                    onOpenLessonTimes = { timetableId ->
                        startActivity(LessonTimeSettingsActivity.intent(this, timetableId))
                    },
                    onOpenWallpaper = { timetableId ->
                        startActivity(WallpaperActivity.intent(this, timetableId))
                    },
                    onCreateTimetable = {
                        startActivity(Intent(this, NewTimetableActivity::class.java))
                    },
                )
            }
        }
    }
}
