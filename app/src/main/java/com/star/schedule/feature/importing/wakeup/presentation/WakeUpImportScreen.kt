package com.star.schedule.feature.importing.wakeup.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.CircleBackButton
import kotlin.math.roundToInt

@Composable
fun WakeUpImportRoute(
    viewModel: WakeUpImportViewModel,
    onBack: () -> Unit,
    onImported: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.phase) {
        if (state.phase is WakeUpImportPhase.Finished) onImported()
    }

    WakeUpImportScreen(
        state = state,
        onBack = onBack,
        onInputChange = viewModel::onInputChange,
        onImport = viewModel::import,
    )
}

/**
 * WakeUp 导入：顶栏、输入框、按钮，三者之外暂时不放任何内容。
 *
 * 视觉与文案都还在重做中，这里只保留结构与交互入口，不做加载态、错误态和提示语。
 *
 * 整页套一层 WakeUp 自己的珊瑚粉配色，只影响这一页。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WakeUpImportScreen(
    state: WakeUpImportUiState,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onImport: () -> Unit,
) {
    // 用 MaterialExpressiveTheme 而不是 MaterialTheme：后者会把 motionScheme 重置成默认，
    // 这里的形状/颜色动画虽然是显式 tween，但没必要让整页的运动规格跟主题脱节。
    MaterialExpressiveTheme(colorScheme = wakeUpImportColorScheme()) {
        WakeUpImportContent(
            state = state,
            onBack = onBack,
            onInputChange = onInputChange,
            onImport = onImport,
        )
    }
}

@Composable
private fun WakeUpImportContent(
    state: WakeUpImportUiState,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onImport: () -> Unit,
) {
    val submitting = state.phase is WakeUpImportPhase.Importing

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 一条主时间轴驱动全部动作，每个元素只从中截一段，节奏要调就只动下面这几组区间。
    val timeline = remember { Animatable(0f) }
    LaunchedEffect(submitting) {
        if (submitting) {
            // 先收键盘再动：输入法展开时可用高度少一截，布局是压缩过的，
            // 这时候起跳，飞出去的位置和落点都不对。
            focusManager.clearFocus()
            keyboardController?.hide()
        }
        timeline.animateTo(
            targetValue = if (submitting) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (submitting) SUBMIT_MILLIS else SUBMIT_MILLIS / 2,
                // 主时间轴必须线性，各段自己加缓动，否则每段都会被再压一次。
                easing = LinearEasing,
            ),
        )
    }

    val elapsed = timeline.value
    val arrowExit = elapsed.segment(0.00f, 0.20f).accelerate()
    val hintExit = elapsed.segment(0.08f, 0.30f).accelerate()
    val fieldExit = elapsed.segment(0.16f, 0.56f).accelerate()
    val buttonMove = elapsed.segment(0.52f, 0.92f).decelerate()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // 底部只认一个内边距：键盘收起时是导航栏高度，弹出时是键盘高度。
                // 分开写 imePadding() + 导航栏内边距的话，两者切换是离散的，动画中途会跳一下。
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                ),
        ) {
            // 尺寸跟着可用高度连续变化，不能用阈值切换规格：一旦跨阈值，
            // 键盘动画到一半时输入框会先冲过头再弹回来。
            val roominess = ((maxHeight - 400.dp) / 140.dp).coerceIn(0f, 1f)
            val fieldHeight = 88.dp + 44.dp * roominess
            val topReserve = 48.dp + 24.dp * roominess
            val bottomGap = 12.dp + 28.dp * roominess

            // 按钮的终点。不用 constraints：它的高度已经扣掉了底部 inset，
            // 算出来会比屏幕中心高半个导航栏。窗口尺寸才是真·屏幕中心。
            val windowSize = LocalWindowInfo.current.containerSize
            val screenCenter = Offset(windowSize.width / 2f, windowSize.height / 2f)
            // 两个中心都在 root 坐标系里取，跟各自最终摆在哪无关；只在布局阶段写一次，
            // 后面的位移全部在 graphicsLayer / offset 里读，不触发重组。
            var fieldCenter by remember { mutableStateOf(Offset.Zero) }
            var buttonCenter by remember { mutableStateOf(Offset.Zero) }

            CircleBackButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp),
            )

            // 上下两段空白把输入框压在正中；按钮参与布局而不是浮在上面，
            // 这样键盘顶起页面时两者不会重叠。
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    // 把顶栏那块让出来，输入框才不会顶到返回按钮上。
                    .padding(top = topReserve),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 上方权重略大于下方：按钮占了自己的高度，等权重会让输入框整体偏上，
                // 按 1.2 : 1 分才回到屏幕视觉中心。
                Spacer(Modifier.weight(1.2f))

                ShareKeyField(
                    value = state.input,
                    onValueChange = onInputChange,
                    minFieldHeight = fieldHeight,
                    hintExit = hintExit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .padding(horizontal = 28.dp)
                        .onGloballyPositioned { fieldCenter = it.centerInRoot() }
                        // 顺序不能反：graphicsLayer 必须在外层。blur 内部会开一个跟控件同样大小的
                        // 离屏缓冲，位移若写在它里层，飞出原来那块矩形的内容就被这个缓冲裁掉了——
                        // 表现就是"没有真的移动，只是被切了一刀"。
                        .graphicsLayer {
                            val delta = (buttonCenter - fieldCenter) * fieldExit
                            translationX = delta.x
                            translationY = delta.y
                            val shrink = 1f - FIELD_SHRINK * fieldExit
                            scaleX = shrink
                            scaleY = shrink
                            alpha = 1f - fieldExit
                        }
                        // 模糊半径只能给具体 dp，必须参与重组；位移和缩放不重组。
                        .blur(
                            radius = FIELD_BLUR * fieldExit,
                            edgeTreatment = BlurredEdgeTreatment.Unbounded,
                        ),
                )

                Spacer(Modifier.weight(1f))

                CookieImportButton(
                    onClick = onImport,
                    // 输入框空着、或文本里认不出口令时按钮置灰，避免点了没反应又看不到错误提示。
                    enabled = state.canImport,
                    submitting = submitting,
                    arrowExit = arrowExit,
                    modifier = Modifier
                        .padding(bottom = bottomGap)
                        .onGloballyPositioned { buttonCenter = it.centerInRoot() }
                        // offset 写在 observer 之后：上报的是移动前的位置，终点才是稳定的。
                        .offset {
                            IntOffset(
                                x = ((screenCenter.x - buttonCenter.x) * buttonMove).roundToInt(),
                                y = ((screenCenter.y - buttonCenter.y) * buttonMove).roundToInt(),
                            )
                        },
                )
            }
        }
    }
}

@Composable
private fun ShareKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    minFieldHeight: Dp,
    hintExit: Float,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minFieldHeight),
        textStyle = MaterialTheme.typography.bodyLarge,
        // 用主题里的形状预设，不自己写圆角；extraLarge 在当前 M3 形状表里就是 28dp。
        shape = MaterialTheme.shapes.extraLarge,
        // 固定提示，不随输入、加载或失败变化；其余状态文案暂时都不做。
        supportingText = {
            Text(
                text = stringResource(R.string.wakeup_import_key_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    // 往上推的同时淡出，比原地淡出更像「收起来」；layout 尺寸不变，
                    // 所以下面按钮不会跟着跳。
                    .graphicsLayer {
                        translationY = -(HINT_LIFT.toPx() * hintExit)
                        alpha = 1f - hintExit
                    },
                textAlign = TextAlign.Center,
            )
        },
    )
}

/**
 * 九瓣饼干的提交按钮：按下时收到正圆，抬起再散开；不可用时底色与图标色淡出去。
 *
 * MaterialShapes 的 `toShape()` 返回的是匿名 `Shape`，不满足 `CornerBasedShape`，
 * 所以 IconButton 自带的形状动画永远不会参与——只能自己用 `Morph` 驱动进度。
 * 颜色同理，`colors` 只接受静态值，可用性切换也要自己插值。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CookieImportButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    submitting: Boolean = false,
    arrowExit: Float = 0f,
) {
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(durationMillis = PRESS_MORPH_MILLIS),
        label = "cookiePressMorph",
    )
    val shape = remember(morph, progress) { MorphingShape(morph, progress) }

    // 提交中虽然点不动了，但视觉上要一直是品牌色——此时它正在往外飞，
    // 不能顺手淡成灰色，否则整段出场动画会脏掉。
    val tinted = enabled || submitting

    // 颜色自己插值：IconButton 的 colors 只吃静态值，直接给 disabledContainerColor
    // 会在 enabled 翻转的那一帧硬切。插值结果同时喂给启用、禁用两组槽位，
    // 于是真正的 enabled 翻转发生时颜色早已走到位，中间不会闪。
    val containerColor by animateColorAsState(
        targetValue = if (tinted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        },
        animationSpec = tween(durationMillis = ENABLE_COLOR_MILLIS),
        label = "cookieContainerColor",
    )
    val contentColor by animateColorAsState(
        targetValue = if (tinted) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        },
        animationSpec = tween(durationMillis = ENABLE_COLOR_MILLIS),
        label = "cookieContentColor",
    )

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        enabled = enabled && !submitting,
        // requiredSize：形状按钮被父布局压扁时会更难看，宁可溢出也不变形。
        // clip：让飞出去的箭头被裁在按钮形状里——形状每帧都在变，clip 跟着重组。
        modifier = modifier
            .requiredSize(BUTTON_SIZE)
            .clip(shape),
        // 两个状态给同一个实例，IconButton 才会当静态形状直接画，不再自己切换。
        shapes = IconButtonDefaults.shapes(shape = shape, pressedShape = shape),
        // 四个槽位都塞插值后的颜色：禁用态若留给默认值会退回「透明底 + 半透明图标」，
        // 整颗按钮看着会直接消失。
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = stringResource(R.string.action_import),
            modifier = Modifier
                .size(32.dp)
                .graphicsLayer {
                    // 顺着箭头方向滑出去，滑到看不见为止；被上面的 clip 关在按钮内，
                    // 所以是「从按钮内部飞走」，不是「从边缘冒出去」。
                    translationX = ARROW_FLY.toPx() * arrowExit
                    alpha = 1f - arrowExit
                },
        )
    }
}

/**
 * 把 `Morph` 在某个进度上的轮廓当成 `Shape` 用。
 *
 * 缩放与居中的处理方式和 `RoundedPolygon.toShape()` 保持一致：先按控件尺寸缩放，
 * 再把轮廓包围盒的中心对到控件中心。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class MorphingShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = morph.toPath(progress, Path())
        path.transform(Matrix().apply { scale(size.width, size.height) })
        path.translate(size.center - path.getBounds().center)
        return Outline.Generic(path)
    }
}

private const val PRESS_MORPH_MILLIS = 220

/** 可用状态切换时底色与图标色的过渡时长，和按下形变保持同一节奏。 */
private const val ENABLE_COLOR_MILLIS = 200

/** 点击提交后整段出场动画的总时长。 */
private const val SUBMIT_MILLIS = 900

private val BUTTON_SIZE = 92.dp

/** 箭头在按钮内部向右滑出的距离，滑到边界外就被 clip 裁掉。 */
private val ARROW_FLY = 56.dp

/** 提示文案上移的距离。 */
private val HINT_LIFT = 14.dp

/** 输入框淡出时糊到的最大半径。 */
private val FIELD_BLUR = 18.dp

/** 输入框飞到按钮时收缩到原尺寸的比例。 */
private const val FIELD_SHRINK = 0.55f

/**
 * 把主时间轴上的 `[start, end]` 区间映射成 0..1，区间外截断成 0 / 1。
 *
 * 这样多条动作共享一个 `Animatable`，加减动作只改区间，不用重排时长总和。
 */
private fun Float.segment(start: Float, end: Float): Float =
    ((this - start) / (end - start)).coerceIn(0f, 1f)

/** 离场用加速曲线：起步慢、越走越快，像是被"抽走"。 */
private fun Float.accelerate(): Float = FastOutLinearInEasing.transform(this)

/** 位移用减速曲线：快速起步、稳稳停住。 */
private fun Float.decelerate(): Float = FastOutSlowInEasing.transform(this)

/** 控件在 root 坐标系里的中心点。 */
private fun LayoutCoordinates.centerInRoot(): Offset {
    val origin = positionInRoot()
    return Offset(origin.x + size.width / 2f, origin.y + size.height / 2f)
}

