package com.star.schedule.feature.importing.wakeup.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.LoadingIndicatorDefaults
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.drawscope.rotate
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
import kotlinx.coroutines.delay
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val buttonMove = elapsed.segment(0.50f, 0.86f).decelerate()
    // 按钮落位之后才交接：按钮淡出的同时加载指示器淡入，两者重叠很短的一段。
    val handoff = elapsed.segment(0.86f, 0.96f)

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

                Box(
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
                    contentAlignment = Alignment.Center,
                ) {
                    CookieImportButton(
                        onClick = onImport,
                        // 输入框空着、或文本里认不出口令时按钮置灰，避免点了没反应又看不到错误提示。
                        enabled = state.canImport,
                        submitting = submitting,
                        arrowExit = arrowExit,
                        handoff = handoff,
                    )
                    // 不是把指示器塞进按钮，而是按钮就地变成它：同一个位置、同样的尺寸、同样的颜色，
                    // 而且第一帧就是同一块饼干，所以交接那一瞬间画面上没有任何变化。
                    if (handoff > 0f) {
                        CookieLoadingIndicator(
                            progress = handoff,
                            modifier = Modifier.requiredSize(BUTTON_SIZE),
                        )
                    }                }
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
    handoff: Float = 0f,
) {
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val progress by animateFloatAsState(
        // 提交中保持饼干，不要提前收成圆：交接给 LoadingIndicator 的瞬间两边必须都是饼干，
        // 否则会在那一下跳一个形状。
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
            // 交接不做渐变，只做「一层盖一层」：指示器从同一块饼干的位置淡入、盖在按钮上，
            // 等它完全铺满（handoff = 1）再把按钮整个撤掉。
            // 反过来写（按钮 1-handoff、指示器 handoff）会在中途叠出两成半透明，颜色发浅、闪一下。
            .graphicsLayer { alpha = if (handoff >= 1f) 0f else 1f }
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
 * 提交中的加载指示器：九瓣饼干在原地开始变形、轮转、循环。
 *
 * 没有用现成的 `LoadingIndicator`，是因为它内部会把整组形状按一个公共比例缩进容器盒
 * （`scaleFactor = 各形状紧包围盒 / 旋转包围盒的最小比值 × ActiveIndicatorScale(38/48)`），
 * 而它的缩放基准是 `normalized()` 之后的单位盒；按钮那边用的是 [MorphingShape] 的画法。
 * 两套约定凑不出像素级重合——只能靠一个"放大一点点"的经验常数硬凑，凑多久都是"差一点点"。
 *
 * 这里改成和按钮同一套画法（[toFittedPath]），于是交接那一帧画出来的就是同一块饼干，
 * 不存在尺寸差、没有偏移，也就没有"跳一下"。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CookieLoadingIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val morphs = remember { WakeUpIndicatorMorphs }
    val step = remember { Animatable(0f) }
    var index by remember { mutableIntStateOf(0) }
    val path = remember { Path() }
    val color = MaterialTheme.colorScheme.primary

    // 持续自转只做“底色动感”，慢一点更好：“忙”的感觉交给形状变化，不要交给转速。
    val spin = rememberInfiniteTransition(label = "cookieSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SPIN_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cookieSpinAngle",
    )

    LaunchedEffect(morphs) {
        // 交接有一小段重叠（按钮还在、指示器淡入）。这段时间形状必须锁死在饼干上，
        // 否则两层各变形各的，叠起来会变成双影。
        delay(HANDOFF_HOLD_MILLIS)
        while (true) {
            step.animateTo(
                targetValue = 1f,
                // 可见阈值放大到 0.1：低阻尼弹簧本该有点回弹，阈值太小会拖出一条尾巴。
                // 刚度比 M3 默认（200）低：一步变形走得更缓，不容易看成“一直在切形状”。
                animationSpec = spring(
                    dampingRatio = 0.6f,
                    stiffness = 110f,
                    visibilityThreshold = 0.1f,
                ),
            )
            index = (index + 1) % morphs.size
            // 序列首尾相接，所以这里从 1 归零不会看到形状跳变。
            step.snapTo(0f)
            delay(MORPH_GAP_MILLIS)
        }
    }

    Canvas(
        modifier = modifier.graphicsLayer { alpha = progress },
    ) {
        val morph = morphs[index]
        // 旋转比 M3 默认慢很多：M3 每步 90°、4.7s 一圈，但它的图形最多只占容器的 79%，
        // 这里的饼干是满格的，同样角速度看着明显更急。这里改成每步 6°、36s 一圈。
        // 每步的角度取模 360：序列绕回第一项时角度才不会倒着跳回去。
        val rotation = (index * ROTATE_PER_STEP) % 360f + step.value * ROTATE_PER_STEP + spin.value
        rotate(degrees = rotation) {
            drawPath(
                // 变形进度截断到 0..1：弹簧的过冲留给旋转，不要拿去把形状拉变形。
                path = morph.toFittedPath(
                    progress = step.value.coerceIn(0f, 1f),
                    size = size,
                    out = path,
                ),
                color = color,
            )
        }
    }
}

/**
 * 把 `Morph` 在某个进度上的轮廓画成控件大小的形状。
 *
 * 缩放与居中的处理方式和 `RoundedPolygon.toShape()` 一致：先按控件尺寸缩放，再把轮廓包围盒的
 * 中心对到控件中心。按钮和加载指示器都走这一条，两边画出来的几何形状完全一致。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun Morph.toFittedPath(progress: Float, size: Size, out: Path = Path()): Path {
    out.reset()
    toPath(progress, out)
    out.transform(Matrix().apply { scale(size.width, size.height) })
    out.translate(size.center - out.getBounds().center)
    return out
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
    ): Outline = Outline.Generic(morph.toFittedPath(progress, size))
}

private const val PRESS_MORPH_MILLIS = 220

/** 可用状态切换时底色与图标色的过渡时长，和按下形变保持同一节奏。 */
private const val ENABLE_COLOR_MILLIS = 200

/**
 * 点击提交后整段出场动画的总时长。
 *
 * ViewModel 用同一个常量来决定「什么时候才真的发请求」——编排播完、按钮已经变成加载指示器
 * 之后才发，所以口令错误也不会把这段动画打断。
 */
private val SUBMIT_MILLIS = WAKEUP_SUBMIT_ANIMATION_MILLIS.toInt()

private val BUTTON_SIZE = 92.dp

/**
 * 交接后先定住一小会儿再开始变形。
 *
 * 交接本身是一段交叉淡化（[SUBMIT_MILLIS] 的 0.86~0.96，约 90ms）。淡化期间两边的形状必须是
 * 同一块饼干，否则半透明的两层各变形各的，看起来就是重影。所以变形要等淡化结束再启动。
 */
private const val HANDOFF_HOLD_MILLIS = 160L

/** 指示器持续自转一圈的时长。M3 默认 4666ms 太急，放慢到 36s。 */
private const val SPIN_MILLIS = 36000

/** 每变形一步转过的角度；和 [SPIN_MILLIS] 一起决定整体转速，目前约 15°/s（24s 一圈）。 */
private const val ROTATE_PER_STEP = 6f

/** 每步变形之后停一下，和弹簧一起构成约 1.1s 一步的节奏。 */
private const val MORPH_GAP_MILLIS = 260L

/**
 * 加载指示器的形状序列：饼干打头，绕一圈回到饼干。
 *
 * 各形状的曲线数不要求一致——`Morph.match()` 在数量不等时会补占位曲线。
 * 打头放饼干是为了接住按钮：进度 0 的那一帧和按钮画的完全一样。
 * 末尾再补一次饼干，是为了让最后一跳也落在饼干上，循环首尾相接不跳变。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val WakeUpIndicatorMorphs: List<Morph> =
    (
        listOf(MaterialShapes.Cookie9Sided) +
            LoadingIndicatorDefaults.IndeterminateIndicatorPolygons +
            listOf(MaterialShapes.Cookie9Sided)
        ).zipWithNext { from, to -> Morph(from, to) }

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

