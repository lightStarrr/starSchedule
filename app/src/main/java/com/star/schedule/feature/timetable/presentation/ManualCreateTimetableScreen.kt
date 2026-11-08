package com.star.schedule.feature.timetable.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.CompactPageTopBar
import com.star.schedule.core.designsystem.component.PageContentTopSpacing
import com.star.schedule.core.designsystem.component.SettingChevron
import com.star.schedule.core.designsystem.navigationBarBottomInset
import com.star.schedule.feature.importing.wakeup.presentation.WakeUpCycleShapes
import com.star.schedule.feature.importing.wakeup.presentation.pickDifferentShapeIndex
import com.star.schedule.feature.importing.wakeup.presentation.wakeUpSuccessColors
import com.star.schedule.feature.settings.presentation.SettingsGroup
import com.star.schedule.feature.settings.presentation.SettingsListItem
import com.star.schedule.feature.timetable.presentation.components.StartDatePickerSheet
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ManualCreateTimetableRoute(
    viewModel: ManualCreateTimetableViewModel,
    onBack: () -> Unit,
    onCreated: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ManualCreateTimetableScreen(
        state = state,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onStartDateChange = viewModel::onStartDateChange,
        onCreate = viewModel::create,
        onCreated = onCreated,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ManualCreateTimetableScreen(
    state: ManualCreateUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onStartDateChange: (LocalDate) -> Unit,
    onCreate: () -> Unit,
    onCreated: () -> Unit,
) {
    val navigationBarInset = navigationBarBottomInset()
    var showDatePicker by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    val created = state.createdTimetableId != null

    // 提交编排与 WakeUp 导入页共用同一种时间轴：箭头先从按钮里飞走，
    // 表单行依次飞向按钮收缩消失，按钮再飞到屏幕中心开始变形。
    // 时间轴自身走线性插值，各段的快慢由 segment 之后各自的缓动决定。
    val exitProgress = remember { Animatable(0f) }
    LaunchedEffect(state.creating) {
        if (state.creating) {
            exitProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = EXIT_MILLIS, easing = LinearEasing),
            )
        }
    }
    // 从下往上错峰：离按钮近的行先收进去，上面的行后飞，经过的已经是空位；
    // 反过来上先行的话，飞行中的行会从还停着的下一行身上碾过去，看着是重叠。
    fun rowExit(index: Int): Float = exitProgress.value
        .segment(
            ROW_EXIT_START + (ROW_COUNT - 1 - index) * ROW_STAGGER,
            ROW_EXIT_END + (ROW_COUNT - 1 - index) * ROW_STAGGER,
        )
        .accelerate()
    val nameExit = rowExit(0)
    val dateExit = rowExit(1)
    val arrowExit = exitProgress.value.segment(0f, ARROW_EXIT_END).accelerate()
    val buttonMove = exitProgress.value.segment(BUTTON_MOVE_START, BUTTON_MOVE_END).decelerate()

    // 和 WakeUp 一致：按钮飞到屏幕中心之后才交出形状开始变形。之前这段时间
    // enabled 已经是 false，靠 busy 把底色钉在品牌色上，不能淡成禁用灰。
    val loading = state.creating && exitProgress.value >= LOADING_START

    // 勾弹出来后停一拍再离开页面，让人看清"成了"（同 WakeUp 的 SUCCESS_HOLD_MILLIS）。
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(settled) {
        if (settled) {
            delay(SUCCESS_HOLD_MILLIS)
            onCreated()
        }
    }

    // 按钮位移的终点是屏幕正中心。两个中心都在 root 坐标系里取，只在布局阶段写一次，
    // 后面的位移全部在 offset / graphicsLayer 里读，不触发重组。
    val windowSize = LocalWindowInfo.current.containerSize
    val screenCenter = Offset(windowSize.width / 2f, windowSize.height / 2f)
    var nameRowCenter by remember { mutableStateOf(Offset.Zero) }
    var dateRowCenter by remember { mutableStateOf(Offset.Zero) }
    var buttonCenter by remember { mutableStateOf(Offset.Zero) }

    // 和软件更新页一致：CompactPageTopBar + pinnedScrollBehavior，顶栏始终最小。
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CompactPageTopBar(
                title = stringResource(R.string.new_timetable_manual_title),
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = PageContentTopSpacing + 8.dp),
            ) {
                SettingsGroup {
                    SettingsListItem(
                        title = stringResource(R.string.manual_create_name_label),
                        supporting = state.name.ifBlank {
                            stringResource(R.string.manual_create_name_hint)
                        },
                        onClick = { showNameDialog = true },
                        shapeIndex = 0,
                        shapeCount = 2,
                        leading = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        trailing = { SettingChevron() },
                        modifier = Modifier
                            .onGloballyPositioned { nameRowCenter = it.centerInRoot() }
                            // 顺序不能反：graphicsLayer 在外层，blur 的离屏缓冲才不会
                            // 把飞出原矩形的内容裁掉（同 WakeUp 输入框的处理）。
                            .graphicsLayer {
                                val delta = (buttonCenter - nameRowCenter) * nameExit
                                translationX = delta.x
                                translationY = delta.y
                                val shrink = 1f - ROW_SHRINK * nameExit
                                scaleX = shrink
                                scaleY = shrink
                                alpha = 1f - nameExit
                            }
                            // 模糊半径只能给具体 dp，必须参与重组。
                            .blur(
                                radius = ROW_BLUR * nameExit,
                                edgeTreatment = BlurredEdgeTreatment.Unbounded,
                            ),
                    )
                    SettingsListItem(
                        title = stringResource(R.string.manual_create_date_label),
                        supporting = state.startDate.toString(),
                        onClick = { showDatePicker = true },
                        shapeIndex = 1,
                        shapeCount = 2,
                        leading = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                        trailing = { SettingChevron() },
                        modifier = Modifier
                            .onGloballyPositioned { dateRowCenter = it.centerInRoot() }
                            .graphicsLayer {
                                val delta = (buttonCenter - dateRowCenter) * dateExit
                                translationX = delta.x
                                translationY = delta.y
                                val shrink = 1f - ROW_SHRINK * dateExit
                                scaleX = shrink
                                scaleY = shrink
                                alpha = 1f - dateExit
                            }
                            .blur(
                                radius = ROW_BLUR * dateExit,
                                edgeTreatment = BlurredEdgeTreatment.Unbounded,
                            ),
                    )
                }
            }

            // 与 WakeUp 导入页同款：悬浮在内容之上的饼干形主按钮，先飞到屏幕中心，
            // 再开始变形旋转，成功染绿弹勾，收尾后离开页面。
            CookieButton(
                onClick = onCreate,
                enabled = state.name.isNotBlank() && !state.creating,
                busy = state.creating,
                loading = loading,
                settling = created,
                success = created,
                arrowOffsetX = ARROW_FLY * arrowExit,
                arrowAlpha = 1f - arrowExit,
                onSettled = { settled = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp + navigationBarInset)
                    .onGloballyPositioned { buttonCenter = it.centerInRoot() }
                    // offset 写在 observer 之后：上报的是移动前的位置，终点才是稳定的。
                    .offset {
                        IntOffset(
                            x = ((screenCenter.x - buttonCenter.x) * buttonMove).roundToInt(),
                            y = ((screenCenter.y - buttonCenter.y) * buttonMove).roundToInt(),
                        )
                    },
            )

            // 挡板：提交编排期间盖在内容上，让事件到不了表单行和飞行中的按钮。
            // graphicsLayer 的位移会连触摸区域一起搬，没有它飞行途中点按钮等于点表单行。
            // 只盖内容区——顶栏返回键在 Scaffold 槽位里，加载再久也退得出去。
            if (state.creating) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                )
            }

        }
    }

    if (showNameDialog) {
        NameInputDialog(
            initial = state.name,
            onDismiss = { showNameDialog = false },
            onConfirm = { name ->
                onNameChange(name)
                showNameDialog = false
            },
        )
    }

    if (showDatePicker) {
        StartDatePickerSheet(
            title = stringResource(R.string.manual_create_date_label),
            initialDate = state.startDate,
            onDateChange = onStartDateChange,
            onDismiss = { showDatePicker = false },
            onConfirm = { showDatePicker = false },
        )
    }
}

@Composable
private fun NameInputDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manual_create_name_label)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.manual_create_name_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
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

// ---------------------------------------------------------------------------
// 以下整块复制自 WakeUpImportScreen 的 CookieButton 及其形状工具，
// 保留同样的按下收圆、加载随机变形+旋转、成功染绿弹勾的完整动画。
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CookieButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    settling: Boolean = false,
    success: Boolean = false,
    busy: Boolean = false,
    arrowOffsetX: Dp = 0.dp,
    arrowAlpha: Float = 1f,
    onSettled: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressMorph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }

    val press = remember { Animatable(0f) }
    LaunchedEffect(pressed, loading) {
        if (loading) {
            press.snapTo(0f)
        } else {
            press.animateTo(
                targetValue = if (pressed) 1f else 0f,
                animationSpec = tween(durationMillis = PRESS_MORPH_MILLIS),
            )
        }
    }

    val random = remember { Random.Default }
    val step = remember { Animatable(0f) }
    val turn = remember { Animatable(0f) }
    var fromShape by remember { mutableIntStateOf(COOKIE_SHAPE) }
    var toShape by remember { mutableIntStateOf(COOKIE_SHAPE) }
    var steps by remember { mutableIntStateOf(0) }
    var settleMorph by remember { mutableStateOf<Morph?>(null) }
    var becomingCookie by remember { mutableStateOf(false) }
    val check = remember { Animatable(0f) }

    val settleNow by rememberUpdatedState(settling)
    val successNow by rememberUpdatedState(success)
    val notifySettled by rememberUpdatedState(onSettled)

    LaunchedEffect(loading) {
        if (!loading) return@LaunchedEffect
        fromShape = COOKIE_SHAPE
        toShape = COOKIE_SHAPE
        steps = 0
        step.snapTo(0f)
        turn.snapTo(0f)
        settleMorph = null
        becomingCookie = false
        check.snapTo(0f)

        delay(HANDOFF_HOLD_MILLIS)
        while (true) {
            toShape = pickDifferentShapeIndex(fromShape, WakeUpCycleShapes.size, random)
            coroutineScope {
                launch { step.animateTo(targetValue = 1f, animationSpec = MORPH_SPRING) }
                launch { turn.animateTo(targetValue = ROTATE_PER_STEP, animationSpec = TURN_SPRING) }
            }
            if (settleNow) break
            fromShape = toShape
            step.snapTo(0f)
            turn.snapTo(0f)
            steps++
            delay(MORPH_GAP_MILLIS)
            if (settleNow) break
        }

        val leaving = if (step.value >= 0.5f) toShape else fromShape
        settleMorph = Morph(cookieShapeAt(leaving), MaterialShapes.Cookie9Sided)
        step.snapTo(0f)
        becomingCookie = true
        coroutineScope {
            launch { step.animateTo(targetValue = 1f, animationSpec = SETTLE_SPRING) }
            launch {
                turn.animateTo(
                    targetValue = turn.value + ROTATE_PER_STEP,
                    animationSpec = SETTLE_TURN_SPRING,
                )
            }
        }
        step.snapTo(1f)
        turn.snapTo(
            (turn.value / COOKIE_SYMMETRY_DEGREES).roundToInt() * COOKIE_SYMMETRY_DEGREES,
        )
        if (successNow) check.animateTo(targetValue = 1f, animationSpec = CHECK_SPRING)
        notifySettled()
    }

    val successColors = wakeUpSuccessColors()
    // 按钮飞向屏幕中心的那段（busy）enabled 已是 false 但还没开始变形，
    // 底色必须钉在品牌色上，掉成灰色整段出场就脏了。
    val tinted = enabled || busy || loading
    val successTint = becomingCookie && success
    val colorMillis = if (successTint) SUCCESS_COLOR_MILLIS else ENABLE_COLOR_MILLIS
    val containerColor by animateColorAsState(
        targetValue = when {
            successTint -> successColors.container
            tinted -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        },
        animationSpec = tween(durationMillis = colorMillis),
        label = "cookieContainerColor",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            successTint -> successColors.content
            tinted -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        },
        animationSpec = tween(durationMillis = colorMillis),
        label = "cookieContentColor",
    )

    val stepMorph = remember(fromShape, toShape) {
        Morph(cookieShapeAt(fromShape), cookieShapeAt(toShape))
    }
    val morph = if (loading) settleMorph ?: stepMorph else pressMorph
    val morphProgress = if (loading) step.value.coerceIn(0f, 1f) else press.value
    val rotation = if (loading) (steps * ROTATE_PER_STEP) % 360f + turn.value else 0f
    val shape = remember(morph, morphProgress, rotation) {
        CookieShape(morph, morphProgress, rotation)
    }

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        enabled = enabled && !loading,
        modifier = modifier
            .requiredSize(BUTTON_SIZE)
            .clip(shape),
        shapes = IconButtonDefaults.shapes(shape = shape, pressedShape = shape),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
    ) {
        Box(
            modifier = Modifier.requiredSize(BUTTON_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(CHECK_SIZE)
                    .graphicsLayer {
                        val entered = check.value
                        alpha = entered.coerceIn(0f, 1f)
                        val scale = CHECK_START_SCALE + (1f - CHECK_START_SCALE) * entered
                        scaleX = scale
                        scaleY = scale
                    },
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = stringResource(R.string.manual_create_create),
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer {
                        translationX = arrowOffsetX.toPx()
                        alpha = arrowAlpha * (1f - check.value.coerceIn(0f, 1f))
                    },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun Morph.toFittedPath(progress: Float, size: Size, out: Path = Path()): Path {
    out.reset()
    toPath(progress, out)
    out.transform(Matrix().apply { scale(size.width, size.height) })
    return out
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class CookieShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotationDegrees: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = morph.toFittedPath(progress, size)
        if (rotationDegrees != 0f) {
            path.transform(
                Matrix().apply {
                    translate(size.width / 2f, size.height / 2f)
                    rotateZ(rotationDegrees)
                    translate(-size.width / 2f, -size.height / 2f)
                },
            )
        }
        return Outline.Generic(path)
    }
}

private const val PRESS_MORPH_MILLIS = 220
private const val ENABLE_COLOR_MILLIS = 200
private val BUTTON_SIZE = 92.dp

/** 退出编排总时长：与 WakeUp 的 SUBMIT_MILLIS 一致，ViewModel 等它播完才落库。 */
private const val EXIT_MILLIS = 900

/** 表单行开始退场的时刻：先给箭头一拍离场时间，行再出发（同 WakeUp 的 fieldExit）。 */
private const val ROW_EXIT_START = 0.16f

/** 行退场在时间轴上的终点份额；行与行用 [ROW_STAGGER] 错峰。 */
private const val ROW_EXIT_END = 0.58f
private const val ROW_COUNT = 2
private const val ROW_STAGGER = 0.08f

/** 行飞向按钮时缩小的比例量（同 WakeUp 输入框的 FIELD_SHRINK，收至约一半大小）。 */
private const val ROW_SHRINK = 0.55f

/** 行飞向按钮途中糊到的最大半径（同 WakeUp 的 FIELD_BLUR）。 */
private val ROW_BLUR = 18.dp

/** 箭头在时间轴上的终点份额：点击后立刻出发，给操作一个即时反馈。 */
private const val ARROW_EXIT_END = 0.20f

/** 按钮飞向屏幕中心的时间区间，减速停稳之后才交出形状开始变形。 */
private const val BUTTON_MOVE_START = 0.50f
private const val BUTTON_MOVE_END = 0.86f

/** 时间轴上「按钮到位、开始变形」的位置（同 WakeUp 的 LOADING_START）。 */
private const val LOADING_START = 0.86f

/** 勾弹出来之后停留的时长（同 WakeUp 的 SUCCESS_HOLD_MILLIS）。 */
private const val SUCCESS_HOLD_MILLIS = 420L

/** 箭头在按钮内部向右滑出的距离，滑出边界被形状 clip 裁掉。 */
private val ARROW_FLY = 56.dp

private const val HANDOFF_HOLD_MILLIS = 160L
private const val COOKIE_SYMMETRY_DEGREES = 40f
private const val ROTATE_PER_STEP = 2f * COOKIE_SYMMETRY_DEGREES
private const val MORPH_GAP_MILLIS = 260L
private const val COOKIE_SHAPE = -1

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun cookieShapeAt(index: Int) =
    if (index == COOKIE_SHAPE) MaterialShapes.Cookie9Sided else WakeUpCycleShapes[index]

private val MORPH_SPRING = spring<Float>(
    dampingRatio = 0.6f,
    stiffness = 110f,
    visibilityThreshold = 0.1f,
)

private val TURN_SPRING = spring<Float>(
    dampingRatio = 1f,
    stiffness = 150f,
    visibilityThreshold = 0.5f,
)

private val SETTLE_SPRING = spring<Float>(
    dampingRatio = 0.85f,
    stiffness = 190f,
    visibilityThreshold = 0.01f,
)

private val SETTLE_TURN_SPRING = spring<Float>(
    dampingRatio = 1f,
    stiffness = 260f,
    visibilityThreshold = 0.5f,
)

private const val SUCCESS_COLOR_MILLIS = 300
private val CHECK_SIZE = 46.dp
private const val CHECK_START_SCALE = 0.6f

private val CHECK_SPRING = spring<Float>(
    dampingRatio = 0.5f,
    stiffness = 420f,
    visibilityThreshold = 0.01f,
)

/**
 * 把主时间轴上的 `[start, end]` 区间映射成 0..1，区间外截断成 0 / 1。
 *
 * 这样多条动作共享一个 `Animatable`，加减动作只改区间，不用重排时长总和。
 */
private fun Float.segment(start: Float, end: Float): Float =
    ((this - start) / (end - start)).coerceIn(0f, 1f)

/** 离场用加速曲线：起步慢、越走越快，像是被"抽走"。 */
private fun Float.accelerate(): Float = FastOutLinearInEasing.transform(this)

/** 位移用减速曲线：起步快、稳稳停住。 */
private fun Float.decelerate(): Float = FastOutSlowInEasing.transform(this)

/** 控件在 root 坐标系里的中心点。 */
private fun LayoutCoordinates.centerInRoot(): Offset {
    val origin = positionInRoot()
    return Offset(origin.x + size.width / 2f, origin.y + size.height / 2f)
}
