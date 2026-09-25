package com.star.schedule.feature.schedule.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

        WeekNavigationSplitButtons(
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

        HeaderSplitButtons(
            onEditClick = onEditClick,
            onSwitchTimetableClick = onSwitchTimetableClick,
            onSettingsClick = onSettingsClick,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeaderSplitButtons(
    onEditClick: () -> Unit,
    onSwitchTimetableClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsDescription = stringResource(R.string.home_action_settings)
    val switchTimetableDescription = stringResource(R.string.home_action_switch_timetable)

    ButtonGroup(
        overflowIndicator = {},
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        customItem(
            buttonGroupContent = {
                ConnectedActionButton(
                    position = ConnectedButtonPosition.LEADING,
                    onClick = onEditClick,
                    contentDescription = stringResource(R.string.home_action_edit),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(
                            ButtonDefaults.iconSizeFor(ToggleButtonSize.Medium.height),
                        ),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.home_action_edit))
                }
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                ConnectedActionButton(
                    position = ConnectedButtonPosition.MIDDLE,
                    onClick = onSwitchTimetableClick,
                    contentDescription = switchTimetableDescription,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(
                            ButtonDefaults.iconSizeFor(ToggleButtonSize.Medium.height),
                        ),
                    )
                }
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                ConnectedActionButton(
                    position = ConnectedButtonPosition.TRAILING,
                    onClick = onSettingsClick,
                    contentDescription = settingsDescription,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(
                            ButtonDefaults.iconSizeFor(ToggleButtonSize.Medium.height),
                        ),
                    )
                }
            },
            menuContent = {},
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WeekNavigationSplitButtons(
    onPreviousWeekClick: () -> Unit,
    onNextWeekClick: () -> Unit,
    onSelectWeekClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectWeekDescription = stringResource(R.string.select_week_number_title)

    ButtonGroup(
        overflowIndicator = {},
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        customItem(
            buttonGroupContent = {
                ConnectedActionButton(
                    position = ConnectedButtonPosition.LEADING,
                    onClick = onPreviousWeekClick,
                    contentDescription = stringResource(R.string.home_action_previous_week),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronLeft,
                        contentDescription = null,
                        modifier = Modifier.size(
                            ButtonDefaults.iconSizeFor(ToggleButtonSize.Medium.height),
                        ),
                    )
                }
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                ConnectedActionButton(
                    position = ConnectedButtonPosition.MIDDLE,
                    onClick = onSelectWeekClick,
                    contentDescription = selectWeekDescription,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(
                            ButtonDefaults.iconSizeFor(ToggleButtonSize.Medium.height),
                        ),
                    )
                }
            },
            menuContent = {},
        )
        customItem(
            buttonGroupContent = {
                ConnectedActionButton(
                    position = ConnectedButtonPosition.TRAILING,
                    onClick = onNextWeekClick,
                    contentDescription = stringResource(R.string.home_action_next_week),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(
                            ButtonDefaults.iconSizeFor(ToggleButtonSize.Medium.height),
                        ),
                    )
                }
            },
            menuContent = {},
        )
    }
}

private enum class ConnectedButtonPosition {
    LEADING,
    MIDDLE,
    TRAILING,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectedActionButton(
    position: ConnectedButtonPosition,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    ToggleButton(
        checked = false,
        onCheckedChange = { onClick() },
        buttonSize = ToggleButtonSize.Small,
        shapes = when (position) {
            ConnectedButtonPosition.LEADING -> ButtonGroupDefaults.connectedLeadingButtonShapes()
            ConnectedButtonPosition.MIDDLE -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            ConnectedButtonPosition.TRAILING -> ButtonGroupDefaults.connectedTrailingButtonShapes()
        },
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
        },
        colors = ToggleButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        content = content,
    )
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
