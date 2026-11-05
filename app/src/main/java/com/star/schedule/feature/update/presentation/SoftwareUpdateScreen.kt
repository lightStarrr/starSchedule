package com.star.schedule.feature.update.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.CountdownSnackbar
import com.star.schedule.core.designsystem.component.MESSAGE_SNACKBAR_TIMEOUT_MILLIS
import com.star.schedule.core.designsystem.component.CompactPageTopBar
import com.star.schedule.core.designsystem.component.PageContentTopSpacing
import com.star.schedule.core.designsystem.navigationBarBottomInset
import com.star.schedule.feature.update.domain.UpdateDownloadState
import com.star.schedule.feature.update.domain.UpdateRelease

@Composable
fun SoftwareUpdateRoute(
    viewModel: SoftwareUpdateViewModel,
    onBack: () -> Unit,
    onOpenRelease: (UpdateRelease) -> Unit,
    onOpenInstallSettings: () -> Unit,
    onStartInstall: (String, String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    SoftwareUpdateEffect.OpenInstallSettings -> onOpenInstallSettings()
                    is SoftwareUpdateEffect.StartInstall ->
                        onStartInstall(effect.identity, effect.localPath)
                }
            }
        }
    }
    SoftwareUpdateScreen(
        state = state,
        downloadState = downloadState,
        onBack = onBack,
        onCheckForUpdates = viewModel::checkNow,
        onIncludeSnapshotsChange = viewModel::setIncludeSnapshots,
        onDownload = viewModel::download,
        onCancelDownload = viewModel::cancelDownload,
        onInstall = viewModel::install,
        onOpenRelease = onOpenRelease,
        onConfirmInstallPermission = viewModel::confirmInstallPermission,
        onDismissInstallPermission = viewModel::dismissInstallPermission,
        onDownloadFailureShown = viewModel::onDownloadFailureShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SoftwareUpdateScreen(
    state: SoftwareUpdateUiState,
    downloadState: UpdateDownloadState,
    onBack: () -> Unit,
    onCheckForUpdates: () -> Unit,
    onIncludeSnapshotsChange: (Boolean) -> Unit,
    onDownload: (UpdateRelease) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenRelease: (UpdateRelease) -> Unit,
    onConfirmInstallPermission: () -> Unit,
    onDismissInstallPermission: () -> Unit,
    onDownloadFailureShown: (Long) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val failure = state.downloadFailureMessage
    val failureText = failure?.let { stringResource(downloadFailureText(it.reason)) }
    LaunchedEffect(failure?.token) {
        if (failure != null && failureText != null) {
            snackbarHostState.showSnackbar(
                message = failureText,
                duration = SnackbarDuration.Indefinite,
            )
            onDownloadFailureShown(failure.token)
        }
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val navigationBarInset = navigationBarBottomInset()
    val result = state.result
    val downloadTarget = when (downloadState) {
        is UpdateDownloadState.Starting -> downloadState.target
        is UpdateDownloadState.Downloading -> downloadState.target
        is UpdateDownloadState.Verifying -> downloadState.target
        is UpdateDownloadState.Ready -> downloadState.target
        is UpdateDownloadState.Failed -> downloadState.target
        UpdateDownloadState.Idle -> null
    }
    val target = result?.target
    val displayedRelease = downloadTarget ?: result?.displayedRelease

    if (state.showInstallPermissionPrompt) {
        AlertDialog(
            onDismissRequest = onDismissInstallPermission,
            title = { Text(stringResource(R.string.update_install_permission_title)) },
            text = { Text(stringResource(R.string.update_install_permission_message)) },
            confirmButton = {
                TextButton(onClick = onConfirmInstallPermission) {
                    Text(stringResource(R.string.update_install_permission_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissInstallPermission) {
                    Text(stringResource(R.string.update_prompt_later))
                }
            },
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                CountdownSnackbar(
                    data = data,
                    timeoutMillis = MESSAGE_SNACKBAR_TIMEOUT_MILLIS,
                )
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CompactPageTopBar(
                title = stringResource(R.string.update_entry_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            ) {
                SoftwareUpdateOverflowMenu(
                    includeSnapshots = state.includeSnapshots,
                    onIncludeSnapshotsChange = onIncludeSnapshotsChange,
                )
            }
        },
        bottomBar = {
            Column {
                UpdateContentFade(opaqueAtTop = false)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(bottom = 16.dp + navigationBarInset),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (state.preferenceWriteFailed) {
                            Text(
                                text = stringResource(R.string.update_pref_save_failed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        UpdateActionArea(
                            state = state,
                            downloadState = downloadState,
                            target = target,
                            onCheckForUpdates = onCheckForUpdates,
                            onDownload = onDownload,
                            onCancelDownload = onCancelDownload,
                            onInstall = onInstall,
                            onOpenRelease = onOpenRelease,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 日志延伸到底部按钮后方，不能消费 Scaffold 的底部内边距。
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            LazyColumn(
                modifier = Modifier
                    .testTag("update-content")
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    top = PageContentTopSpacing + HERO_CARD_HEIGHT + CONTENT_FADE_HEIGHT,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                item {
                    UpdateChangelogSection(
                        release = displayedRelease,
                        isLoading = state.isChecking && result == null,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(top = PageContentTopSpacing),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    UpdateHeroCard(
                        versionName = downloadTarget?.versionName
                            ?: target?.versionName
                            ?: state.installed.versionName,
                        statusText = updateStatusText(
                            state,
                            downloadTarget,
                            downloadState is UpdateDownloadState.Failed,
                        ),
                        modifier = Modifier.widthIn(max = 720.dp),
                    )
                }
                UpdateContentFade(opaqueAtTop = true)
            }
        }
    }
}

@Composable
private fun UpdateContentFade(opaqueAtTop: Boolean) {
    val background = MaterialTheme.colorScheme.surfaceContainer
    val transparent = background.copy(alpha = 0f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CONTENT_FADE_HEIGHT)
            .background(
                Brush.verticalGradient(
                    colors = if (opaqueAtTop) {
                        listOf(background, transparent)
                    } else {
                        listOf(transparent, background)
                    },
                ),
            ),
    )
}

/**
 * 顶部卡片：图形拼成的背景保持不变，图标和版本号直接叠在上面。
 *
 * 图形都允许越过卡片边界，靠外层的 clip 裁掉，边缘才是切开的弧线而不是整块贴纸。
 * 左上角是当前版本，右上角是应用图标，都贴着卡片内边距。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateHeroCard(
    versionName: String,
    statusText: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(HERO_CARD_HEIGHT)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(colors.primaryContainer),
    ) {
        HeroShape(
            shape = MaterialShapes.SoftBurst.toShape(),
            size = 236.dp,
            align = Alignment.CenterEnd,
            offsetX = 96.dp,
            offsetY = (-26).dp,
            rotation = 12f,
            color = colors.primary.copy(alpha = 0.16f),
        )
        HeroShape(
            shape = MaterialShapes.Cookie4Sided.toShape(),
            size = 128.dp,
            align = Alignment.TopStart,
            offsetX = (-44).dp,
            offsetY = (-36).dp,
            rotation = -14f,
            color = colors.primary.copy(alpha = 0.22f),
        )
        HeroShape(
            shape = MaterialShapes.Sunny.toShape(),
            size = 62.dp,
            align = Alignment.BottomStart,
            offsetX = 22.dp,
            offsetY = 24.dp,
            rotation = -18f,
            color = colors.tertiary.copy(alpha = 0.42f),
        )
        HeroShape(
            shape = MaterialShapes.Circle.toShape(),
            size = 26.dp,
            align = Alignment.CenterStart,
            offsetX = 92.dp,
            offsetY = 18.dp,
            rotation = 0f,
            color = colors.primary.copy(alpha = 0.30f),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(HERO_CONTENT_PADDING),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = versionName,
                style = MaterialTheme.typography.headlineLargeEmphasized,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier
                    .testTag("update-hero-logo")
                    .size(HERO_LOGO_SIZE),
            )
        }
        if (statusText.isNotEmpty()) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onPrimaryContainer,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun BoxScope.HeroShape(
    shape: Shape,
    size: Dp,
    color: Color,
    align: Alignment,
    offsetX: Dp,
    offsetY: Dp,
    rotation: Float,
) {
    Box(
        modifier = Modifier
            .align(align)
            .offset(x = offsetX, y = offsetY)
            .size(size)
            .rotate(rotation)
            .clip(shape)
            .background(color),
    )
}

private val HERO_CARD_HEIGHT = 200.dp
private val HERO_CONTENT_PADDING = 20.dp
private val HERO_LOGO_SIZE = 40.dp
private val CONTENT_FADE_HEIGHT = 32.dp
