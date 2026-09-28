package com.star.schedule.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.core.designsystem.theme.rememberImageSeedColor
import com.star.schedule.feature.schedule.domain.ScheduleRepository
import com.star.schedule.feature.schedule.presentation.ScheduleBackgroundMode
import com.star.schedule.feature.schedule.presentation.ScheduleHomeRoute

@Composable
fun StarScheduleApp(scheduleRepository: ScheduleRepository) {
    var backgroundIndex by rememberSaveable { mutableIntStateOf(0) }
    val backgroundMode = remember(backgroundIndex) {
        ScheduleBackgroundMode.entries[backgroundIndex % ScheduleBackgroundMode.entries.size]
    }
    val seedColor = rememberImageSeedColor(backgroundMode.imageResId)

    StarScheduleTheme(
        dynamicColor = backgroundMode == ScheduleBackgroundMode.SYSTEM,
        seedColor = seedColor,
    ) {
        ScheduleHomeRoute(
            repository = scheduleRepository,
            backgroundMode = backgroundMode,
            onEditClick = {
                backgroundIndex = (backgroundIndex + 1) % ScheduleBackgroundMode.entries.size
            },
        )
    }
}
