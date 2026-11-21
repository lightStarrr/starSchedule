package com.star.schedule.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.schedule.presentation.ScheduleHomeRoute
import com.star.schedule.feature.schedule.presentation.ScheduleHomeViewModel
import com.star.schedule.feature.settings.presentation.StartupHintRoute
import com.star.schedule.feature.settings.presentation.StartupHintViewModel
import com.star.schedule.feature.update.presentation.StartupUpdateRoute
import com.star.schedule.feature.update.presentation.StartupUpdateViewModel
import com.star.schedule.feature.wallpaper.domain.WallpaperState
import com.star.schedule.feature.wallpaper.domain.seedArgb

@Composable
fun StarScheduleApp(
    scheduleHomeViewModel: ScheduleHomeViewModel,
    startupHintViewModel: StartupHintViewModel,
    startupUpdateViewModel: StartupUpdateViewModel,
    onTimetableSettingsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSoftwareUpdateClick: () -> Unit,
) {
    val homeState by scheduleHomeViewModel.uiState.collectAsStateWithLifecycle()
    val startupHintState by startupHintViewModel.uiState.collectAsStateWithLifecycle()
    // 锁存：课表首次加载完成后一直放行启动弹窗；切课表重新加载或旋转屏幕时不收回。
    var homeContentReady by rememberSaveable { mutableStateOf(false) }
    val wallpaperState = homeState.wallpaperState
    val seedColor = wallpaperState.seedArgb?.let { argb -> Color(argb) }

    StarScheduleTheme(
        dynamicColor = wallpaperState is WallpaperState.None,
        seedColor = seedColor,
    ) {
        Box(Modifier.fillMaxSize()) {
            ScheduleHomeRoute(
                viewModel = scheduleHomeViewModel,
                onTimetableSettingsClick = onTimetableSettingsClick,
                onSettingsClick = onSettingsClick,
                onContentReady = { homeContentReady = true },
            )
            if (homeContentReady) {
                StartupHintRoute(viewModel = startupHintViewModel)
                StartupUpdateRoute(
                    viewModel = startupUpdateViewModel,
                    startupHintState = startupHintState,
                    onOpenUpdate = onSoftwareUpdateClick,
                )
            }
        }
    }
}
