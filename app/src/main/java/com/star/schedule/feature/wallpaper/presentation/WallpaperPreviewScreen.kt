package com.star.schedule.feature.wallpaper.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.CompactPageTopBar
import com.star.schedule.core.designsystem.navigationBarBottomInset
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.schedule.presentation.TimetableGrid
import com.star.schedule.feature.wallpaper.domain.WallpaperBlurSpec
import com.star.schedule.feature.wallpaper.presentation.components.WallpaperEffectsIsland

@Composable
fun WallpaperPreviewRoute(
    viewModel: WallpaperPreviewViewModel,
    onBack: () -> Unit,
    onApplied: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 与主页一致：照片取好 seed 后整页用壁纸取色，未就绪时退回动态取色。
    val seedColor = (state as? WallpaperPreviewUiState.Ready)
        ?.seedArgb
        ?.let(::Color)
    StarScheduleTheme(
        dynamicColor = seedColor == null,
        seedColor = seedColor,
    ) {
        WallpaperPreviewScreen(
            state = state,
            onBack = onBack,
            onApplied = onApplied,
            onBlurEnabledChange = viewModel::setBlurEnabled,
            onBlurRadiusChange = viewModel::setBlurRadius,
            onApply = viewModel::apply,
            onSelectSolidColor = viewModel::selectSolidColor,
        )
    }
}

/**
 * 壁纸预览页：照片铺满整个窗口（含系统栏），上面盖与主页相同的 46% 遮罩，
 * 再叠真实课表；底部是特效岛。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WallpaperPreviewScreen(
    state: WallpaperPreviewUiState,
    onBack: () -> Unit,
    onApplied: () -> Unit,
    onBlurEnabledChange: (Boolean) -> Unit,
    onBlurRadiusChange: (Float) -> Unit,
    onApply: (WallpaperBlurSpec?) -> Unit,
    onSelectSolidColor: (Int) -> Unit,
) {
    val ready = state as? WallpaperPreviewUiState.Ready
    var effectsExpanded by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val applyFailedMessage = stringResource(R.string.wallpaper_apply_failed)
    // 烘焙模糊需要窗口尺寸与 density：1 屏幕像素 = 烘焙后 1 像素。
    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current

    LaunchedEffect(ready?.applied) {
        if (ready?.applied == true) onApplied()
    }
    LaunchedEffect(ready?.applyFailed) {
        if (ready?.applyFailed == true) {
            snackbarHostState.showSnackbar(applyFailedMessage)
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (ready != null) {
            if (ready.design != null) {
                GeneratedWallpaperBackground(
                    design = ready.design.copy(
                        blurRadius = if (ready.blurEnabled) ready.blurRadius else 0f,
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (ready.stagedPath != null) {
                WallpaperBackground(
                    path = ready.stagedPath,
                    blurRadius = if (ready.blurEnabled) ready.blurRadius else 0f,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer))
            }
            // 与主页一致：壁纸上盖一层 surface 46% 遮罩，前景内容才读得清。
            if (ready.design != null || ready.stagedPath != null) Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.46f)),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.padding(bottom = navigationBarBottomInset()),
                )
            },
            topBar = {
                CompactPageTopBar(
                    title = stringResource(R.string.wallpaper_preview_title),
                    onBack = onBack,
                    containerColor = Color.Transparent,
                    actions = {
                        if (ready != null) {
                            Button(
                                onClick = {
                                    onApply(
                                        if (ready.blurEnabled) {
                                            WallpaperBlurSpec(
                                                radiusPx = with(density) {
                                                    ready.blurRadius.dp.toPx()
                                                },
                                                windowWidthPx = windowSize.width,
                                                windowHeightPx = windowSize.height,
                                            )
                                        } else {
                                            null
                                        },
                                    )
                                },
                                enabled = !ready.isApplying,
                                shapes = ButtonDefaults.shapes(
                                    shape = RoundedCornerShape(50),
                                ),
                            ) {
                                Text(stringResource(R.string.timetable_settings_apply))
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
                    is WallpaperPreviewUiState.Ready -> TimetableGrid(
                        lessonTimes = state.lessonTimes,
                        courses = state.courses,
                        currentWeek = state.week,
                        weekStartDate = state.weekStartDate,
                        showWeekend = state.showWeekend,
                        rowHeight = state.rowHeight.dp,
                        // 给特效岛（展开后约 200dp）让出位置，最后的课才不会被挡住。
                        bottomContentPadding = 200.dp + navigationBarBottomInset(),
                        modifier = Modifier.fillMaxSize(),
                    )
                    WallpaperPreviewUiState.Loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                    WallpaperPreviewUiState.LoadFailed -> Text(
                        text = stringResource(R.string.wallpaper_load_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        if (ready != null && (ready.stagedPath != null || ready.design?.emoji != null)) {
            WallpaperEffectsIsland(
                expanded = effectsExpanded,
                onExpandedChange = { effectsExpanded = it },
                blurEnabled = ready.blurEnabled,
                onBlurEnabledChange = onBlurEnabledChange,
                blurRadius = ready.blurRadius,
                onBlurRadiusChange = onBlurRadiusChange,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (ready?.design?.solidArgb != null) {
            SolidWallpaperColors(
                selectedArgb = ready.design.solidArgb,
                onSelectColor = onSelectSolidColor,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
