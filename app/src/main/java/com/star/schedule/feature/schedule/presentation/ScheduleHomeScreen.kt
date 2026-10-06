package com.star.schedule.feature.schedule.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.LessonTimeEntity
import com.star.schedule.core.database.TimetableEntity
import com.star.schedule.feature.schedule.domain.ScheduleRepository
import com.star.schedule.feature.wallpaper.domain.WallpaperState
import com.star.schedule.feature.wallpaper.presentation.WallpaperBackground
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private data class HomeTimetableData(
    val timetable: TimetableEntity?,
    val courses: List<CourseEntity>,
    val lessonTimes: List<LessonTimeEntity>,
)

@Composable
fun ScheduleHomeRoute(
    repository: ScheduleRepository,
    timetableId: Long?,
    isSelectionLoading: Boolean,
    wallpaperState: WallpaperState = WallpaperState.None,
    onEditClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val homeDataFlow: Flow<HomeTimetableData?> = remember(repository, timetableId) {
        if (timetableId == null) {
            flowOf(null)
        } else {
            combine(
                repository.observeTimetable(timetableId),
                repository.observeCourses(timetableId),
                repository.observeLessonTimes(timetableId),
            ) { timetable, courses, lessonTimes ->
                HomeTimetableData(timetable, courses, lessonTimes)
            }
        }
    }
    val homeData by homeDataFlow.collectAsState(initial = null)
    val timetable = homeData?.timetable
    val courses = homeData?.courses.orEmpty()
    val lessonTimes = homeData?.lessonTimes.orEmpty()
    val isLoading = isSelectionLoading || (timetableId != null && homeData == null)
    val semesterStart = remember(timetable?.startDate) {
        runCatching { timetable?.startDate?.let(LocalDate::parse) }
            .getOrNull()
            ?: LocalDate.now().with(java.time.DayOfWeek.MONDAY)
    }
    val realCurrentWeek = remember(semesterStart) {
        ChronoUnit.DAYS.between(semesterStart, LocalDate.now()).toInt() / 7 + 1
    }.coerceAtLeast(1)
    var currentWeek by rememberSaveable(timetableId, timetable?.startDate) {
        mutableIntStateOf(realCurrentWeek)
    }

    val weekStartDate = semesterStart.plusWeeks((currentWeek - 1).toLong())
    ScheduleHomeScreen(
        currentWeek = currentWeek,
        dateRange = stringResource(
            R.string.home_date_range_template,
            weekStartDate.monthValue,
            weekStartDate.dayOfMonth,
            weekStartDate.plusDays(6).monthValue,
            weekStartDate.plusDays(6).dayOfMonth,
        ),
        lessonTimes = lessonTimes,
        courses = courses,
        hasTimetable = timetable != null,
        isLoading = isLoading,
        weekStartDate = weekStartDate,
        showWeekend = timetable?.showWeekend ?: true,
        rowHeight = (timetable?.rowHeight ?: 60).dp,
        wallpaperState = wallpaperState,
        onEditClick = onEditClick,
        onSwitchTimetableClick = {},
        onSettingsClick = onSettingsClick,
        onPreviousWeekClick = { currentWeek = (currentWeek - 1).coerceAtLeast(1) },
        onNextWeekClick = { currentWeek += 1 },
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
    lessonTimes: List<LessonTimeEntity> = emptyList(),
    courses: List<CourseEntity> = emptyList(),
    hasTimetable: Boolean = true,
    isLoading: Boolean = false,
    weekStartDate: LocalDate = LocalDate.now().with(java.time.DayOfWeek.MONDAY),
    showWeekend: Boolean = true,
    rowHeight: Dp = 60.dp,
    wallpaperState: WallpaperState = WallpaperState.None,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        WallpaperBackground(
            state = wallpaperState,
            modifier = Modifier.fillMaxSize(),
        )
        if (wallpaperState is WallpaperState.Ready) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.46f)),
            )
        }
        val emptyState = determineTimetableEmptyState(
            isLoading = isLoading,
            hasTimetable = hasTimetable,
            lessonTimes = lessonTimes,
            courses = courses,
            currentWeek = currentWeek,
        )
        Column(modifier = Modifier.fillMaxSize()) {
            ScheduleHomeHeader(
                currentWeek = currentWeek,
                dateRange = dateRange,
                onEditClick = onEditClick,
                onSwitchTimetableClick = onSwitchTimetableClick,
                onSettingsClick = onSettingsClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 8.dp),
            )

            if (!isLoading && emptyState == null) {
                TimetableGrid(
                    lessonTimes = lessonTimes,
                    courses = courses,
                    currentWeek = currentWeek,
                    weekStartDate = weekStartDate,
                    showWeekend = showWeekend,
                    rowHeight = rowHeight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }

        if (emptyState != null) {
            EmptyTimetableState(
                state = emptyState,
                modifier = Modifier.fillMaxSize(),
            )
        }

        WeekNavigationSplitButtons(
            onPreviousWeekClick = onPreviousWeekClick,
            onNextWeekClick = onNextWeekClick,
            onSelectWeekClick = onSelectWeekClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 8.dp),
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
    var expandedOverflowCount by remember { mutableIntStateOf(0) }

    SubcomposeLayout(modifier = modifier) { constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val horizontalSpacing = 12.dp.roundToPx()
        val headerInfo = subcompose(ScheduleHomeHeaderSlot.INFO) {
            ScheduleHomeHeaderInfo(
                currentWeek = currentWeek,
                dateRange = dateRange,
            )
        }.single().measure(childConstraints)

        val variants = listOf(
            HeaderActionVariant(HeaderActionLabelMode.ALL, overflowCount = 0),
            HeaderActionVariant(HeaderActionLabelMode.EDIT_ONLY, overflowCount = 0),
            HeaderActionVariant(HeaderActionLabelMode.NONE, overflowCount = 0),
            HeaderActionVariant(HeaderActionLabelMode.NONE, overflowCount = 1),
            HeaderActionVariant(HeaderActionLabelMode.NONE, overflowCount = 2),
            HeaderActionVariant(HeaderActionLabelMode.NONE, overflowCount = 3),
        )
        val actionConstraints = Constraints(maxHeight = constraints.maxHeight)
        val actionPlaceables = variants.map { variant ->
            subcompose(variant.labelMode.slot to variant.overflowCount) {
                HeaderSplitButtons(
                    labelMode = variant.labelMode,
                    overflowCount = variant.overflowCount,
                    expandedOverflowCount = expandedOverflowCount,
                    onExpandedOverflowCountChange = { expandedOverflowCount = it },
                    onEditClick = onEditClick,
                    onSwitchTimetableClick = onSwitchTimetableClick,
                    onSettingsClick = onSettingsClick,
                )
            }.single().measure(actionConstraints)
        }
        val availableWidth = constraints.maxWidth
        val selectedActionIndex = actionPlaceables.indexOfFirst { actions ->
            headerInfo.width + horizontalSpacing + actions.width <= availableWidth
        }

        if (selectedActionIndex >= 0) {
            val actions = actionPlaceables[selectedActionIndex]
            val layoutHeight = maxOf(headerInfo.height, actions.height)
            layout(availableWidth, layoutHeight) {
                headerInfo.placeRelative(0, 0)
                actions.placeRelative(availableWidth - actions.width, 0)
            }
        } else {
            val compactActionIndex = variants.indices.firstOrNull { index ->
                variants[index].labelMode == HeaderActionLabelMode.NONE &&
                    actionPlaceables[index].width <= availableWidth
            } ?: variants.lastIndex
            val compactActions = actionPlaceables[compactActionIndex]
            val layoutWidth = maxOf(constraints.minWidth, headerInfo.width, compactActions.width)
            val layoutHeight = headerInfo.height + horizontalSpacing + compactActions.height
            layout(layoutWidth, layoutHeight) {
                headerInfo.placeRelative(0, 0)
                compactActions.placeRelative(layoutWidth - compactActions.width, headerInfo.height + horizontalSpacing)
            }
        }
    }
}

@Composable
private fun ScheduleHomeHeaderInfo(
    currentWeek: Int,
    dateRange: String,
) {
    Column {
        Text(
            text = stringResource(R.string.week_label_template, currentWeek),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Text(
            text = dateRange,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}

private enum class ScheduleHomeHeaderSlot {
    INFO,
    ACTIONS_ALL,
    ACTIONS_EDIT_ONLY,
    ACTIONS_NONE,
}

private enum class HeaderActionLabelMode(
    val slot: ScheduleHomeHeaderSlot,
) {
    ALL(ScheduleHomeHeaderSlot.ACTIONS_ALL),
    EDIT_ONLY(ScheduleHomeHeaderSlot.ACTIONS_EDIT_ONLY),
    NONE(ScheduleHomeHeaderSlot.ACTIONS_NONE),
}

private data class HeaderActionVariant(
    val labelMode: HeaderActionLabelMode,
    val overflowCount: Int,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeaderSplitButtons(
    labelMode: HeaderActionLabelMode,
    overflowCount: Int,
    expandedOverflowCount: Int,
    onExpandedOverflowCountChange: (Int) -> Unit,
    onEditClick: () -> Unit,
    onSwitchTimetableClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editLabel = stringResource(R.string.home_action_edit)
    val switchTimetableLabel = stringResource(R.string.home_action_switch_timetable)
    val settingsLabel = stringResource(R.string.home_action_settings)

    val actions = listOf(
        HeaderAction(editLabel, Icons.Rounded.Edit, onEditClick),
        HeaderAction(switchTimetableLabel, Icons.Rounded.CalendarMonth, onSwitchTimetableClick),
        HeaderAction(settingsLabel, Icons.Rounded.Settings, onSettingsClick),
    )
    val visibleActions = actions.drop(overflowCount)
    val visibleItemCount = visibleActions.size + if (overflowCount > 0) 1 else 0

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        visibleActions.forEachIndexed { index, action ->
            val position = connectedPosition(index, visibleItemCount)
            val showLabel = labelMode == HeaderActionLabelMode.ALL ||
                (labelMode == HeaderActionLabelMode.EDIT_ONLY && index == 0 && overflowCount == 0)
            ConnectedActionButton(
                position = position,
                onClick = action.onClick,
                contentDescription = action.label,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    modifier = Modifier.size(
                        ButtonDefaults.iconSizeFor(ToggleButtonSize.Medium.height),
                    ),
                )
                if (showLabel) {
                    Spacer(modifier = Modifier.size(ToggleButtonDefaults.IconSpacing))
                    Text(action.label)
                }
            }
        }
        if (overflowCount > 0) {
            OverflowMenuButton(
                actions = actions.take(overflowCount),
                connectedPosition = connectedPosition(visibleActions.size, visibleItemCount),
                isExpanded = expandedOverflowCount == overflowCount,
                onExpandedChange = { expanded ->
                    onExpandedOverflowCountChange(if (expanded) overflowCount else 0)
                },
                modifier = Modifier,
            )
        }
    }
}

private data class HeaderAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

private fun connectedPosition(index: Int, itemCount: Int): ConnectedButtonPosition = when {
    itemCount <= 1 -> ConnectedButtonPosition.STANDALONE
    index == 0 -> ConnectedButtonPosition.LEADING
    index == itemCount - 1 -> ConnectedButtonPosition.TRAILING
    else -> ConnectedButtonPosition.MIDDLE
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OverflowMenuButton(
    actions: List<HeaderAction>,
    connectedPosition: ConnectedButtonPosition,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val overflowDescription = stringResource(R.string.home_action_more)
    val connectedShapes = ButtonGroupDefaults.connectedTrailingButtonShapes()
    val buttonShapes = when {
        isExpanded -> ToggleButtonShapes(
            shape = connectedShapes.shape,
            pressedShape = connectedShapes.pressedShape,
            checkedShape = CircleShape,
        )
        connectedPosition == ConnectedButtonPosition.STANDALONE -> ToggleButtonShapes(
            shape = CircleShape,
            pressedShape = CircleShape,
            checkedShape = CircleShape,
        )
        else -> connectedShapes
    }

    Box(modifier = modifier) {
        ToggleButton(
            checked = isExpanded,
            onCheckedChange = onExpandedChange,
            buttonSize = ToggleButtonSize.Small,
            shapes = buttonShapes,
            modifier = Modifier.semantics {
                contentDescription = overflowDescription
            },
            colors = ToggleButtonDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                checkedContainerColor = MaterialTheme.colorScheme.primary,
                checkedContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(
                    ButtonDefaults.iconSizeFor(ToggleButtonSize.Medium.height),
                ),
            )
        }

        DropdownMenuPopup(
            expanded = isExpanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShapes()) {
                actions.forEachIndexed { index, action ->
                    DropdownMenuItem(
                        onClick = {
                            action.onClick()
                            onExpandedChange(false)
                        },
                        text = { Text(action.label) },
                        shape = MenuDefaults.itemShape(index, actions.size).shape,
                        leadingIcon = {
                            Icon(imageVector = action.icon, contentDescription = null)
                        },
                    )
                }
            }
        }
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
    STANDALONE,
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
            ConnectedButtonPosition.STANDALONE ->
                ToggleButtonDefaults.shapesFor(ToggleButtonSize.Small.height)
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
