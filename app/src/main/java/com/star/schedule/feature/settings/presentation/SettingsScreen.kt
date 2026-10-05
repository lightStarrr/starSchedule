package com.star.schedule.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.feature.settings.domain.TimetableSummary

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBack = onBack,
        onSelectTimetable = viewModel::selectTimetable,
        onReminderEnabledChange = viewModel::setReminderEnabled,
        onNotifyOnlyFirstChange = viewModel::setNotifyOnlyForFirstContinuousClass,
        onHideFromRecentsChange = viewModel::setHideFromRecents,
        onSendTestNotification = viewModel::sendTestNotification,
        onScheduleTestReminder = viewModel::scheduleTestReminder,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSelectTimetable: (Long) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onNotifyOnlyFirstChange: (Boolean) -> Unit,
    onHideFromRecentsChange: (Boolean) -> Unit,
    onSendTestNotification: () -> Unit,
    onScheduleTestReminder: () -> Unit,
) {
    var showTimetablePicker by rememberSaveable { mutableStateOf(false) }
    val currentTimetable = state.timetables.firstOrNull { it.id == state.currentTimetableId }
    val context = LocalContext.current
    val versionName = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(start = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item { SettingsSectionTitle(R.string.settings_section_timetable) }
                    item {
                        SettingsGroup {
                            SettingsListItem(
                                title = stringResource(R.string.settings_current_timetable_title),
                                supporting = currentTimetable?.name
                                    ?: stringResource(R.string.timetable_not_selected),
                                enabled = state.timetables.isNotEmpty(),
                                onClick = { showTimetablePicker = true },
                                shapeIndex = 0,
                                shapeCount = 1,
                                trailing = {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                    }
                    if (state.timetables.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.settings_empty_timetable),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            )
                        }
                    }

                    item { SettingsSectionTitle(R.string.settings_section_notifications) }
                    item {
                        SettingsGroup {
                            SettingsSwitchItem(
                                title = stringResource(R.string.reminder_toggle_title),
                                supporting = currentTimetable?.let {
                                    stringResource(
                                        R.string.reminder_toggle_support,
                                        it.name,
                                        it.reminderTime,
                                    )
                                } ?: stringResource(R.string.settings_reminder_requires_timetable),
                                checked = state.reminderEnabled,
                                enabled = currentTimetable != null,
                                onCheckedChange = onReminderEnabledChange,
                                shapeIndex = 0,
                                shapeCount = 5,
                            )
                            SettingsSwitchItem(
                                title = stringResource(R.string.only_first_continuous_title),
                                supporting = stringResource(R.string.only_first_continuous_support),
                                checked = state.notifyOnlyForFirstContinuousClass,
                                enabled = currentTimetable != null,
                                onCheckedChange = onNotifyOnlyFirstChange,
                                shapeIndex = 1,
                                shapeCount = 5,
                            )
                            SettingsActionItem(
                                title = stringResource(R.string.notification_test_title),
                                supporting = stringResource(R.string.notification_test_support_instant),
                                onClick = onSendTestNotification,
                                shapeIndex = 2,
                                shapeCount = 5,
                            )
                            SettingsActionItem(
                                title = stringResource(R.string.notification_test_title),
                                supporting = stringResource(R.string.notification_test_support_delayed),
                                onClick = onScheduleTestReminder,
                                shapeIndex = 3,
                                shapeCount = 5,
                            )
                            SettingsInfoItem(
                                title = stringResource(R.string.live_capsule_settings_title),
                                supporting = stringResource(
                                    if (state.liveCapsuleCustomizationAvailable) {
                                        R.string.live_capsule_settings_available
                                    } else {
                                        R.string.live_capsule_settings_unavailable
                                    },
                                ),
                                shapeIndex = 4,
                                shapeCount = 5,
                            )
                        }
                    }

                    item { SettingsSectionTitle(R.string.settings_section_behavior) }
                    item {
                        SettingsGroup {
                            SettingsSwitchItem(
                                title = stringResource(R.string.hide_from_recents_title),
                                supporting = stringResource(R.string.hide_from_recents_support),
                                checked = state.hideFromRecents,
                                onCheckedChange = onHideFromRecentsChange,
                                shapeIndex = 0,
                                shapeCount = 1,
                            )
                        }
                    }

                    item { SettingsSectionTitle(R.string.settings_section_about) }
                    item {
                        SettingsGroup {
                            SettingsInfoItem(
                                title = stringResource(R.string.about_app_title),
                                supporting = stringResource(R.string.version_label, versionName),
                                shapeIndex = 0,
                                shapeCount = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTimetablePicker) {
        TimetablePickerDialog(
            timetables = state.timetables,
            selectedId = state.currentTimetableId,
            onSelect = { timetableId ->
                onSelectTimetable(timetableId)
                showTimetablePicker = false
            },
            onDismiss = { showTimetablePicker = false },
        )
    }
}

@Composable
private fun SettingsSectionTitle(stringRes: Int) {
    Text(
        text = stringResource(stringRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsListItem(
    title: String,
    supporting: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    shapeIndex: Int,
    shapeCount: Int,
) {
    SegmentedListItem(
        onClick = onClick ?: {},
        shapes = ListItemDefaults.segmentedShapes(index = shapeIndex, count = shapeCount),
        enabled = enabled,
        colors = ListItemDefaults.segmentedColors(
            containerColor =  MaterialTheme.colorScheme.surfaceContainer
        ),
        content = { Text(title) },
        supportingContent = { Text(supporting) },
        trailingContent = trailing,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsActionItem(
    title: String,
    supporting: String,
    onClick: () -> Unit,
    shapeIndex: Int,
    shapeCount: Int,
) {
    SettingsListItem(
        title = title,
        supporting = supporting,
        onClick = onClick,
        shapeIndex = shapeIndex,
        shapeCount = shapeCount,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsInfoItem(
    title: String,
    supporting: String,
    shapeIndex: Int,
    shapeCount: Int,
) {
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = shapeIndex, count = shapeCount),
        colors = ListItemDefaults.segmentedColors(
            containerColor =  MaterialTheme.colorScheme.surfaceContainer
        ),
        content = { Text(title) },
        supportingContent = { Text(supporting) },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsSwitchItem(
    title: String,
    supporting: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    shapeIndex: Int,
    shapeCount: Int,
) {
    SegmentedListItem(
        onClick = { if (enabled) onCheckedChange(!checked) },
        shapes = ListItemDefaults.segmentedShapes(index = shapeIndex, count = shapeCount),
        enabled = enabled,
        colors = ListItemDefaults.segmentedColors(
            containerColor =  MaterialTheme.colorScheme.surfaceContainer
        ),
        content = { Text(title) },
        supportingContent = { Text(supporting) },
        trailingContent = {
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TimetablePickerDialog(
    timetables: List<TimetableSummary>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_timetable_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                timetables.forEachIndexed { index, timetable ->
                    SegmentedListItem(
                        onClick = { onSelect(timetable.id) },
                        shapes = ListItemDefaults.segmentedShapes(
                            index = index,
                            count = timetables.size,
                        ),
                        colors = ListItemDefaults.segmentedColors(),
                        content = { Text(timetable.name) },
                        supportingContent = {
                            if (timetable.id == selectedId) {
                                Text(stringResource(R.string.settings_selected_timetable))
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
