package com.star.schedule

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.star.schedule.app.StarScheduleApp
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.feature.schedule.data.RoomScheduleRepository
import com.star.schedule.feature.wallpaper.data.RoomWallpaperRepository
import com.star.schedule.platform.systembar.installAdaptiveStatusBarAppearance

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatabaseProvider.init(this)
        enableEdgeToEdge()
        installAdaptiveStatusBarAppearance()
        val scheduleRepository = RoomScheduleRepository(DatabaseProvider.dao())
        val wallpaperRepository = RoomWallpaperRepository(this, DatabaseProvider.dao())
        setContent {
            StarScheduleApp(
                scheduleRepository = scheduleRepository,
                wallpaperRepository = wallpaperRepository,
                onSettingsClick = {
                    startActivity(Intent(this, SettingsActivity::class.java))
                },
            )
        }
    }
}
