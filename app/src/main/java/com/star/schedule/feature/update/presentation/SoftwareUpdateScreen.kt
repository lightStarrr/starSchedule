package com.star.schedule.feature.update.presentation

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
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
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
        onProblemMessageShown = viewModel::onProblemMessageShown,
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
    onProblemMessageShown: (Long) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val problem = state.problemMessage
    val problemText = problem?.let { stringResource(it.problem.messageRes()) }
    LaunchedEffect(problem?.token) {
        if (problem != null && problemText != null) {
            snackbarHostState.showSnackbar(
                message = problemText,
                duration = SnackbarDuration.Indefinite,
            )
            onProblemMessageShown(problem.token)
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
                        spinning = state.isChecking,
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
 * 顶部卡片：背景图形每次打开从 MaterialShapes 里随机抽四个，图标和版本号直接叠在上面。
 *
 * 图形都允许越过卡片边界，靠外层的 clip 裁掉，边缘才是切开的弧线而不是整块贴纸。
 * 左上角是当前版本，右上角是应用图标，都贴着卡片内边距。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateHeroCard(
    versionName: String,
    statusText: String,
    spinning: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spin = rememberHeroSpin(spinning)
    val shapes = remember { HERO_SHAPE_POOL.shuffled() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(HERO_CARD_HEIGHT)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(colors.primaryContainer),
    ) {
        HeroShape(
            shape = shapes[0].toShape(),
            size = 236.dp,
            align = Alignment.CenterEnd,
            offsetX = 96.dp,
            offsetY = (-26).dp,
            rotation = 12f,
            spin = { spin.floatValue },
            color = colors.primary.copy(alpha = 0.16f),
        )
        HeroShape(
            shape = shapes[1].toShape(),
            size = 128.dp,
            align = Alignment.TopStart,
            offsetX = (-44).dp,
            offsetY = (-36).dp,
            rotation = -14f,
            spin = { spin.floatValue * -0.75f },
            color = colors.primary.copy(alpha = 0.22f),
        )
        HeroShape(
            shape = shapes[2].toShape(),
            size = 62.dp,
            align = Alignment.BottomStart,
            offsetX = 22.dp,
            offsetY = 24.dp,
            rotation = -18f,
            spin = { spin.floatValue * 1.35f },
            color = colors.tertiary.copy(alpha = 0.42f),
        )
        HeroShape(
            shape = shapes[3].toShape(),
            size = 26.dp,
            align = Alignment.CenterStart,
            offsetX = 92.dp,
            offsetY = 18.dp,
            rotation = 0f,
            spin = { spin.floatValue * -0.5f },
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
            RollingText(
                text = versionName,
                style = MaterialTheme.typography.headlineLargeEmphasized
                    .copy(fontWeight = FontWeight.Bold),
                color = colors.onPrimaryContainer,
                horizontalBias = -1f,
                fallbackMaxLines = 2,
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
        RollingText(
            text = statusText,
            style = MaterialTheme.typography.titleMedium
                .copy(fontWeight = FontWeight.SemiBold),
            color = colors.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp)
                .padding(horizontal = HERO_CONTENT_PADDING),
        )
    }
}

/**
 * 检查时让背景图形缓慢转动：速度因子先缓入到满速再匀速，
 * 结束后用更长的缓出刹停。角度在绘制阶段读取，转动只触发
 * 重绘不触发重组；系统关闭动画时速度恒为 0。
 */
@Composable
private fun rememberHeroSpin(active: Boolean): MutableFloatState {
    val context = LocalContext.current
    val animationsEnabled = remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    val angle = remember { mutableFloatStateOf(0f) }
    val speed = remember { Animatable(0f) }
    LaunchedEffect(active) {
        speed.animateTo(
            targetValue = if (active && animationsEnabled) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (active) {
                    HERO_SPIN_RAMP_UP_MILLIS
                } else {
                    HERO_SPIN_RAMP_DOWN_MILLIS
                },
                easing = if (active) FastOutSlowInEasing else LinearOutSlowInEasing,
            ),
        )
    }
    LaunchedEffect(Unit) {
        var lastFrame = 0L
        while (true) {
            withInfiniteAnimationFrameMillis { frame ->
                val velocity = speed.value
                if (lastFrame != 0L && velocity > 0f) {
                    angle.floatValue +=
                        HERO_SPIN_DEG_PER_SEC * velocity * (frame - lastFrame) / 1000f
                }
                lastFrame = frame
            }
        }
    }
    return angle
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
    spin: () -> Float,
) {
    Box(
        modifier = Modifier
            .align(align)
            .offset(x = offsetX, y = offsetY)
            .size(size)
            .graphicsLayer { rotationZ = rotation + spin() }
            .clip(shape)
            .background(color),
    )
}

private val HERO_CARD_HEIGHT = 200.dp
private val HERO_CONTENT_PADDING = 20.dp
private val HERO_LOGO_SIZE = 40.dp
private val CONTENT_FADE_HEIGHT = 32.dp
private const val HERO_SPIN_DEG_PER_SEC = 40f
private const val HERO_SPIN_RAMP_UP_MILLIS = 1_000
private const val HERO_SPIN_RAMP_DOWN_MILLIS = 1_600

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val HERO_SHAPE_POOL = listOf(
    MaterialShapes.Circle,
    MaterialShapes.Square,
    MaterialShapes.Slanted,
    MaterialShapes.Arch,
    MaterialShapes.Fan,
    MaterialShapes.Arrow,
    MaterialShapes.SemiCircle,
    MaterialShapes.Oval,
    MaterialShapes.Pill,
    MaterialShapes.Triangle,
    MaterialShapes.Diamond,
    MaterialShapes.ClamShell,
    MaterialShapes.Pentagon,
    MaterialShapes.Gem,
    MaterialShapes.Sunny,
    MaterialShapes.VerySunny,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Cookie7Sided,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Cookie12Sided,
    MaterialShapes.Ghostish,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Clover8Leaf,
    MaterialShapes.Burst,
    MaterialShapes.SoftBurst,
    MaterialShapes.Boom,
    MaterialShapes.SoftBoom,
    MaterialShapes.Flower,
    MaterialShapes.Puffy,
    MaterialShapes.PuffyDiamond,
    MaterialShapes.PixelCircle,
    MaterialShapes.PixelTriangle,
    MaterialShapes.Bun,
    MaterialShapes.Heart,
)
