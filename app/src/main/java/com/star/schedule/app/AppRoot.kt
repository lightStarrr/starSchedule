package com.star.schedule.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.schedule.domain.ScheduleRepository
import com.star.schedule.feature.schedule.presentation.ScheduleHomeRoute
import com.star.schedule.feature.wallpaper.domain.WallpaperRepository
import com.star.schedule.feature.wallpaper.domain.WallpaperState
import kotlinx.coroutines.flow.flowOf

@Composable
fun StarScheduleApp(
    scheduleRepository: ScheduleRepository,
    wallpaperRepository: WallpaperRepository,
) {
    val timetableId by scheduleRepository.observeCurrentTimetableId().collectAsState(initial = null)
    key(timetableId) {
        val wallpaperState by remember(timetableId) {
            timetableId?.let(wallpaperRepository::observe) ?: flowOf(WallpaperState.None)
        }.collectAsState(initial = WallpaperState.None)
        val seedColor = (wallpaperState as? WallpaperState.Ready)
            ?.seedArgb
            ?.let { argb -> Color(argb) }

        StarScheduleTheme(
            dynamicColor = wallpaperState is WallpaperState.None,
            seedColor = seedColor,
        ) {
            ScheduleHomeRoute(
                repository = scheduleRepository,
                wallpaperState = wallpaperState,
            )
        }
    }
}
