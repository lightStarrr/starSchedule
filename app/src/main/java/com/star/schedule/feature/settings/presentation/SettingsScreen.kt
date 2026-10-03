package com.star.schedule.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TableView
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import androidx.compose.ui.platform.LocalContext
import com.star.schedule.feature.settings.domain.TimetableSummary

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
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
    var showTimetablePicker by remember { mutableStateOf(false) }
    val currentTimetable = state.timetables.firstOrNull { it.id == state.currentTimetableId }
    val context = LocalContext.current
    val versionName = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
        ) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item { SettingsSectionTitle(R.string.settings_section_timetable) }
                    item {
                        SettingsListItem(
                            title = stringResource(R.string.settings_current_timetable_title),
                            supporting = currentTimetable?.name
                                ?: stringResource(R.string.timetable_not_selected),
                            icon = Icons.Rounded.TableView,
                            onClick = { showTimetablePicker = true },
                            trailing = {
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                    if (state.timetables.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.settings_empty_timetable),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 72.dp, vertical = 8.dp),
                            )
                        }
                    }

                    item { SettingsSectionTitle(R.string.settings_section_notifications) }
                    item {
                        SettingsSwitchItem(
                            title = stringResource(R.string.reminder_toggle_title),
                            supporting = currentTimetable?.let {
                                stringResource(
                                    R.string.reminder_toggle_support,
                                    it.name,
                                    it.reminderTime,
                                )
                            } ?: stringResource(R.string.settings_reminder_requires_timetable),
                            icon = if (state.reminderEnabled) {
                                Icons.Rounded.NotificationsActive
                            } else {
                                Icons.Rounded.NotificationsOff
                            },
                            checked = state.reminderEnabled,
                            enabled = currentTimetable != null,
                            onCheckedChange = onReminderEnabledChange,
                        )
                    }
                    item {
                        SettingsSwitchItem(
                            title = stringResource(R.string.only_first_continuous_title),
                            supporting = stringResource(R.string.only_first_continuous_support),
                            icon = Icons.Rounded.Schedule,
                            checked = state.notifyOnlyForFirstContinuousClass,
                            enabled = currentTimetable != null,
                            onCheckedChange = onNotifyOnlyFirstChange,
                        )
                    }
                    item {
                        SettingsListItem(
                            title = stringResource(R.string.notification_test_title),
                            supporting = stringResource(R.string.notification_test_support_instant),
                            icon = Icons.Rounded.Notifications,
                            onClick = onSendTestNotification,
                        )
                    }
                    item {
                        SettingsListItem(
                            title = stringResource(R.string.notification_test_title),
                            supporting = stringResource(R.string.notification_test_support_delayed),
                            icon = Icons.Rounded.Schedule,
                            onClick = onScheduleTestReminder,
                        )
                    }
                    item {
                        SettingsListItem(
                            title = stringResource(R.string.live_capsule_settings_title),
                            supporting = stringResource(
                                if (state.liveCapsuleCustomizationAvailable) {
                                    R.string.live_capsule_settings_available
                                } else {
                                    R.string.live_capsule_settings_unavailable
                                },
                            ),
                            icon = Icons.Rounded.PhoneAndroid,
                        )
                    }

                    item { SettingsSectionTitle(R.string.settings_section_behavior) }
                    item {
                        SettingsSwitchItem(
                            title = stringResource(R.string.hide_from_recents_title),
                            supporting = stringResource(R.string.hide_from_recents_support),
                            icon = Icons.Rounded.VisibilityOff,
                            checked = state.hideFromRecents,
                            onCheckedChange = onHideFromRecentsChange,
                        )
                    }

                    item { SettingsSectionTitle(R.string.settings_section_about) }
                    item {
                        SettingsListItem(
                            title = stringResource(R.string.about_app_title),
                            supporting = stringResource(
                                R.string.version_label,
                                versionName,
                            ),
                            icon = Icons.Rounded.Info,
                        )
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
        modifier = Modifier.padding(start = 72.dp, top = 24.dp, end = 24.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsListItem(
    title: String,
    supporting: String,
    icon: ImageVector,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    SegmentedListItem(
        onClick = onClick ?: {},
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        enabled = onClick != null,
        colors = ListItemDefaults.segmentedColors(),
        content = { Text(title) },
        supportingContent = { Text(supporting) },
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
        trailingContent = trailing,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsSwitchItem(
    title: String,
    supporting: String,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        onClick = { if (enabled) onCheckedChange(!checked) },
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        enabled = enabled,
        colors = ListItemDefaults.segmentedColors(),
        content = { Text(title) },
        supportingContent = { Text(supporting) },
        leadingContent = { Icon(imageVector = icon, contentDescription = null) },
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
                timetables.forEach { timetable ->
                    SegmentedListItem(
                        onClick = { onSelect(timetable.id) },
                        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                        colors = ListItemDefaults.segmentedColors(),
                        content = { Text(timetable.name) },
                        supportingContent = {
                            if (timetable.id == selectedId) {
                                Text(stringResource(R.string.settings_selected_timetable))
                            }
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.TableView, contentDescription = null)
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
