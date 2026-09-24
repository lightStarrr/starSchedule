package com.star.schedule.feature.schedule.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import com.star.schedule.core.designsystem.theme.StarScheduleTheme

@Composable
fun ScheduleHomeRoute() {
    ScheduleHomeScreen(
        currentWeek = 1,
        dateRange = stringResource(
            R.string.home_date_range_template,
            9,
            21,
            9,
            27,
        ),
        onEditClick = {},
        onSwitchTimetableClick = {},
        onSettingsClick = {},
        onPreviousWeekClick = {},
        onNextWeekClick = {},
        onSelectWeekClick = {},
    )
}

@Composable
fun ScheduleHomeScreen(
    currentWeek: Int,
    dateRange: String,
    onEditClick: () -> Unit,
    onSwitchTimetableClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onPreviousWeekClick: () -> Unit,
    onNextWeekClick: () -> Unit,
    onSelectWeekClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        ScheduleHomeHeader(
            currentWeek = currentWeek,
            dateRange = dateRange,
            onEditClick = onEditClick,
            onSwitchTimetableClick = onSwitchTimetableClick,
            onSettingsClick = onSettingsClick,
            modifier = Modifier.fillMaxWidth(),
        )

        WeekNavigationButtonGroup(
            currentWeek = currentWeek,
            onPreviousWeekClick = onPreviousWeekClick,
            onNextWeekClick = onNextWeekClick,
            onSelectWeekClick = onSelectWeekClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun ScheduleHomeHeader(
    currentWeek: Int,
    dateRange: String,
    onEditClick: () -> Unit,
    onSwitchTimetableClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.week_label_template, currentWeek),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = dateRange,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        HomeActionButtonGroup(
            onEditClick = onEditClick,
            onSwitchTimetableClick = onSwitchTimetableClick,
            onSettingsClick = onSettingsClick,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeActionButtonGroup(
    onEditClick: () -> Unit,
    onSwitchTimetableClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editLabel = stringResource(R.string.home_action_edit)
    val switchTimetableLabel = stringResource(R.string.home_action_switch_timetable)
    val settingsLabel = stringResource(R.string.home_action_settings)

    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState)
        },
        modifier = modifier,
    ) {
        clickableItem(
            onClick = onEditClick,
            label = editLabel,
        )
        clickableItem(
            onClick = onSwitchTimetableClick,
            label = switchTimetableLabel,
        )
        clickableItem(
            onClick = onSettingsClick,
            label = settingsLabel,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WeekNavigationButtonGroup(
    currentWeek: Int,
    onPreviousWeekClick: () -> Unit,
    onNextWeekClick: () -> Unit,
    onSelectWeekClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previousWeekLabel = stringResource(R.string.home_action_previous_week)
    val selectWeekLabel = stringResource(R.string.week_label_template, currentWeek)
    val nextWeekLabel = stringResource(R.string.home_action_next_week)

    ButtonGroup(
        overflowIndicator = { menuState ->
            ButtonGroupDefaults.OverflowIndicator(menuState)
        },
        modifier = modifier,
    ) {
        clickableItem(
            onClick = onPreviousWeekClick,
            label = previousWeekLabel,
        )
        clickableItem(
            onClick = onSelectWeekClick,
            label = selectWeekLabel,
        )
        clickableItem(
            onClick = onNextWeekClick,
            label = nextWeekLabel,
        )
    }
}

@Preview(
    name = "Schedule home",
    widthDp = 412,
    heightDp = 915,
    showBackground = true,
)
@Composable
private fun ScheduleHomeScreenPreview() {
    StarScheduleTheme(dynamicColor = false) {
        ScheduleHomeScreen(
            currentWeek = 1,
            dateRange = "9/21–9/27",
            onEditClick = {},
            onSwitchTimetableClick = {},
            onSettingsClick = {},
            onPreviousWeekClick = {},
            onNextWeekClick = {},
            onSelectWeekClick = {},
        )
    }
}
