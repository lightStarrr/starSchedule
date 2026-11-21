package com.star.schedule.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.star.schedule.core.designsystem.color.successColors
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 饼干形主动作按钮：按下收圆、加载时在多边形之间变形旋转，完成后平滑收回饼干并可染绿弹勾。
 *
 * [busy] 表示出场编排正在播——此时虽然点不动，颜色必须保持品牌色，不能淡成禁用态的灰。
 * 收回完成（成功时连勾也算）才回调 [onSettled]。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CookieActionButton(
    onClick: () -> Unit,
    actionContentDescription: String,
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
            toShape = pickDifferentCookieShapeIndex(fromShape, CookieCycleShapes.size, random)
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

    val successColors = successColors()
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
            .requiredSize(COOKIE_BUTTON_SIZE)
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
            modifier = Modifier.requiredSize(COOKIE_BUTTON_SIZE),
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
                contentDescription = actionContentDescription,
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
internal val CookieCycleShapes: List<RoundedPolygon> =
    LoadingIndicatorDefaults.IndeterminateIndicatorPolygons
        .filter { it !== MaterialShapes.Cookie9Sided }

internal fun pickDifferentCookieShapeIndex(
    current: Int,
    shapeCount: Int,
    random: Random,
): Int {
    require(shapeCount >= 2) { "候选形状少于两个时无法保证前后不同" }
    if (current !in 0 until shapeCount) return random.nextInt(shapeCount)
    val raw = random.nextInt(shapeCount - 1)
    return if (raw >= current) raw + 1 else raw
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun cookieShapeAt(index: Int): RoundedPolygon =
    if (index == COOKIE_SHAPE) MaterialShapes.Cookie9Sided else CookieCycleShapes[index]

private val COOKIE_BUTTON_SIZE = 92.dp
private const val PRESS_MORPH_MILLIS = 220
private const val ENABLE_COLOR_MILLIS = 200
private const val HANDOFF_HOLD_MILLIS = 160L
private const val COOKIE_SYMMETRY_DEGREES = 40f
private const val ROTATE_PER_STEP = 2f * COOKIE_SYMMETRY_DEGREES
private const val MORPH_GAP_MILLIS = 260L
private const val COOKIE_SHAPE = -1

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
