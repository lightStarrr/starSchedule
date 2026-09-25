package com.star.schedule.app

import androidx.compose.runtime.Composable
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.schedule.domain.ScheduleRepository
import com.star.schedule.feature.schedule.presentation.ScheduleHomeRoute

@Composable
fun StarScheduleApp(scheduleRepository: ScheduleRepository) {
    StarScheduleTheme {
        ScheduleHomeRoute(scheduleRepository)
    }
}
