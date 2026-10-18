package com.star.schedule.feature.settings.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.ColorPickerController
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.CircleBackButton
import com.star.schedule.core.designsystem.component.MainSwitchBar
import com.star.schedule.feature.settings.domain.TimetableSummary
import com.star.schedule.notification.FlymeLiveTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReminderSettingsRoute(
    viewModel: ReminderSettingsViewModel,
    onBack: () -> Unit,
    onRequestNotificationPermission: ((() -> Unit) -> Unit),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReminderSettingsScreen(
        state = state,
        onBack = onBack,
        onReminderEnabledChange = { enabled ->
            if (enabled) {
                onRequestNotificationPermission { viewModel.setReminderEnabled(true) }
            } else {
                viewModel.setReminderEnabled(false)
            }
        },
        onNotifyOnlyFirstChange = viewModel::setNotifyOnlyForFirstContinuousClass,
        onImportLiveIcon = viewModel::importLiveCapsuleIcon,
        onClearLiveIcon = viewModel::clearLiveCapsuleIcon,
        onLiveCapsuleBgColorChange = viewModel::setLiveCapsuleBgColor,
        onLiveTemplateChange = viewModel::setLiveNotificationTemplate,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReminderSettingsScreen(
    state: ReminderSettingsUiState,
    onBack: () -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onNotifyOnlyFirstChange: (Boolean) -> Unit,
    onImportLiveIcon: suspend (String) -> Result<Unit>,
    onClearLiveIcon: () -> Unit,
    onLiveCapsuleBgColorChange: (String) -> Unit,
    onLiveTemplateChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val currentTimetable = state.currentTimetable
    val liveTemplate = FlymeLiveTemplate.fromPref(state.liveNotificationTemplate)
    val savedColor = remember(state.liveCapsuleBgColor) {
        runCatching { state.liveCapsuleBgColor?.let { Color(android.graphics.Color.parseColor(it)) } }
            .getOrNull()
            ?: Color(0xFFFFE082)
    }
    var selectedColor by remember(savedColor) { mutableStateOf(savedColor) }
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    var showTemplatePicker by rememberSaveable { mutableStateOf(false) }
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

    val rows = reminderDetailRows(state.liveCapsuleCustomizationAvailable).detailPositions()
        .associateBy { it.row }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.reminder_toggle_title)) },
                navigationIcon = {
                    Row {
                        CircleBackButton(onClick = onBack)
                        Spacer(Modifier.size(16.dp))
                    }
                },
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
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
                    LoadingIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            MainSwitchBar(
                                title = stringResource(R.string.reminder_switch_label),
                                checked = state.reminderEnabled,
                                enabled = currentTimetable != null,
                                onCheckedChange = onReminderEnabledChange,
                            )
                            Text(
                                text = reminderSupportingText(currentTimetable),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    start = 20.dp,
                                    top = 10.dp,
                                    end = 20.dp,
                                ),
                            )
                        }
                    }

                    // 总开关关闭时整块列表不渲染，与 InstallerX-Revived 的网络设置页一致。
                    if (state.reminderEnabled) {
                        item {
                            SettingsGroup {
                                val firstPosition = rows.getValue(ReminderDetailRow.OnlyFirst)
                                SettingsSwitchItem(
                                    title = stringResource(R.string.only_first_continuous_title),
                                    supporting = stringResource(R.string.only_first_continuous_support),
                                    checked = state.notifyOnlyForFirstContinuousClass,
                                    onCheckedChange = onNotifyOnlyFirstChange,
                                    shapeIndex = firstPosition.index,
                                    shapeCount = firstPosition.count,
                                )
                                val iconPosition = rows.getValue(ReminderDetailRow.Icon)
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
                                    val colorPosition = rows[ReminderDetailRow.CapsuleColor]
                                    val templatePosition = rows[ReminderDetailRow.Template]
                                    if (colorPosition != null && templatePosition != null) {
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
                                                        .border(
                                                            1.dp,
                                                            MaterialTheme.colorScheme.primary,
                                                            MaterialTheme.shapes.small,
                                                        ),
                                                )
                                            },
                                        )
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
                    }
                }
            }
        }
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
private fun reminderSupportingText(timetable: TimetableSummary?): String = timetable?.let {
    stringResource(R.string.reminder_toggle_support, it.name, it.reminderTime)
} ?: stringResource(R.string.settings_reminder_requires_timetable)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
                            else icon.setImageDrawable(
                                ContextCompat.getDrawable(context, R.drawable.star),
                            )
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
