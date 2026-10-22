package com.star.schedule.feature.settings.presentation

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import com.star.schedule.core.designsystem.navigationBarBottomInset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.BuildConfig
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.PageTopBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenReminderSettings: () -> Unit,
    onRequestNotificationPermission: ((() -> Unit) -> Unit),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBack = onBack,
        onOpenReminderSettings = onOpenReminderSettings,
        onReminderEnabledChange = viewModel::setReminderEnabled,
        onHideFromRecentsChange = viewModel::setHideFromRecents,
        onWakeUpSimulationChange = viewModel::setWakeUpSimulation,
        onSendTestNotification = {
            onRequestNotificationPermission(viewModel::sendTestNotification)
        },
        onScheduleTestReminder = {
            onRequestNotificationPermission(viewModel::scheduleTestReminder)
        },
        onEnableReminder = {
            onRequestNotificationPermission { viewModel.setReminderEnabled(true) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onOpenReminderSettings: () -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onHideFromRecentsChange: (Boolean) -> Unit,
    onWakeUpSimulationChange: (Boolean) -> Unit,
    onSendTestNotification: () -> Unit,
    onScheduleTestReminder: () -> Unit,
    onEnableReminder: () -> Unit,
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val currentTimetable = state.timetables.firstOrNull { it.id == state.currentTimetableId }
    // 直接读编译期常量：任何一次打包都能被唯一定位，不用再猜用户装的是哪版。
    val buildStamp = BuildConfig.BUILD_STAMP

    val navigationBarInset = navigationBarBottomInset()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        // 不吃系统栏内边距：内容要能滑到手势条下面，手势条后面才不会留一条纯背景的带子。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageTopBar(
                title = stringResource(R.string.settings_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(bottom = 24.dp + navigationBarInset),
                ) {
                    item {
                        SettingsSectionTitle(
                            resId = R.string.settings_section_notifications,
                            // 第一组标题直接接在顶栏留白下面，不再叠上边距。
                            topPadding = 0.dp,
                        )
                    }
                    item {
                        val positions = notificationRows(state.wakeUpSimulationAvailable)
                            .positions()
                            .associateBy { it.row }
                        SettingsGroup {
                            if (state.wakeUpSimulationAvailable) {
                                val position = positions.getValue(SettingsRow.WakeUp)
                                SettingsSwitchItem(
                                    title = stringResource(R.string.wakeup_simulation_title),
                                    supporting = stringResource(R.string.wakeup_simulation_support),
                                    checked = state.wakeUpSimulationEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled && !state.wakeUpProxyInstalled) {
                                            Toast.makeText(
                                                context,
                                                R.string.wakeup_simulation_proxy_missing,
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        } else {
                                            onWakeUpSimulationChange(enabled)
                                        }
                                    },
                                    shapeIndex = position.index,
                                    shapeCount = position.count,
                                )
                            }
                            val reminderPosition = positions.getValue(SettingsRow.Reminder)
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
                                // 左侧进入独立的提醒设置页，右侧开关就地切换。
                                onClick = onOpenReminderSettings,
                                onCheckedChange = { enabled ->
                                    if (enabled) onEnableReminder()
                                    else onReminderEnabledChange(false)
                                },
                                shapeIndex = reminderPosition.index,
                                shapeCount = reminderPosition.count,
                            )
                            val instantPosition = positions.getValue(SettingsRow.InstantTest)
                            SettingsActionItem(
                                title = stringResource(R.string.notification_test_title),
                                supporting = stringResource(R.string.notification_test_support_instant),
                                onClick = onSendTestNotification,
                                shapeIndex = instantPosition.index,
                                shapeCount = instantPosition.count,
                                leading = { Icon(Icons.Rounded.Science, contentDescription = null) },
                            )
                            val delayedPosition = positions.getValue(SettingsRow.DelayedTest)
                            SettingsActionItem(
                                title = stringResource(R.string.notification_test_title),
                                supporting = stringResource(R.string.notification_test_support_delayed),
                                onClick = onScheduleTestReminder,
                                shapeIndex = delayedPosition.index,
                                shapeCount = delayedPosition.count,
                                leading = { Icon(Icons.Rounded.Science, contentDescription = null) },
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
                                leading = {
                                    Icon(Icons.Rounded.VisibilityOff, contentDescription = null)
                                },
                            )
                        }
                    }

                    item { SettingsSectionTitle(R.string.settings_section_about) }
                    item {
                        SettingsGroup {
                            // 只展示版本信息，之前的「连点五次播放彩蛋」已经去掉。
                            SettingsListItem(
                                title = stringResource(R.string.about_app_title),
                                supporting = stringResource(R.string.version_label, buildStamp),
                                shapeIndex = 0,
                                shapeCount = 3,
                                leading = { Icon(Icons.Rounded.Info, contentDescription = null) },
                            )
                            SettingsActionItem(
                                title = stringResource(R.string.github_title),
                                supporting = stringResource(R.string.github_url),
                                onClick = {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            context.getString(R.string.github_url).toUri(),
                                        ),
                                    )
                                },
                                shapeIndex = 1,
                                shapeCount = 3,
                                leading = { Icon(Icons.Rounded.Code, contentDescription = null) },
                            )
                            SettingsActionItem(
                                title = stringResource(R.string.qq_group_title),
                                supporting = "947574953",
                                onClick = {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            context.getString(R.string.qq_group_url).toUri(),
                                        ),
                                    )
                                },
                                shapeIndex = 2,
                                shapeCount = 3,
                                leading = { Icon(Icons.Rounded.Group, contentDescription = null) },
                            )
                        }
                    }
                }
            }
        }
    }
}
