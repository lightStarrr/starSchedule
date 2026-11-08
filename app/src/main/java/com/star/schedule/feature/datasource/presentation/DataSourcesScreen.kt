package com.star.schedule.feature.datasource.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.AppMenu
import com.star.schedule.core.designsystem.component.MenuEntry
import com.star.schedule.core.designsystem.component.PageContentTopSpacing
import com.star.schedule.core.designsystem.component.PageTopBar
import com.star.schedule.core.designsystem.navigationBarBottomInset
import com.star.schedule.feature.datasource.domain.RefreshInterval
import com.star.schedule.feature.holiday.data.HolidayCnDataSource
import com.star.schedule.feature.settings.presentation.SettingsGroup
import com.star.schedule.feature.settings.presentation.SettingsListItem
import com.star.schedule.feature.settings.presentation.SettingsSectionTitle
import java.text.DateFormat
import java.util.Date

@Composable
fun DataSourcesRoute(
    viewModel: DataSourcesViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DataSourcesScreen(
        state = state,
        onBack = onBack,
        onRefreshSource = viewModel::refresh,
        onIntervalChange = viewModel::setInterval,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataSourcesScreen(
    state: DataSourcesUiState,
    onBack: () -> Unit,
    onRefreshSource: (String) -> Unit,
    onIntervalChange: (RefreshInterval) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navigationBarInset = navigationBarBottomInset()
    var intervalMenuExpanded by remember { mutableStateOf(false) }
    var showCustomIntervalDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageTopBar(
                title = stringResource(R.string.datasource_page_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(
                    top = PageContentTopSpacing + 8.dp,
                    bottom = 24.dp + navigationBarInset,
                ),
        ) {
            SettingsSectionTitle(R.string.datasource_section_refresh, topPadding = 0.dp)
            SettingsGroup {
                SettingsListItem(
                    title = stringResource(R.string.datasource_interval_title),
                    supporting = stringResource(R.string.datasource_interval_support),
                    onClick = { intervalMenuExpanded = true },
                    shapeIndex = 0,
                    shapeCount = 1,
                    leading = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                    trailing = {
                        // 菜单以下拉图标为锚点，图标与菜单同一层；
                        // 图标本身不接点击，展开由整行 onClick 触发
                        Box {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = intervalLabel(state.interval),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(Icons.Rounded.UnfoldMore, contentDescription = null)
                            }
                            RefreshIntervalMenu(
                                expanded = intervalMenuExpanded,
                                current = state.interval,
                                onDismiss = { intervalMenuExpanded = false },
                                onSelect = onIntervalChange,
                                onCustom = { showCustomIntervalDialog = true },
                            )
                        }
                    },
                )
            }

            SettingsSectionTitle(R.string.datasource_section_sources)
            SettingsGroup {
                state.sources.forEachIndexed { index, item ->
                    SettingsListItem(
                        title = stringResource(item.nameRes),
                        supporting = sourceSupporting(item),
                        onClick = { onRefreshSource(item.id) },
                        enabled = !item.refreshing,
                        shapeIndex = index,
                        shapeCount = state.sources.size,
                        leading = { Icon(sourceIcon(item.id), contentDescription = null) },
                        trailing = {
                            if (item.refreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                // 图标不单独接点击，刷新由整行 onClick 触发（与服务商选择一致）
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = stringResource(
                                        R.string.datasource_refresh_action,
                                    ),
                                )
                            }
                        },
                    )
                }
            }

        }
    }

    if (showCustomIntervalDialog) {
        CustomIntervalDialog(
            onDismiss = { showCustomIntervalDialog = false },
            onConfirm = { days ->
                onIntervalChange(RefreshInterval(days))
                showCustomIntervalDialog = false
            },
        )
    }
}

/** 每个源的图标先写死在这里，源多起来再考虑搬进注册表。 */
private fun sourceIcon(sourceId: String) = when (sourceId) {
    HolidayCnDataSource.SOURCE_ID -> Icons.AutoMirrored.Rounded.EventNote
    else -> Icons.Rounded.Refresh
}

@Composable
private fun sourceSupporting(item: DataSourceItemUiState): String {
    val description = stringResource(item.descriptionRes)
    val status = when {
        item.refreshing -> stringResource(R.string.datasource_refreshing)
        item.lastError != null ->
            stringResource(R.string.datasource_last_error, item.lastError)

        item.lastSuccessAt != null -> stringResource(
            R.string.datasource_last_success,
            formatRefreshTime(item.lastSuccessAt),
        )

        else -> stringResource(R.string.datasource_never_updated)
    }
    return "$description\n$status"
}

private fun formatRefreshTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMillis))

@Composable
private fun intervalLabel(interval: RefreshInterval): String = when {
    !interval.enabled -> stringResource(R.string.datasource_interval_off)
    interval == RefreshInterval.DAILY -> stringResource(R.string.datasource_interval_daily)
    interval == RefreshInterval.WEEKLY -> stringResource(R.string.datasource_interval_weekly)
    interval == RefreshInterval.MONTHLY -> stringResource(R.string.datasource_interval_monthly)
    else -> stringResource(R.string.datasource_interval_custom, interval.days)
}

/**
 * 刷新频率菜单：第一组是可选中的预设档位（当前是自定义值时把它也列进去保持选中态），
 * 第二组是「自定义…」动作项，点开输入天数的对话框。
 */
@Composable
private fun RefreshIntervalMenu(
    expanded: Boolean,
    current: RefreshInterval,
    onDismiss: () -> Unit,
    onSelect: (RefreshInterval) -> Unit,
    onCustom: () -> Unit,
) {
    val optionEntries = buildList {
        add(
            MenuEntry(
                label = stringResource(R.string.datasource_interval_off),
                selected = !current.enabled,
                onClick = {
                    onDismiss()
                    onSelect(RefreshInterval.OFF)
                },
            ),
        )
        RefreshInterval.PRESETS.forEach { preset ->
            add(
                MenuEntry(
                    label = intervalLabel(preset),
                    selected = current == preset,
                    onClick = {
                        onDismiss()
                        onSelect(preset)
                    },
                ),
            )
        }
        if (current.enabled && current !in RefreshInterval.PRESETS) {
            add(
                MenuEntry(
                    label = stringResource(R.string.datasource_interval_custom, current.days),
                    selected = true,
                    onClick = onDismiss,
                ),
            )
        }
    }
    val actionEntries = listOf(
        MenuEntry(
            label = stringResource(R.string.datasource_interval_custom_option),
            onClick = {
                onDismiss()
                onCustom()
            },
        ),
    )

    AppMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        groups = listOf(optionEntries, actionEntries),
    )
}

/**
 * 自定义间隔天数输入：没有填有效天数时「确定」不可点，
 * 避免写出一个非法值。
 */
@Composable
private fun CustomIntervalDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var days by remember { mutableStateOf("") }
    val validDays = days.toIntOrNull()?.takeIf { it in 1..365 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.datasource_interval_custom_title)) },
        text = {
            OutlinedTextField(
                value = days,
                onValueChange = { input -> days = input.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.datasource_interval_custom_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { validDays?.let(onConfirm) },
                enabled = validDays != null,
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
