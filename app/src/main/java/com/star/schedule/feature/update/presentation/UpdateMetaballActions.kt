package com.star.schedule.feature.update.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal sealed interface UpdatePillContent {
    data class Label(
        val text: String,
        val enabled: Boolean,
        val onClick: () -> Unit,
    ) : UpdatePillContent

    data class Progress(
        val progress: Float,
        val label: String,
    ) : UpdatePillContent
}

internal data class UpdateCircleAction(
    val icon: Painter,
    val contentDescription: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

private class LastCircleAction {
    var action: UpdateCircleAction? = null
}

private fun MetaballBridge.toPath(): Path = Path().apply {
    moveTo(start1.x, start1.y)
    cubicTo(control1.x, control1.y, control2.x, control2.y, end1.x, end1.y)
    lineTo(start2.x, start2.y)
    cubicTo(control3.x, control3.y, control4.x, control4.y, end2.x, end2.y)
    close()
}

@Composable
internal fun UpdateMetaballActions(
    pill: UpdatePillContent,
    leftCircle: UpdateCircleAction?,
    rightCircle: UpdateCircleAction?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val surface = colorScheme.surfaceContainer
    val disabledContainer = colorScheme.onSurface
        .copy(alpha = 0.12f)
        .compositeOver(surface)
    val disabledContent = colorScheme.onSurface
        .copy(alpha = 0.38f)
        .compositeOver(surface)

    val lastLeft = remember { LastCircleAction() }
    if (leftCircle != null) lastLeft.action = leftCircle
    val lastRight = remember { LastCircleAction() }
    if (rightCircle != null) lastRight.action = rightCircle
    val drawnLeft = leftCircle ?: lastLeft.action
    val drawnRight = rightCircle ?: lastRight.action

    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val fillFraction = remember { Animatable(1f) }
    val fillTarget = when (pill) {
        is UpdatePillContent.Label -> 1f
        is UpdatePillContent.Progress -> pill.progress
    }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(fillTarget) {
        fillFraction.animateTo(fillTarget, spatialSpec)
    }
    val fillColor by animateColorAsState(
        targetValue = when (pill) {
            is UpdatePillContent.Label ->
                if (pill.enabled) colorScheme.primary else disabledContainer
            is UpdatePillContent.Progress -> colorScheme.primary
        },
        animationSpec = effects,
        label = "fill",
    )
    val fillContentColor = animateColorAsState(
        targetValue = when (pill) {
            is UpdatePillContent.Label ->
                if (pill.enabled) colorScheme.onPrimary else disabledContent
            is UpdatePillContent.Progress -> colorScheme.onPrimary
        },
        animationSpec = effects,
        label = "fillContent",
    )
    val leftCircleColor by animateColorAsState(
        targetValue = if (drawnLeft?.enabled == false) {
            disabledContainer
        } else {
            colorScheme.primary
        },
        animationSpec = effects,
        label = "leftCircle",
    )
    val leftContentColor by animateColorAsState(
        targetValue = if (drawnLeft?.enabled == false) {
            disabledContent
        } else {
            colorScheme.onPrimary
        },
        animationSpec = effects,
        label = "leftContent",
    )
    val rightCircleColor by animateColorAsState(
        targetValue = if (drawnRight?.enabled == false) {
            disabledContainer
        } else {
            colorScheme.primary
        },
        animationSpec = effects,
        label = "rightCircle",
    )
    val rightContentColor by animateColorAsState(
        targetValue = if (drawnRight?.enabled == false) {
            disabledContent
        } else {
            colorScheme.onPrimary
        },
        animationSpec = effects,
        label = "rightContent",
    )

    val pL = animateFloatAsState(
        targetValue = if (leftCircle != null) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "pL",
    )
    val pR = animateFloatAsState(
        targetValue = if (rightCircle != null) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "pR",
    )
    val height = METABALL_HEIGHT
    val step = METABALL_HEIGHT + METABALL_SPACING
    val density = LocalDensity.current
    val stepPx = with(density) { step.toPx() }
    val heightPx = with(density) { height.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clipToBounds(),
    ) {
        val track = colorScheme.primaryContainer
        val widthPx = constraints.maxWidth.toFloat()
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.height / 2f
            val centerY = size.height / 2f
            val leftCx = radius
            val rightCx = size.width - radius
            val pillLeft = pL.value.coerceAtLeast(0f) * stepPx
            val pillRight = size.width - pR.value.coerceAtLeast(0f) * stepPx
            val leftCapX = pillLeft + radius
            val rightCapX = pillRight - radius
            val f = fillFraction.value.coerceIn(0f, 1f)
            val pillEndLeft = if (f > 0f) fillColor else track
            val pillEndRight = if (f >= 0.999f) fillColor else track

            if (drawnLeft != null) {
                metaballBridge(
                    c1x = leftCx,
                    c1y = centerY,
                    r1 = radius,
                    c2x = leftCapX,
                    c2y = centerY,
                    r2 = radius,
                    maxDistance = stepPx,
                )?.let { bridge ->
                    drawPath(
                        path = bridge.toPath(),
                        brush = Brush.horizontalGradient(
                            colors = listOf(leftCircleColor, pillEndLeft),
                            startX = leftCx,
                            endX = leftCapX,
                            tileMode = TileMode.Clamp,
                        ),
                    )
                }
                drawCircle(
                    color = leftCircleColor,
                    radius = radius,
                    center = Offset(leftCx, centerY),
                )
                val alpha = pL.value.coerceIn(0f, 1f)
                if (alpha > 0f) {
                    val icon = METABALL_ICON_SIZE.toPx()
                    translate(leftCx - icon / 2f, centerY - icon / 2f) {
                        with(drawnLeft.icon) {
                            draw(
                                size = Size(icon, icon),
                                alpha = alpha,
                                colorFilter = ColorFilter.tint(leftContentColor),
                            )
                        }
                    }
                }
            }

            if (drawnRight != null) {
                metaballBridge(
                    c1x = rightCapX,
                    c1y = centerY,
                    r1 = radius,
                    c2x = rightCx,
                    c2y = centerY,
                    r2 = radius,
                    maxDistance = stepPx,
                )?.let { bridge ->
                    drawPath(
                        path = bridge.toPath(),
                        brush = Brush.horizontalGradient(
                            colors = listOf(pillEndRight, rightCircleColor),
                            startX = rightCapX,
                            endX = rightCx,
                            tileMode = TileMode.Clamp,
                        ),
                    )
                }
                drawCircle(
                    color = rightCircleColor,
                    radius = radius,
                    center = Offset(rightCx, centerY),
                )
                val alpha = pR.value.coerceIn(0f, 1f)
                if (alpha > 0f) {
                    val icon = METABALL_ICON_SIZE.toPx()
                    translate(rightCx - icon / 2f, centerY - icon / 2f) {
                        with(drawnRight.icon) {
                            draw(
                                size = Size(icon, icon),
                                alpha = alpha,
                                colorFilter = ColorFilter.tint(rightContentColor),
                            )
                        }
                    }
                }
            }

            val pillPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = pillLeft,
                        top = 0f,
                        right = pillRight.coerceAtLeast(pillLeft),
                        bottom = size.height,
                        cornerRadius = CornerRadius(radius, radius),
                    ),
                )
            }
            if (f >= 0.999f) {
                drawPath(pillPath, fillColor)
            } else {
                clipPath(pillPath) {
                    val pillWidth = (pillRight - pillLeft).coerceAtLeast(0f)
                    drawRect(
                        color = track,
                        topLeft = Offset(pillLeft, 0f),
                        size = Size(pillWidth, size.height),
                    )
                    drawRect(
                        color = fillColor,
                        topLeft = Offset(pillLeft, 0f),
                        size = Size(pillWidth * f, size.height),
                    )
                }
            }
        }

        if (leftCircle != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(height)
                    .clip(CircleShape)
                    .clickable(
                        enabled = leftCircle.enabled,
                        role = Role.Button,
                        onClick = leftCircle.onClick,
                    )
                    .semantics {
                        contentDescription = leftCircle.contentDescription
                    },
            )
        }
        if (rightCircle != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(height)
                    .clip(CircleShape)
                    .clickable(
                        enabled = rightCircle.enabled,
                        role = Role.Button,
                        onClick = rightCircle.onClick,
                    )
                    .semantics {
                        contentDescription = rightCircle.contentDescription
                    },
            )
        }

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (pL.value.coerceAtLeast(0f) * stepPx).roundToInt(),
                        0,
                    )
                }
                .layout { measurable, _ ->
                    val left = pL.value.coerceAtLeast(0f) * stepPx
                    val right = widthPx - pR.value.coerceAtLeast(0f) * stepPx
                    val placeable = measurable.measure(
                        Constraints.fixed(
                            (right - left).roundToInt().coerceAtLeast(0),
                            heightPx.roundToInt(),
                        ),
                    )
                    layout(placeable.width, placeable.height) {
                        placeable.place(0, 0)
                    }
                }
                .clip(CircleShape)
                .then(
                    when (pill) {
                        is UpdatePillContent.Label -> Modifier.clickable(
                            enabled = pill.enabled,
                            role = Role.Button,
                            onClick = pill.onClick,
                        )

                        is UpdatePillContent.Progress ->
                            Modifier.progressSemantics(pill.progress)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            val styleFraction by animateFloatAsState(
                targetValue = if (pill is UpdatePillContent.Label) 0f else 1f,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "textStyle",
            )
            val textPaint = remember { Paint() }
            RollingText(
                text = when (pill) {
                    is UpdatePillContent.Label -> pill.text
                    is UpdatePillContent.Progress -> pill.label
                },
                style = lerp(
                    MaterialTheme.typography.titleMedium,
                    MaterialTheme.typography.bodyMedium,
                    styleFraction,
                ),
                color = colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithContent {
                        val edge = size.width * fillFraction.value.coerceIn(0f, 1f)
                        clipRect(left = edge) {
                            this@drawWithContent.drawContent()
                        }
                        // 颜色必须在绘制阶段读取，否则颜色动画不会触发文字重绘。
                        textPaint.colorFilter = ColorFilter.tint(
                            fillContentColor.value,
                            BlendMode.SrcIn,
                        )
                        clipRect(right = edge) {
                            drawIntoCanvas { canvas ->
                                canvas.saveLayer(
                                    Rect(0f, 0f, size.width, size.height),
                                    textPaint,
                                )
                                this@drawWithContent.drawContent()
                                canvas.restore()
                            }
                        }
                    }
                    .padding(horizontal = 16.dp),
            )
        }
    }
}

private val METABALL_HEIGHT = 56.dp
private val METABALL_SPACING = 12.dp
private val METABALL_ICON_SIZE = 24.dp
