package com.star.schedule.feature.settings.presentation

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.ColorPickerController
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.star.schedule.R
import com.star.schedule.feature.settings.domain.TimetableSummary
import com.star.schedule.notification.FlymeLiveTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onRequestNotificationPermission: ((() -> Unit) -> Unit),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBack = onBack,
        onSelectTimetable = viewModel::selectTimetable,
        onReminderEnabledChange = viewModel::setReminderEnabled,
        onNotifyOnlyFirstChange = viewModel::setNotifyOnlyForFirstContinuousClass,
        onHideFromRecentsChange = viewModel::setHideFromRecents,
        onCloseStartupHint = viewModel::closeStartupHint,
        onWakeUpSimulationChange = viewModel::setWakeUpSimulation,
        onImportLiveIcon = viewModel::importLiveCapsuleIcon,
        onClearLiveIcon = viewModel::clearLiveCapsuleIcon,
        onLiveCapsuleBgColorChange = viewModel::setLiveCapsuleBgColor,
        onLiveTemplateChange = viewModel::setLiveNotificationTemplate,
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
    onSelectTimetable: (Long) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onNotifyOnlyFirstChange: (Boolean) -> Unit,
    onHideFromRecentsChange: (Boolean) -> Unit,
    onCloseStartupHint: () -> Unit,
    onWakeUpSimulationChange: (Boolean) -> Unit,
    onImportLiveIcon: suspend (String) -> Result<Unit>,
    onClearLiveIcon: () -> Unit,
    onLiveCapsuleBgColorChange: (String) -> Unit,
    onLiveTemplateChange: (String) -> Unit,
    onSendTestNotification: () -> Unit,
    onScheduleTestReminder: () -> Unit,
    onEnableReminder: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val currentTimetable = state.timetables.firstOrNull { it.id == state.currentTimetableId }
    val versionName = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    val liveTemplate = FlymeLiveTemplate.fromPref(state.liveNotificationTemplate)
    val savedColor = remember(state.liveCapsuleBgColor) {
        runCatching { state.liveCapsuleBgColor?.let { Color(android.graphics.Color.parseColor(it)) } }
            .getOrNull()
            ?: Color(0xFFFFE082)
    }
    var selectedColor by remember(savedColor) { mutableStateOf(savedColor) }
    var showTimetablePicker by rememberSaveable { mutableStateOf(false) }
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    var showTemplatePicker by rememberSaveable { mutableStateOf(false) }
    var clickCount by rememberSaveable { mutableIntStateOf(0) }
    var resetJob by remember { mutableStateOf<Job?>(null) }
    val colorPickerController = remember { ColorPickerController() }
    val colorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val templateSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val liveIconBitmap by produceState<Bitmap?>(null, state.liveCapsuleIconPath) {
        value = withContext(Dispatchers.IO) {
            state.liveCapsuleIconPath?.let { BitmapFactory.decodeFile(it) }
        }
    }
    val liveIconPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            onImportLiveIcon(uri.toString())
                .onSuccess {
                    Toast.makeText(
                        context,
                        R.string.notification_icon_updated,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                .onFailure { error ->
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.notification_icon_update_failed,
                            error.message ?: context.getString(R.string.error_unknown),
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
                }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
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
                .padding(innerPadding),
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
                    if (showStartupHint(state.startupHintClosed)) {
                        item {
                            SettingsGroup {
                                SettingsInfoItem(
                                    title = stringResource(R.string.startup_hint_primary),
                                    supporting = stringResource(R.string.startup_hint_secondary),
                                    shapeIndex = 0,
                                    shapeCount = 1,
                                    trailing = {
                                        IconButton(onClick = onCloseStartupHint) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = stringResource(
                                                    R.string.content_desc_close_hint,
                                                ),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }

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
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = null)
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
                        val rows = notificationRows(
                            reminderEnabled = false,
                            wakeUpAvailable = state.wakeUpSimulationAvailable,
                            liveCapsuleAvailable = false,
                        ).take(if (state.wakeUpSimulationAvailable) 2 else 1)
                        val positions = rows.positions().associateBy { it.row }
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
                            val position = positions.getValue(SettingsRow.Reminder)
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
                                onCheckedChange = { enabled ->
                                    if (enabled) onEnableReminder()
                                    else onReminderEnabledChange(false)
                                },
                                shapeIndex = position.index,
                                shapeCount = position.count,
                                stateDescription = stringResource(
                                    if (state.reminderEnabled) R.string.settings_expanded
                                    else R.string.settings_collapsed,
                                ),
                            )
                        }
                    }

                    item {
                        AnimatedVisibility(
                            visible = state.reminderEnabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            val rows = notificationRows(
                                reminderEnabled = true,
                                wakeUpAvailable = false,
                                liveCapsuleAvailable = state.liveCapsuleCustomizationAvailable,
                            ).filter { it in setOf(SettingsRow.OnlyFirst, SettingsRow.Icon, SettingsRow.CapsuleColor, SettingsRow.Template) }
                            val positions = rows.positions().associateBy { it.row }
                            SettingsGroup {
                                val firstPosition = positions.getValue(SettingsRow.OnlyFirst)
                                SettingsSwitchItem(
                                    title = stringResource(R.string.only_first_continuous_title),
                                    supporting = stringResource(R.string.only_first_continuous_support),
                                    checked = state.notifyOnlyForFirstContinuousClass,
                                    onCheckedChange = onNotifyOnlyFirstChange,
                                    shapeIndex = firstPosition.index,
                                    shapeCount = firstPosition.count,
                                )
                                val iconPosition = positions.getValue(SettingsRow.Icon)
                                SettingsListItem(
                                    title = stringResource(R.string.notification_icon_title),
                                    supporting = stringResource(
                                        if (state.liveCapsuleIconPath == null) {
                                            R.string.notification_icon_subtitle_empty
                                        } else {
                                            R.string.notification_icon_subtitle_set
                                        },
                                    ),
                                    onClick = { liveIconPicker.launch(arrayOf("image/*")) },
                                    shapeIndex = iconPosition.index,
                                    shapeCount = iconPosition.count,
                                    trailing = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (liveIconBitmap != null) {
                                                Image(
                                                    bitmap = liveIconBitmap!!.asImageBitmap(),
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(MaterialTheme.shapes.small),
                                                    contentScale = ContentScale.Crop,
                                                )
                                            } else {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_notification),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                            if (state.liveCapsuleIconPath != null) {
                                                IconButton(onClick = onClearLiveIcon) {
                                                    Icon(
                                                        Icons.Rounded.Close,
                                                        contentDescription = stringResource(
                                                            R.string.action_reset_default,
                                                        ),
                                                    )
                                                }
                                            }
                                        }
                                    },
                                )
                                if (state.liveCapsuleCustomizationAvailable) {
                                    val colorPosition = positions.getValue(SettingsRow.CapsuleColor)
                                    SettingsListItem(
                                        title = stringResource(R.string.live_capsule_bg_title),
                                        supporting = stringResource(R.string.live_capsule_bg_support),
                                        onClick = {
                                            selectedColor = savedColor
                                            colorPickerController.wheelColor = savedColor
                                            showColorPicker = true
                                        },
                                        shapeIndex = colorPosition.index,
                                        shapeCount = colorPosition.count,
                                        trailing = {
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 48.dp, height = 24.dp)
                                                    .background(savedColor, MaterialTheme.shapes.small)
                                                    .border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
                                            )
                                        },
                                    )
                                    val templatePosition = positions.getValue(SettingsRow.Template)
                                    SettingsListItem(
                                        title = stringResource(R.string.live_notification_template_title),
                                        supporting = stringResource(liveTemplate.descriptionRes),
                                        onClick = { showTemplatePicker = true },
                                        shapeIndex = templatePosition.index,
                                        shapeCount = templatePosition.count,
                                        trailing = {
                                            Text(
                                                text = stringResource(liveTemplate.titleRes),
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    item {
                        SettingsGroup {
                            SettingsActionItem(
                                title = stringResource(R.string.notification_test_title),
                                supporting = stringResource(R.string.notification_test_support_instant),
                                onClick = onSendTestNotification,
                                shapeIndex = 0,
                                shapeCount = 2,
                                leading = { Icon(Icons.Rounded.Science, contentDescription = null) },
                            )
                            SettingsActionItem(
                                title = stringResource(R.string.notification_test_title),
                                supporting = stringResource(R.string.notification_test_support_delayed),
                                onClick = onScheduleTestReminder,
                                shapeIndex = 1,
                                shapeCount = 2,
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
                            SettingsActionItem(
                                title = stringResource(R.string.about_app_title),
                                supporting = stringResource(R.string.version_label, versionName),
                                onClick = {
                                    clickCount++
                                    if (clickCount >= 5) {
                                        MediaPlayer.create(context, R.raw.egg)?.apply {
                                            start()
                                            setOnCompletionListener { it.release() }
                                        }
                                        clickCount = 0
                                        resetJob?.cancel()
                                    } else {
                                        resetJob?.cancel()
                                        resetJob = scope.launch {
                                            delay(2_000)
                                            clickCount = 0
                                        }
                                    }
                                },
                                shapeIndex = 0,
                                shapeCount = 3,
                                leading = { Icon(Icons.Rounded.Info, contentDescription = null) },
                            )
                            SettingsActionItem(
                                title = stringResource(R.string.github_title),
                                supporting = stringResource(R.string.github_url),
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, context.getString(R.string.github_url).toUri()),
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
                                        Intent(Intent.ACTION_VIEW, context.getString(R.string.qq_group_url).toUri()),
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

    if (showColorPicker) {
        ModalBottomSheet(
            onDismissRequest = { showColorPicker = false },
            sheetState = colorSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.color_picker_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                ColorPreview(selectedColor)
                HsvColorPicker(
                    modifier = Modifier.size(280.dp),
                    controller = colorPickerController,
                    initialColor = savedColor,
                    onColorChanged = { selectedColor = it.color },
                )
                BrightnessSlider(
                    modifier = Modifier.fillMaxWidth().height(24.dp),
                    controller = colorPickerController,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { showColorPicker = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = {
                            val colorHex = "#${selectedColor.toArgb().toUInt().toString(16).takeLast(6).uppercase()}"
                            onLiveCapsuleBgColorChange(colorHex)
                            showColorPicker = false
                        },
                    ) {
                        Text(stringResource(R.string.action_confirm))
                    }
                }
            }
        }
    }

    if (showTemplatePicker) {
        ModalBottomSheet(
            onDismissRequest = { showTemplatePicker = false },
            sheetState = templateSheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.live_notification_template_picker_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                FlymeLiveTemplate.entries.forEach { template ->
                    FlymeTemplateOption(
                        template = template,
                        selected = template == liveTemplate,
                        customIcon = liveIconBitmap,
                        onSelect = {
                            onLiveTemplateChange(template.prefValue)
                            scope.launch { templateSheetState.hide() }
                                .invokeOnCompletion {
                                    if (!templateSheetState.isVisible) showTemplatePicker = false
                                }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorPreview(color: Color) {
    val contentColor = if (color.luminance() > 0.7f) Color.Black else Color.White
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(color)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Notifications, contentDescription = null, tint = contentColor)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.color_preview_sample), color = contentColor)
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
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
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    shapeIndex: Int,
    shapeCount: Int,
) {
    val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    if (onClick == null) {
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(shapeIndex, shapeCount),
            enabled = enabled,
            colors = colors,
            leadingContent = leading,
            content = { Text(title) },
            supportingContent = { Text(supporting) },
            trailingContent = trailing,
        )
    } else {
        SegmentedListItem(
            onClick = onClick,
            shapes = ListItemDefaults.segmentedShapes(shapeIndex, shapeCount),
            enabled = enabled,
            colors = colors,
            leadingContent = leading,
            content = { Text(title) },
            supportingContent = { Text(supporting) },
            trailingContent = trailing,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsActionItem(
    title: String,
    supporting: String,
    onClick: () -> Unit,
    shapeIndex: Int,
    shapeCount: Int,
    leading: (@Composable () -> Unit)? = null,
) = SettingsListItem(
    title = title,
    supporting = supporting,
    onClick = onClick,
    leading = leading,
    shapeIndex = shapeIndex,
    shapeCount = shapeCount,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsInfoItem(
    title: String,
    supporting: String,
    shapeIndex: Int,
    shapeCount: Int,
    trailing: (@Composable () -> Unit)? = null,
) = SettingsListItem(
    title = title,
    supporting = supporting,
    trailing = trailing,
    shapeIndex = shapeIndex,
    shapeCount = shapeCount,
)

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
    leading: (@Composable () -> Unit)? = null,
    stateDescription: String? = null,
) {
    val modifier = stateDescription?.let { description ->
        Modifier.semantics { this.stateDescription = description }
    } ?: Modifier
    SegmentedListItem(
        modifier = modifier,
        onClick = { if (enabled) onCheckedChange(!checked) },
        shapes = ListItemDefaults.segmentedShapes(shapeIndex, shapeCount),
        enabled = enabled,
        verticalAlignment = Alignment.CenterVertically,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = leading,
        content = { Text(title) },
        supportingContent = { Text(supporting) },
        trailingContent = {
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
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
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                timetables.forEachIndexed { index, timetable ->
                    SegmentedListItem(
                        onClick = { onSelect(timetable.id) },
                        shapes = ListItemDefaults.segmentedShapes(index, timetables.size),
                        colors = ListItemDefaults.colors(),
                        leadingContent = {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                        },
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
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun FlymeTemplateOption(
    template: FlymeLiveTemplate,
    selected: Boolean,
    customIcon: Bitmap?,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(template.titleRes), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(template.descriptionRes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected) Icon(Icons.Rounded.Check, contentDescription = null)
            }
            Spacer(Modifier.height(12.dp))
            FlymeTemplatePreview(template, customIcon)
        }
    }
}

@Composable
private fun FlymeTemplatePreview(template: FlymeLiveTemplate, customIcon: Bitmap?) {
    val context = LocalContext.current
    val sampleCourse = stringResource(R.string.sample_course)
    val sampleLocation = stringResource(R.string.sample_location)

    @Composable
    fun PreviewCard(label: String, layoutRes: Int) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    factory = { ctx -> android.view.LayoutInflater.from(ctx).inflate(layoutRes, null) },
                    update = { view ->
                        view.findViewById<android.widget.TextView>(R.id.live_title)?.text = sampleCourse
                        view.findViewById<android.widget.TextView>(R.id.live_time)?.text = "10:00"
                        view.findViewById<android.widget.TextView>(R.id.location)?.text = sampleLocation
                        view.findViewById<android.widget.ImageView>(R.id.live_icon)?.let { icon ->
                            if (customIcon != null) icon.setImageBitmap(customIcon)
                            else icon.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.star))
                        }
                    },
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PreviewCard(stringResource(R.string.template_label_upcoming), template.ongoingLayout)
        PreviewCard(stringResource(R.string.template_label_finished), template.finishedLayout)
    }
}
