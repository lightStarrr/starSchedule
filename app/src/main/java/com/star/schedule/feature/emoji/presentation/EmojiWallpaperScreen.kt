package com.star.schedule.feature.emoji.presentation

import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EmojiFlags
import androidx.compose.material.icons.rounded.EmojiObjects
import androidx.compose.material.icons.rounded.EmojiSymbols
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.EmojiSupportMatch
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.CompactPageTopBar
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.emoji.domain.EmojiPattern
import com.star.schedule.feature.emoji.domain.EmojiWallpaperColors
import com.star.schedule.feature.emoji.domain.EmojiWallpaperSpec
import com.star.schedule.feature.emoji.domain.emojiWallpaperLayout
import java.io.File

private enum class EmojiEditorTab { EMOJI, PATTERN, COLOR }

@Composable
fun EmojiWallpaperRoute(
    viewModel: EmojiWallpaperViewModel,
    onBack: () -> Unit,
    onExported: (File) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = state as? EmojiWallpaperUiState.Ready
    // 界面跟所选配色走：编辑页主题种子 = 当前配色的生成种子，
    // 明暗由壁纸底色决定（配色只有浅底/深底两档），不跟随系统，
    // 这样标题、底部卡片等前景元素在任何配色下都自带对比度。
    val seedColor = ready?.spec?.colors?.seedArgb?.let(::Color)
    val darkTheme = ready?.spec?.colors?.backgroundArgb
        ?.let { Color(it).luminance() < 0.5f }
        ?: isSystemInDarkTheme()
    StarScheduleTheme(
        darkTheme = darkTheme,
        dynamicColor = seedColor == null,
        seedColor = seedColor,
    ) {
        val snackbarHostState = remember { SnackbarHostState() }
        val pickerFullMessage = stringResource(
            R.string.emoji_wallpaper_max_toast, EMOJI_WALLPAPER_MAX_EMOJIS,
        )
        val exportFailedMessage = stringResource(R.string.emoji_wallpaper_export_failed)
        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is EmojiWallpaperEvent.Exported -> onExported(event.file)
                    EmojiWallpaperEvent.ExportFailed ->
                        snackbarHostState.showSnackbar(exportFailedMessage)
                    EmojiWallpaperEvent.PickerFull ->
                        snackbarHostState.showSnackbar(pickerFullMessage)
                }
            }
        }
        EmojiWallpaperScreen(
            state = state,
            snackbarHostState = snackbarHostState,
            onBack = onBack,
            onLucky = viewModel::lucky,
            onExport = viewModel::export,
            onSelectPattern = viewModel::selectPattern,
            onScaleChange = viewModel::setScale,
            onSelectPalette = viewModel::selectPalette,
            onOpenPicker = viewModel::openPicker,
            onClosePicker = viewModel::closePicker,
            onSelectPickerGroup = viewModel::selectPickerGroup,
            onAddEmoji = viewModel::addEmoji,
            onRemoveEmoji = viewModel::removeEmoji,
            onClearEmojis = viewModel::clearEmojis,
            onConfirmPicker = viewModel::confirmPicker,
        )
    }
}

/**
 * 表情壁纸编辑页：整屏实时预览 + 顶部 ✓ 导出 + 底部卡片与三段页签。
 * 壁纸本身是静态图，点 ✓ 导出后交给照片预览页走「应用」流程。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmojiWallpaperScreen(
    state: EmojiWallpaperUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onLucky: () -> Unit,
    onExport: (widthPx: Int, heightPx: Int) -> Unit,
    onSelectPattern: (EmojiPattern) -> Unit,
    onScaleChange: (Float) -> Unit,
    onSelectPalette: (Int) -> Unit,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    onSelectPickerGroup: (String) -> Unit,
    onAddEmoji: (String) -> Unit,
    onRemoveEmoji: (Int) -> Unit,
    onClearEmojis: () -> Unit,
    onConfirmPicker: () -> Unit,
) {
    val ready = state as? EmojiWallpaperUiState.Ready
    var tab by remember { mutableStateOf(EmojiEditorTab.EMOJI) }
    val windowSize = LocalWindowInfo.current.containerSize

    // 选择器模式时返回键先退出选择器，不直接离开页面。
    BackHandler(enabled = ready?.pickerOpen == true, onBack = onClosePicker)

    Box(Modifier.fillMaxSize()) {
        if (ready != null) {
            val spec = ready.spec
            val layout = remember(spec, windowSize) {
                emojiWallpaperLayout(
                    pattern = spec.pattern,
                    emojiCount = spec.emojis.size,
                    scale = spec.scale,
                    seed = spec.seed,
                    width = windowSize.width.toFloat(),
                    height = windowSize.height.toFloat(),
                )
            }
            Canvas(Modifier.fillMaxSize()) {
                drawIntoCanvas {
                    drawEmojiWallpaper(
                        canvas = it.nativeCanvas,
                        width = windowSize.width,
                        height = windowSize.height,
                        spec = spec,
                        layout = layout,
                        typeface = ready.typeface,
                    )
                }
            }
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            )
        }

        // 顶栏与底部的对比度保护层：壁纸颜色随配色变化，
        // 标题和底部编辑控件直接压在画布上可能看不清。
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        ),
                    ),
                ),
        )

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CompactPageTopBar(
                    title = stringResource(R.string.emoji_wallpaper_title),
                    onBack = onBack,
                    containerColor = Color.Transparent,
                    actions = {
                        if (ready != null) {
                            Button(
                                onClick = {
                                    onExport(windowSize.width, windowSize.height)
                                },
                                enabled = ready.emojis.isNotEmpty() && !ready.exporting,
                                shapes = ButtonDefaults.shapes(
                                    shape = RoundedCornerShape(50),
                                ),
                            ) {
                                Text(stringResource(R.string.emoji_wallpaper_done))
                            }
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (state) {
                    EmojiWallpaperUiState.Loading -> LoadingIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                    EmojiWallpaperUiState.LoadFailed -> Text(
                        text = stringResource(R.string.emoji_wallpaper_load_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                    )
                    is EmojiWallpaperUiState.Ready -> Unit
                }
            }
        }

        if (ready != null) {
            BottomEditor(
                ready = ready,
                tab = tab,
                onTabChange = { tab = it },
                onLucky = onLucky,
                onSelectPattern = onSelectPattern,
                onScaleChange = onScaleChange,
                onSelectPalette = onSelectPalette,
                onOpenPicker = onOpenPicker,
                onSelectPickerGroup = onSelectPickerGroup,
                onAddEmoji = onAddEmoji,
                onRemoveEmoji = onRemoveEmoji,
                onClearEmojis = onClearEmojis,
                onConfirmPicker = onConfirmPicker,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }
}

// ---------- 底部编辑区 ----------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BottomEditor(
    ready: EmojiWallpaperUiState.Ready,
    tab: EmojiEditorTab,
    onTabChange: (EmojiEditorTab) -> Unit,
    onLucky: () -> Unit,
    onSelectPattern: (EmojiPattern) -> Unit,
    onScaleChange: (Float) -> Unit,
    onSelectPalette: (Int) -> Unit,
    onOpenPicker: () -> Unit,
    onSelectPickerGroup: (String) -> Unit,
    onAddEmoji: (String) -> Unit,
    onRemoveEmoji: (Int) -> Unit,
    onClearEmojis: () -> Unit,
    onConfirmPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = ready.pickerOpen,
            transitionSpec = { fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) },
            label = "emojiEditorPicker",
        ) { pickerOpen ->
            if (pickerOpen) {
                EmojiPickerPanel(
                    ready = ready,
                    onSelectPickerGroup = onSelectPickerGroup,
                    onAddEmoji = onAddEmoji,
                    onRemoveEmoji = onRemoveEmoji,
                    onClearEmojis = onClearEmojis,
                    onConfirmPicker = onConfirmPicker,
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FilledTonalButton(onClick = onLucky) {
                        Icon(
                            imageVector = Icons.Rounded.Lightbulb,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.emoji_wallpaper_lucky))
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        when (tab) {
                            EmojiEditorTab.EMOJI -> EmojiTabContent(
                                ready = ready,
                                onEdit = onOpenPicker,
                            )
                            EmojiEditorTab.PATTERN -> PatternTabContent(
                                ready = ready,
                                onSelectPattern = onSelectPattern,
                                onScaleChange = onScaleChange,
                            )
                            EmojiEditorTab.COLOR -> ColorTabContent(
                                ready = ready,
                                onSelectPalette = onSelectPalette,
                            )
                        }
                    }
                    EditorTabRow(tab = tab, onTabChange = onTabChange)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditorTabRow(
    tab: EmojiEditorTab,
    onTabChange: (EmojiEditorTab) -> Unit,
) {
    val tabs = EmojiEditorTab.entries
    Row(
        horizontalArrangement = Arrangement.spacedBy(
            ButtonGroupDefaults.ConnectedSpaceBetween,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        tabs.forEachIndexed { index, item ->
            ToggleButton(
                checked = tab == item,
                onCheckedChange = { onTabChange(item) },
                buttonSize = ToggleButtonSize.Small,
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    tabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f),
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Text(
                    text = stringResource(
                        when (item) {
                            EmojiEditorTab.EMOJI -> R.string.emoji_wallpaper_tab_emoji
                            EmojiEditorTab.PATTERN -> R.string.emoji_wallpaper_tab_pattern
                            EmojiEditorTab.COLOR -> R.string.emoji_wallpaper_tab_color
                        },
                    ),
                )
            }
        }
    }
}

// ---------- 表情页签 ----------

@Composable
private fun EmojiTabContent(
    ready: EmojiWallpaperUiState.Ready,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(ready.emojis) { emoji ->
                EmojiGlyph(
                    emoji = emoji,
                    typeface = ready.typeface,
                    size = 26.sp,
                )
            }
        }
        Text(
            text = stringResource(
                R.string.emoji_wallpaper_selected_count,
                ready.emojis.size,
                EMOJI_WALLPAPER_MAX_EMOJIS,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onEdit) {
            Text(stringResource(R.string.emoji_wallpaper_edit_emoji))
        }
    }
}

// ---------- 图案页签 ----------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PatternTabContent(
    ready: EmojiWallpaperUiState.Ready,
    onSelectPattern: (EmojiPattern) -> Unit,
    onScaleChange: (Float) -> Unit,
) {
    val density = LocalDensity.current
    val sliderState = remember { SliderState(value = ready.scale, steps = 23) }
    sliderState.value = ready.scale
    val thumbSize = 56.dp
    val thumbPx = with(density) { thumbSize.toPx().toInt() }

    // 缩放行的 IconButton 自带 12dp 视觉内缩：外层边距按 16dp 目标反推，
    // 让缩略图行与 ± 图标字形到卡片边缘的距离四边一致。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 16.dp, end = 4.dp, bottom = 0.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            EmojiPattern.entries.forEach { pattern ->
                PatternThumbnail(
                    pattern = pattern,
                    ready = ready,
                    thumbPx = thumbPx,
                    thumbSize = thumbSize,
                    selected = ready.pattern == pattern,
                    onClick = { onSelectPattern(pattern) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onScaleChange(ready.scale - 1f / 24f) },
                enabled = ready.scale > 0f,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = stringResource(
                        R.string.emoji_wallpaper_scale_decrease,
                    ),
                )
            }
            Slider(
                state = sliderState,
                onValueChange = onScaleChange,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onScaleChange(ready.scale + 1f / 24f) },
                enabled = ready.scale < 1f,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(
                        R.string.emoji_wallpaper_scale_increase,
                    ),
                )
            }
        }
    }
}

/** 图案缩略图：用当前表情 + 中性色实时画，跟实际壁纸同构只是缩小。 */
@Composable
private fun PatternThumbnail(
    pattern: EmojiPattern,
    ready: EmojiWallpaperUiState.Ready,
    thumbPx: Int,
    thumbSize: Dp,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = MaterialTheme.colorScheme.surfaceContainerHighest
    val emojiColor = MaterialTheme.colorScheme.onSurface
    val label = stringResource(pattern.nameRes)
    val spec = EmojiWallpaperSpec(
        emojis = ready.emojis,
        pattern = pattern,
        scale = ready.scale,
        colors = EmojiWallpaperColors(background.toArgb(), emojiColor.toArgb(), 0),
        seed = ready.seed,
    )
    val layout = remember(spec, thumbPx) {
        emojiWallpaperLayout(
            pattern = pattern,
            emojiCount = spec.emojis.size,
            scale = spec.scale,
            seed = spec.seed,
            width = thumbPx.toFloat(),
            height = thumbPx.toFloat(),
        )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            modifier = Modifier
                .size(thumbSize)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .semantics { contentDescription = label }
                .then(
                    if (selected) {
                        Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(12.dp),
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            drawIntoCanvas {
                drawEmojiWallpaper(
                    canvas = it.nativeCanvas,
                    width = thumbPx,
                    height = thumbPx,
                    spec = spec,
                    layout = layout,
                    typeface = ready.typeface,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

// ---------- 颜色页签 ----------

@Composable
private fun ColorTabContent(
    ready: EmojiWallpaperUiState.Ready,
    onSelectPalette: (Int) -> Unit,
) {
    // 高度 = 两行色板 96 + 行距 8 + 上下各 16 内边距，四边视觉边距一致。
    LazyHorizontalGrid(
        rows = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        itemsIndexed(ready.palettes) { index, colors ->
            val selected = index == ready.paletteIndex
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(colors.backgroundArgb))
                    .clickable { onSelectPalette(index) }
                    .then(
                        if (selected) {
                            Modifier.border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            )
                        } else {
                            Modifier.border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SentimentSatisfied,
                    contentDescription = stringResource(
                        R.string.emoji_wallpaper_color_option, index + 1,
                    ),
                    tint = Color(colors.emojiArgb),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

// ---------- 选择器 ----------

@Composable
private fun EmojiPickerPanel(
    ready: EmojiWallpaperUiState.Ready,
    onSelectPickerGroup: (String) -> Unit,
    onAddEmoji: (String) -> Unit,
    onRemoveEmoji: (Int) -> Unit,
    onClearEmojis: () -> Unit,
    onConfirmPicker: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // 已选表情卡片：点表情移除，✓ 至少留一个才可点。
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(ready.emojis) { index, emoji ->
                        EmojiGlyph(
                            emoji = emoji,
                            typeface = ready.typeface,
                            size = 26.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onRemoveEmoji(index) }
                                .padding(4.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(
                        R.string.emoji_wallpaper_selected_count,
                        ready.emojis.size,
                        EMOJI_WALLPAPER_MAX_EMOJIS,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onClearEmojis) {
                    Text(stringResource(R.string.emoji_wallpaper_clear_all))
                }
                IconButton(
                    onClick = onConfirmPicker,
                    enabled = ready.emojis.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(
                            R.string.emoji_wallpaper_done,
                        ),
                    )
                }
            }
        }

        // 分类页签 + 表情网格。
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    item {
                        PickerGroupTab(
                            icon = Icons.Rounded.History,
                            description = stringResource(R.string.emoji_group_recent),
                            selected = ready.pickerGroupId == EMOJI_GROUP_RECENT,
                            onClick = { onSelectPickerGroup(EMOJI_GROUP_RECENT) },
                        )
                    }
                    items(ready.groups) { group ->
                        PickerGroupTab(
                            icon = pickerGroupIcon(group.id),
                            description = stringResource(pickerGroupNameRes(group.id)),
                            selected = ready.pickerGroupId == group.id,
                            onClick = { onSelectPickerGroup(group.id) },
                        )
                    }
                }
                val shown = if (ready.pickerGroupId == EMOJI_GROUP_RECENT) {
                    ready.recents
                } else {
                    ready.groups.firstOrNull { it.id == ready.pickerGroupId }?.emojis
                }.orEmpty()
                if (shown.isEmpty()) {
                    Text(
                        text = stringResource(R.string.emoji_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 48.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .padding(horizontal = 8.dp),
                    ) {
                        items(shown) { emoji ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .clickable { onAddEmoji(emoji) },
                                contentAlignment = Alignment.Center,
                            ) {
                                EmojiGlyph(
                                    emoji = emoji,
                                    typeface = ready.typeface,
                                    size = 26.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerGroupTab(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                LocalContentColor.current
            },
        )
    }
}

private fun pickerGroupIcon(groupId: String): ImageVector = when (groupId) {
    "smileys" -> Icons.Rounded.EmojiEmotions
    "people" -> Icons.Rounded.People
    "animals" -> Icons.Rounded.Pets
    "food" -> Icons.Rounded.Restaurant
    "travel" -> Icons.Rounded.FlightTakeoff
    "activities" -> Icons.Rounded.EmojiEvents
    "objects" -> Icons.Rounded.EmojiObjects
    "symbols" -> Icons.Rounded.EmojiSymbols
    else -> Icons.Rounded.EmojiFlags
}

private fun pickerGroupNameRes(groupId: String): Int = when (groupId) {
    "smileys" -> R.string.emoji_group_smileys
    "people" -> R.string.emoji_group_people
    "animals" -> R.string.emoji_group_animals
    "food" -> R.string.emoji_group_food
    "travel" -> R.string.emoji_group_travel
    "activities" -> R.string.emoji_group_activities
    "objects" -> R.string.emoji_group_objects
    "symbols" -> R.string.emoji_group_symbols
    else -> R.string.emoji_group_flags
}

private val EmojiPattern.nameRes: Int
    get() = when (this) {
        EmojiPattern.MOSAIC -> R.string.emoji_pattern_mosaic
        EmojiPattern.LOTUS -> R.string.emoji_pattern_lotus
        EmojiPattern.STACK -> R.string.emoji_pattern_stack
        EmojiPattern.SCATTER -> R.string.emoji_pattern_scatter
        EmojiPattern.PRISM -> R.string.emoji_pattern_prism
    }

/**
 * 用下载的 Noto Emoji 字体画表情：
 * 关掉 EmojiSupportMatch 防止 EmojiCompat 换成彩色表情。
 */
@Composable
private fun EmojiGlyph(
    emoji: String,
    typeface: Typeface,
    size: TextUnit,
    modifier: Modifier = Modifier,
) {
    val fontFamily = remember(typeface) {
        FontFamily(androidx.compose.ui.text.font.Typeface(typeface))
    }
    Text(
        text = emoji,
        fontSize = size,
        fontFamily = fontFamily,
        style = TextStyle(
            platformStyle = PlatformTextStyle(
                emojiSupportMatch = EmojiSupportMatch.None,
            ),
        ),
        modifier = modifier,
    )
}
