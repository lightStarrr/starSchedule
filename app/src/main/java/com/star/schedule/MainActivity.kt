package com.star.schedule

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.app.StarScheduleApp
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.feature.schedule.data.RoomScheduleRepository
import com.star.schedule.feature.settings.data.RoomStartupHintRepository
import com.star.schedule.feature.settings.presentation.StartupHintViewModel
import com.star.schedule.feature.settings.presentation.StartupHintViewModelFactory
import com.star.schedule.feature.wallpaper.data.RoomWallpaperRepository
import com.star.schedule.platform.recents.RecentsTaskVisibility
import com.star.schedule.platform.systembar.installEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatabaseProvider.init(this)
        installEdgeToEdge()
        val scheduleRepository = RoomScheduleRepository(DatabaseProvider.dao())
        val wallpaperRepository = RoomWallpaperRepository(this, DatabaseProvider.dao())
        val startupHintViewModel = ViewModelProvider(
            this,
            StartupHintViewModelFactory(RoomStartupHintRepository(DatabaseProvider.dao())),
        )[StartupHintViewModel::class.java]
        setContent {
            StarScheduleApp(
                scheduleRepository = scheduleRepository,
                wallpaperRepository = wallpaperRepository,
                startupHintViewModel = startupHintViewModel,
                onTimetableSettingsClick = {
                    startActivity(Intent(this, TimetableSettingsActivity::class.java))
                },
                onSettingsClick = {
                    startActivity(Intent(this, SettingsActivity::class.java))
                },
            )
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // 比进程级 onStop 更早：用户按 Home 或切到别的应用时，任务卡片可能已经在动画里了。
        if (RecentsTaskVisibility.hideFromRecents) {
            RecentsTaskVisibility.apply(this, excluded = true)
        }
    }
}
