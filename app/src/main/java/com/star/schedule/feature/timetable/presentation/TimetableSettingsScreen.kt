package com.star.schedule.feature.timetable.presentation

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.PageTopBar
import com.star.schedule.core.designsystem.CourseBlockCornerRadius
import com.star.schedule.core.designsystem.navigationBarBottomInset
import com.star.schedule.platform.display.rememberScreenCornerRadius
import com.star.schedule.feature.settings.domain.TimetablePreview
import com.star.schedule.feature.settings.presentation.SettingsGroup
import com.star.schedule.feature.settings.presentation.SettingsListItem

@Composable
fun TimetableSettingsRoute(
    viewModel: TimetableSettingsViewModel,
    onBack: () -> Unit,
    onOpenRowHeight: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TimetableSettingsScreen(
        state = state,
        onBack = onBack,
        onApply = viewModel::apply,
        onRowHeightClick = onOpenRowHeight,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TimetableSettingsScreen(
    state: TimetableSettingsUiState,
    onBack: () -> Unit,
    onApply: (Long) -> Unit,
    onRowHeightClick: () -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navigationBarInset = navigationBarBottomInset()
    // pager 状态提到这一层：下面的设置列表要跟着「居中那张」走，而不是已经应用的那张。
    val pagerState = rememberPagerState(pageCount = { state.previews.size + 1 })
    val previewed = state.previews.getOrNull(pagerState.currentPage)
    // 长按卡片弹出的操作菜单：只记 id + 卡片在屏幕上的中心，按钮画在根布局上，
    // 这样点卡片外面任何地方都能关掉。
    var actionMenu by remember { mutableStateOf<TimetableActionMenu?>(null) }
    // 单独记展开状态：关闭时不清空 actionMenu，淡出动画期间还得知道按钮画在哪。
    var actionMenuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<TimetablePreview?>(null) }
    var deleting by remember { mutableStateOf<TimetablePreview?>(null) }

    // 翻页、点卡片外面都要能把菜单收起来，所以菜单画在整个页面之上。
    LaunchedEffect(pagerState.currentPage) { actionMenuOpen = false }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        // 内容滑到手势条下面，手势条后面才不会留一条纯背景的带子。
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageTopBar(
                title = stringResource(R.string.timetable_settings_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                val configuration = LocalConfiguration.current
                // 真实的高宽比：竖屏 >1，横屏 <1。不做下限夹取，横屏时卡片才会跟着变扁变宽。
                val screenAspect = (
                    configuration.screenHeightDp.toFloat() /
                        configuration.screenWidthDp.toFloat()
                    ).coerceAtLeast(0.2f)
                // 顶栏折叠会让 innerPadding 每帧变化，maxHeight 也就跟着变；
                // 卡片尺寸只在进入页面（顶栏展开）时量一次，滚动时不再跟着变，否则会一直缩放。
                val viewportHeight = remember(
                    configuration.screenWidthDp,
                    configuration.screenHeightDp,
                ) { maxHeight }
                // 尺寸受两个方向同时约束：不能超出剩余高度，也不能横向超出窗口。
                // 先由可用高度反推宽度，再用窗口宽度收一次，最后按比例得到高度。
                val availableHeight = (viewportHeight - PreviewCardBelowSpace)
                    .coerceAtLeast(0.dp)
                val cardMaxWidth = (availableHeight / screenAspect)
                    .coerceAtMost(maxWidth * PreviewCardWidthMaxFraction)
                    .coerceIn(PreviewCardMinWidth, PreviewCardMaxWidth)
                val cardMaxHeight = cardMaxWidth * screenAspect
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    TimetablePreviewPager(
                        pagerState = pagerState,
                        previews = state.previews,
                        currentId = state.currentId,
                        // 直接传 state：折叠进度在 layout / draw 阶段读，避免每帧重组卡片内容。
                        topBarState = scrollBehavior.state,
                        maxCardWidth = cardMaxWidth,
                        maxCardHeight = cardMaxWidth * screenAspect,
                        onApply = onApply,
                        scrimmedTimetableId = if (actionMenuOpen) actionMenu?.timetableId else null,
                        onLongPress = { id, bounds ->
                            actionMenu = TimetableActionMenu(id, bounds)
                            actionMenuOpen = true
                        },
                    )
                if (previewed != null) {
                    Spacer(Modifier.height(24.dp))
                    SettingsGroup {
                        SettingsListItem(
                            title = stringResource(R.string.timetable_settings_row_height),
                            supporting = stringResource(R.string.timetable_settings_row_height_support),
                            onClick = onRowHeightClick,
                            shapeIndex = 0,
                            shapeCount = 1,
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = stringResource(
                                            R.string.row_height_value,
                                            previewed.rowHeight
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                    )
                                }
                            },
                        )
                    }
                }
                    Spacer(Modifier.height(24.dp + navigationBarInset))
                }
            }
        }
    }

    // 外层只负责「内容什么时候被移除」：进场不淡入（按钮各自做），
    // 退场保留 OUT 时长，否则一关就直接销毁，按钮的缩小动画来不及播。
    AnimatedVisibility(
        visible = actionMenuOpen,
        enter = fadeIn(tween(0)),
        exit = fadeOut(tween(TimetableActionMenuFadeOutMillis)),
    ) {
        actionMenu?.let { menu ->
            TimetableCardActionOverlay(
                bounds = menu.bounds,
                visible = actionMenuOpen,
                onRename = {
                    renaming = state.previews.firstOrNull { it.id == menu.timetableId }
                    actionMenuOpen = false
                },
                onDelete = {
                    deleting = state.previews.firstOrNull { it.id == menu.timetableId }
                    actionMenuOpen = false
                },
                onDismiss = { actionMenuOpen = false },
            )
        }
    }
    }

    renaming?.let { target ->
        RenameTimetableDialog(
            initialName = target.name,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                onRename(target.id, name)
                renaming = null
            },
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.timetable_delete_title)) },
            text = {
                Text(stringResource(R.string.timetable_delete_message, target.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(target.id)
                        deleting = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * 顶部预览：左右滑动看不同课表，最右一张是「新建」占位。
 * 滑动只改变预览，真正切换要点下面的「应用」，避免顺手一滑就把课表换掉。
 */
@Composable
private fun TimetablePreviewPager(
    pagerState: PagerState,
    previews: List<TimetablePreview>,
    currentId: Long?,
    topBarState: TopAppBarState,
    maxCardWidth: Dp,
    maxCardHeight: Dp,
    onApply: (Long) -> Unit,
    scrimmedTimetableId: Long?,
    onLongPress: (Long, Rect) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 折叠进度不直接照着滚，而是做指数平滑（一阶低通，Unity 里叫 SmoothDamp）：
    // 每帧按 dt 把当前值往目标靠一定比例。目标一直在变也不会有问题——
    // 弹簧那种「每次重设目标就把速度清零」的做法，慢速滚动时会一直追不上，看着像卡住。
    // Animatable 的值是 State，下面都在测量/绘制阶段读，不会引起重组。
    val progress = remember { Animatable(topBarState.collapsedFraction) }
    LaunchedEffect(topBarState) {
        var lastFrameNanos = 0L
        while (isActive) {
            // 停下来时不占帧：等目标真的变了再开始追。
            snapshotFlow { topBarState.collapsedFraction }.first { it != progress.value }
            lastFrameNanos = 0L
            while (isActive) {
                // 每帧重新读目标，而不是「一变就重启动画」——后者会把速度清零，慢速拖动时几乎不动。
                val frameNanos = withFrameNanos { it }
                val deltaSeconds = if (lastFrameNanos == 0L) {
                    PreviewFollowFrameFallback
                } else {
                    ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                }
                lastFrameNanos = frameNanos
                val target = topBarState.collapsedFraction
                val current = progress.value
                val diff = target - current
                if (abs(diff) <= PreviewFollowEpsilon) {
                    progress.snapTo(target)
                    break
                }
                val alpha = 1f - exp(-deltaSeconds / PreviewFollowTimeConstant)
                progress.snapTo(current + diff * alpha)
            }
        }
    }
    // 记住一个稳定回调，页面拿到的是同一个函数实例，折叠时上层重组才能跳过页面内容。
    val onSelectPage: (Int) -> Unit = remember(pagerState, scope) {
        { page -> scope.launch { pagerState.animateScrollToPage(page) } }
    }

    // 当前课表变化（首次加载、点应用之后）时把预览滚到对应位置。
    LaunchedEffect(currentId, previews.size) {
        val index = previews.indexOfFirst { it.id == currentId }
        if (index >= 0 && pagerState.currentPage != index) {
            pagerState.animateScrollToPage(index)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 左右各留 (宽 - 卡片宽)/2，居中的那一页才落在屏幕正中，两侧各露出半张相邻课表。
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // 卡片尺寸由上层按可用高度算好传进来；这里只要屏幕宽度算圆角比例。
            val configuration = LocalConfiguration.current
            // 圆角跟着设备屏幕走：屏幕圆角多大，就按「卡片宽 / 屏幕宽」等比缩到卡片上；
            // 取不到（模拟器、老系统）时退回 24dp。
            val screenCornerRadius = rememberScreenCornerRadius()
            val cardCorner = if (screenCornerRadius > 0.dp) {
                (screenCornerRadius * (maxCardWidth.value / configuration.screenWidthDp))
                    .coerceIn(PreviewCardMinCorner, PreviewCardMaxCorner)
            } else {
                PreviewCardFallbackCorner
            }
            // pageSize 与 contentPadding 传的是「会自己读折叠进度」的对象，
            // pager 在测量时才取值，所以折叠过程中这一层不会重组。
            val pageSize = remember(progress, maxCardWidth) {
                CollapsingPageSize(progress, maxCardWidth, PreviewCardCollapsedWidth)
            }
            val contentPadding = remember(progress, maxWidth, maxCardWidth) {
                CollapsingContentPadding(
                    progress = progress,
                    viewportWidth = maxWidth,
                    expandedWidth = maxCardWidth,
                    collapsedWidth = PreviewCardCollapsedWidth,
                )
            }
            HorizontalPager(
                state = pagerState,
                contentPadding = contentPadding,
                pageSpacing = 12.dp,
                pageSize = pageSize,
            ) { page ->
                val preview = previews.getOrNull(page)
                TimetablePreviewPage(
                    page = page,
                    preview = preview,
                    applied = preview != null && preview.id == currentId,
                    progress = progress,
                    maxCardWidth = maxCardWidth,
                    maxCardHeight = maxCardHeight,
                    corner = cardCorner,
                    scrimmed = scrimmedTimetableId != null && preview?.id == scrimmedTimetableId,
                    onSelect = onSelectPage,
                    onLongPress = onLongPress,
                )
            }
        }

        // 用 currentPage 而不是 settledPage：滑动过半按钮就该跟着变，settledPage 要等动画完全停下。
        val previewed = previews.getOrNull(pagerState.currentPage)
        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Button(
                onClick = { previewed?.let { onApply(it.id) } },
                shapes = ButtonDefaults.shapes(),
                enabled = previewed != null && previewed.id != currentId,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.timetable_settings_apply))
            }
        }
    }
}

/**
 * 一页预览。参数全部取稳定值（[TimetablePreview] 标了 @Immutable、[TopAppBarState] 是 @Stable），
 * 所以顶栏折叠导致上层重组时，这里会被跳过；缩放发生在 layout / draw 阶段。
 */
@Composable
private fun TimetablePreviewPage(
    page: Int,
    preview: TimetablePreview?,
    applied: Boolean,
    progress: Animatable<Float, AnimationVector1D>,
    maxCardWidth: Dp,
    maxCardHeight: Dp,
    corner: Dp,
    scrimmed: Boolean,
    onSelect: (Int) -> Unit,
    onLongPress: (Long, Rect) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.height(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) {
                Text(
                    text = preview.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (applied) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (preview == null) {
            NewTimetableCard(
                progress = progress,
                maxWidth = maxCardWidth,
                maxHeight = maxCardHeight,
                corner = corner,
            )
        } else {
            TimetablePreviewCard(
                preview = preview,
                applied = applied,
                progress = progress,
                maxWidth = maxCardWidth,
                maxHeight = maxCardHeight,
                corner = corner,
                scrimmed = scrimmed,
                onClick = { onSelect(page) },
                onLongPress = { bounds -> onLongPress(preview.id, bounds) },
            )
        }
    }
}

/**
 * 折叠缩放全部走 layout + draw：
 * - `layout` 里读 [TopAppBarState.collapsedFraction]，只让测量阶段失效；
 * - 子节点始终按最大尺寸测量，约束不变，所以缩小时不会重新测量里面的 Canvas；
 * - 真正的缩放交给 `graphicsLayer`，只触发重绘。
 *
 * 三者加起来，滚动过程中卡片内容既不会重组、也不会重测。
 */
private fun Modifier.previewCardSize(
    progress: Animatable<Float, AnimationVector1D>,
    maxWidth: Dp,
    maxHeight: Dp,
): Modifier = this
    .layout { measurable, _ ->
        val fraction = progress.value.coerceIn(0f, 1f)
        val width = lerp(maxWidth, PreviewCardCollapsedWidth, fraction).roundToPx()
        // 高度始终由宽度按同一比例推出来：宽高各自插值会让卡片越缩越扁。
        val height = (width.toFloat() * maxHeight.value / maxWidth.value).roundToInt()
        val placeable = measurable.measure(
            Constraints.fixed(maxWidth.roundToPx(), maxHeight.roundToPx()),
        )
        layout(width, height) { placeable.place(0, 0) }
    }
    .graphicsLayer {
        val fraction = progress.value.coerceIn(0f, 1f)
        val scaledWidth = lerp(maxWidth, PreviewCardCollapsedWidth, fraction).toPx()
        scaleX = scaledWidth / maxWidth.toPx()
        scaleY = scaledWidth / maxWidth.toPx()
        transformOrigin = TransformOrigin(0f, 0f)
    }

@Composable
private fun TimetablePreviewCard(
    preview: TimetablePreview,
    applied: Boolean,
    progress: Animatable<Float, AnimationVector1D>,
    maxWidth: Dp,
    maxHeight: Dp,
    corner: Dp,
    scrimmed: Boolean,
    onClick: () -> Unit,
    onLongPress: (Rect) -> Unit,
) {
    // 长按要知道卡片在屏幕上的位置，菜单按钮才好画在卡片正中。
    var boundsInRoot by remember { mutableStateOf(Rect.Zero) }
    Card(
        modifier = Modifier
            .previewCardSize(progress, maxWidth, maxHeight)
            .onGloballyPositioned { boundsInRoot = it.boundsInRoot() }
            // clip 必须排在 clickable 前面：水波纹画在 clickable 那一层，
            // 顺序反了就是矩形的按压反馈盖在圆角卡片上。
            .clip(RoundedCornerShape(corner))
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = { onLongPress(boundsInRoot) },
            ),
        shape = RoundedCornerShape(corner),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright,
        ),
        // 当前课表用 primary 描边；卡片本身用 Card，点击、水波纹、语义都由组件自带。
        border = if (applied) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        // 长按菜单的遮罩就画在卡片里，圆角和裁剪由 Card 的 shape 负责。
        val scrimAlpha by animateFloatAsState(
            targetValue = if (scrimmed) TimetableActionScrimAlpha else 0f,
            animationSpec = tween(
                if (scrimmed) TimetableActionMenuFadeInMillis else TimetableActionMenuFadeOutMillis,
            ),
            label = "timetableCardScrim",
        )
        Box(modifier = Modifier.fillMaxSize()) {
            TimetableThumbnail(
                preview = preview,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(PreviewCardContentInset),
            )
            if (scrimAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha)),
                )
            }
        }
    }
}

/** 缩略网格：只画课程块，不放文字，和系统「壁纸与风格」里的预览卡同一路数。 */
@Composable
private fun TimetableThumbnail(
    preview: TimetablePreview,
    modifier: Modifier = Modifier,
) {
    val columns = if (preview.hasWeekendCourses) 7 else 5
    val blockColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)

    Canvas(modifier = modifier) {
        val cellWidth = size.width / columns
        val cellHeight = size.height / THUMBNAIL_ROWS
        val gap = 1.dp.toPx()
        // 方块圆角与首页课表一致，不再按卡片外圆角做同心推导——
        // 窄列上同心算出来接近胶囊，和真实课表对不上。
        val blockCorner = CourseBlockCornerRadius.toPx()
        preview.blocks.forEach { block ->
            if (block.dayOfWeek > columns) return@forEach
            val top = (block.startPeriod - 1) * cellHeight
            if (top >= size.height) return@forEach
            val blockWidth = (cellWidth - gap * 2).coerceAtLeast(1f)
            val blockHeight = (cellHeight * block.periodCount - gap * 2).coerceAtLeast(1f)
            drawRoundRect(
                color = blockColor,
                topLeft = Offset((block.dayOfWeek - 1) * cellWidth + gap, top + gap),
                size = Size(blockWidth, blockHeight),
                cornerRadius = CornerRadius(
                    blockCorner.coerceAtMost(minOf(blockWidth, blockHeight) / 2f),
                ),
            )
        }
    }
}

@Composable
private fun NewTimetableCard(
    progress: Animatable<Float, AnimationVector1D>,
    maxWidth: Dp,
    maxHeight: Dp,
    corner: Dp,
) {
    Card(
        modifier = Modifier.previewCardSize(progress, maxWidth, maxHeight),
        shape = RoundedCornerShape(corner),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // TODO(设计): 新建课表的流程还没定，先只放占位，不挂点击。
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.timetable_settings_new),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                // 卡片折叠时整块内容会被 graphicsLayer 缩小，这里按 1/缩放 抵消，
                // 让加号在屏幕上始终是同一个大小，不会越缩越小。
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer {
                        val fraction = progress.value.coerceIn(0f, 1f)
                        val cardScale =
                            lerp(maxWidth, PreviewCardCollapsedWidth, fraction) / maxWidth
                        scaleX = 1f / cardScale
                        scaleY = 1f / cardScale
                    },
            )
        }
    }
}


/**
 * 页宽随顶栏折叠变化，但取值发生在测量阶段（[PageSize.calculateMainAxisPageSize] 在 measure 里调用），
 * 所以 pager 不会因为折叠而重组。
 */
private class CollapsingPageSize(
    private val progress: Animatable<Float, AnimationVector1D>,
    private val expandedWidth: Dp,
    private val collapsedWidth: Dp,
) : PageSize {
    override fun Density.calculateMainAxisPageSize(
        availableSpace: Int,
        pageSpacing: Int,
    ): Int = lerp(expandedWidth, collapsedWidth, progress.value.coerceIn(0f, 1f)).roundToPx()
}

/**
 * 左右留白 = (视口宽 - 页宽) / 2，保证居中的一页始终在屏幕正中。
 * 同样把折叠进度的读取放到了测量阶段。
 */
private class CollapsingContentPadding(
    private val progress: Animatable<Float, AnimationVector1D>,
    private val viewportWidth: Dp,
    private val expandedWidth: Dp,
    private val collapsedWidth: Dp,
) : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        ((viewportWidth - currentWidth()) / 2).coerceAtLeast(16.dp)

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding(): Dp = 0.dp

    override fun calculateBottomPadding(): Dp = 0.dp

    private fun currentWidth(): Dp =
        lerp(expandedWidth, collapsedWidth, progress.value.coerceIn(0f, 1f))
}

// 卡片圆角以「设备屏幕圆角」为准，这三个值负责缩放后的收口与取不到时的兜底。
private val PreviewCardFallbackCorner = 24.dp
private val PreviewCardMinCorner = 12.dp
private val PreviewCardMaxCorner = 36.dp

// 卡片内容区到卡片边缘的间距。留白加大一点，方块的小圆角才不会贴着卡片边缘显得突兀。
private val PreviewCardContentInset = 12.dp

// 预览卡占可用宽度的比例，高度按手机常见比例 9:19.5 推出来。
// 宽度主要由高度倒推，这里只做「别超过屏宽太多」的安全上限。
private const val PreviewCardWidthMaxFraction = 0.8f
private val PreviewCardMaxWidth = 280.dp

// 折叠跟随的时间常数（秒）：越小越贴手，越大越飘。0.08 大约是 5 帧的迟滞。
private const val PreviewFollowTimeConstant = 0.08f
private const val PreviewFollowEpsilon = 0.001f

// 第一帧没有上一帧可比，按 60fps 估一个 dt。
private const val PreviewFollowFrameFallback = 1f / 60f

// 卡片下方固定要占的高度：标题 20 + 标题与卡间距 8 + 卡与按钮间距 16 + 按钮 40 + 与设置间距 24，
// 再加上想露出来的行高（35dp）。
private val PreviewCardRowHeightPeek = 35.dp
private val PreviewCardBelowSpace =
    20.dp + 8.dp + 16.dp + 40.dp + 24.dp + PreviewCardRowHeightPeek

/** 极端窗口（横屏、分屏）下卡片也不至于被压没或糊成一条线。 */
private val PreviewCardMinWidth = 160.dp

// 顶栏完全收起后只定宽度，高度按 PreviewCardAspect 推出来（128dp 宽 ≈ 277dp 高）。
private val PreviewCardCollapsedWidth = 128.dp
private const val THUMBNAIL_ROWS = 12


/** 长按卡片后浮在页面上的操作菜单，位置跟着卡片走。 */
private data class TimetableActionMenu(
    val timetableId: Long,
    val bounds: Rect,
)

private const val TimetableActionScrimAlpha = 0.12f
private const val TimetableActionMenuFadeInMillis = 180
private const val TimetableActionMenuFadeOutMillis = 140

private const val TimetableActionButtonInitialScale = 0.6f
private const val TimetableActionButtonStaggerMillis = 60

private val TimetableActionButtonMinSize = 48.dp
private val TimetableActionButtonMaxSize = 88.dp
private val TimetableActionButtonGap = 12.dp

/**
 * 卡片中心浮出两个按钮：编辑名称 / 删除。
 *
 * 整层铺满屏幕接管点击，点按钮以外任何地方都收起；按钮尺寸按卡片短边收敛，
 * 卡片折叠变小的时候不会顶出卡片外。
 */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun TimetableCardActionOverlay(
    bounds: Rect,
    visible: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    // 宽高比决定排布：卡片宽于高就横排，否则竖排。
    val horizontal = bounds.width >= bounds.height
    // 大卡片上按短边的 30% 撑开，小卡片上限收到 64dp，别顶出卡片外。
    val buttonSize = with(density) {
        (minOf(bounds.width, bounds.height) * 0.3f).toDp()
    }.coerceIn(TimetableActionButtonMinSize, TimetableActionButtonMaxSize)
    val stackWidth =
        if (horizontal) buttonSize * 2 + TimetableActionButtonGap else buttonSize
    val stackHeight =
        if (horizontal) buttonSize else buttonSize * 2 + TimetableActionButtonGap

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Box(
            modifier = Modifier.offset {
                IntOffset(
                    (bounds.center.x - with(density) { stackWidth.toPx() } / 2f).roundToInt(),
                    (bounds.center.y - with(density) { stackHeight.toPx() } / 2f).roundToInt(),
                )
            },
        ) {
            // 两个按钮各自做淡入 + 放大，第二个晚一点点，出场时同样分开缩回去。
            val editButton: @Composable () -> Unit = {
                AnimatedCardActionButton(visible = visible, delayMillis = 0) {
                    TimetableActionButton(
                        label = stringResource(R.string.timetable_action_edit_name),
                        icon = Icons.Rounded.Edit,
                        shape = MaterialShapes.Cookie4Sided.toShape(),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        size = buttonSize,
                        onClick = onRename,
                    )
                }
            }
            val deleteButton: @Composable () -> Unit = {
                AnimatedCardActionButton(
                    visible = visible,
                    delayMillis = TimetableActionButtonStaggerMillis,
                ) {
                    TimetableActionButton(
                        label = stringResource(R.string.action_delete),
                        icon = Icons.Rounded.Delete,
                        shape = MaterialShapes.Ghostish.toShape(),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        size = buttonSize,
                        onClick = onDelete,
                    )
                }
            }
            if (horizontal) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(TimetableActionButtonGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    editButton()
                    deleteButton()
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(TimetableActionButtonGap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    editButton()
                    deleteButton()
                }
            }
        }
    }
}

@Composable
private fun TimetableActionButton(
    label: String,
    icon: ImageVector,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    size: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(size * 0.45f),
        )
    }
}

@Composable
private fun RenameTimetableDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timetable_action_edit_name)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.timetable_edit_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}


/**
 * 单个按钮的出场动画。
 *
 * 用 [MutableTransitionState] 从 false 起步：直接给 AnimatedVisibility 传 visible = true 时，
 * 首次组合就已经是可见状态，进场动画会被整个跳过。
 */
@Composable
private fun AnimatedCardActionButton(
    visible: Boolean,
    delayMillis: Int = 0,
    content: @Composable () -> Unit,
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible
    AnimatedVisibility(
        visibleState = transitionState,
        enter = fadeIn(tween(TimetableActionMenuFadeInMillis, delayMillis = delayMillis)) +
            scaleIn(
                initialScale = TimetableActionButtonInitialScale,
                animationSpec = tween(TimetableActionMenuFadeInMillis, delayMillis = delayMillis),
            ),
        exit = fadeOut(tween(TimetableActionMenuFadeOutMillis)) +
            scaleOut(
                targetScale = TimetableActionButtonInitialScale,
                animationSpec = tween(TimetableActionMenuFadeOutMillis),
            ),
    ) {
        content()
    }
}
