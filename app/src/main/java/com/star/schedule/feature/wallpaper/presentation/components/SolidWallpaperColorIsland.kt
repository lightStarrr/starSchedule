package com.star.schedule.feature.wallpaper.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import com.star.schedule.core.designsystem.rememberAnimationsEnabled
import com.star.schedule.feature.wallpaper.domain.SolidColorHsv
import com.star.schedule.feature.wallpaper.domain.formatSolidColorHex

private val PalettePillHeight = 56.dp
private val PaletteIslandCorner = 28.dp
private val PalettePillCorner = 64.dp
private val PaletteBoardHeight = 160.dp
private val PaletteBoardCorner = 20.dp
private val PaletteHueHeight = 28.dp

private val HueSpectrumColors = listOf(
    Color(0xFFFF0000),
    Color(0xFFFFFF00),
    Color(0xFF00FF00),
    Color(0xFF00FFFF),
    Color(0xFF0000FF),
    Color(0xFFFF00FF),
    Color(0xFFFF0000),
)

/**
 * 纯色壁纸预览页底部的 M3E 风格调色盘岛：
 * 收起为带当前色点的 tonal 胶囊，展开平滑变形为圆角调色卡片（饼干色标 + HEX + 饱和度/明度画板 + 色相滑轨）。
 * 与 [WallpaperEffectsIsland] 共用同一套 M3E 悬浮岛变形规格。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SolidWallpaperColorIsland(
    selectedArgb: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectColor: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = expanded) { onExpandedChange(false) }

    val animationsEnabled = rememberAnimationsEnabled()
    val spatialDpSpec = if (animationsEnabled) {
        MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()
    } else {
        snap<Dp>()
    }
    val fadeSpec = if (animationsEnabled) {
        MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    } else {
        snap<Float>()
    }
    val sizeSpec = if (animationsEnabled) {
        MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    } else {
        snap<IntSize>()
    }
    val colorSpec = if (animationsEnabled) {
        MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    } else {
        snap<Color>()
    }

    val corner by animateDpAsState(
        targetValue = if (expanded) PaletteIslandCorner else PalettePillCorner,
        animationSpec = spatialDpSpec,
        label = "solidPaletteCorner",
    )
    val containerColor by animateColorAsState(
        targetValue = if (expanded) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        animationSpec = colorSpec,
        label = "solidPaletteContainer",
    )

    var hsv by remember { mutableStateOf(SolidColorHsv.fromArgb(selectedArgb)) }
    LaunchedEffect(selectedArgb) {
        if (hsv.toOpaqueArgb() != selectedArgb) {
            hsv = SolidColorHsv.fromArgb(selectedArgb, fallbackHue = hsv.hue)
        }
    }

    val updateHsv: (SolidColorHsv) -> Unit = { next ->
        val normalized = next.normalized()
        hsv = normalized
        onSelectColor(normalized.toOpaqueArgb())
    }

    Box(modifier.fillMaxSize()) {
        if (expanded) {
            // 点岛外课表区域直接收起调色盘，方便查看完整课表配色效果。
            Spacer(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onExpandedChange(false) },
            )
        }
        Surface(
            onClick = { onExpandedChange(true) },
            enabled = !expanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
            shape = RoundedCornerShape(corner),
            color = containerColor,
            contentColor = if (expanded) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            shadowElevation = 3.dp,
        ) {
            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) using
                        SizeTransform { _, _ -> sizeSpec }
                },
                label = "solidPaletteContent",
            ) { isExpanded ->
                if (isExpanded) {
                    SolidColorPaletteCardContent(
                        selectedArgb = selectedArgb,
                        hsv = hsv,
                        animationsEnabled = animationsEnabled,
                        onHsvChange = updateHsv,
                        onCollapse = { onExpandedChange(false) },
                    )
                } else {
                    SolidColorPillContent(selectedArgb = selectedArgb)
                }
            }
        }
    }
}

@Composable
private fun SolidColorPillContent(selectedArgb: Int) {
    Row(
        modifier = Modifier
            .height(PalettePillHeight)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color(selectedArgb))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.wallpaper_solid_palette),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SolidColorPaletteCardContent(
    selectedArgb: Int,
    hsv: SolidColorHsv,
    animationsEnabled: Boolean,
    onHsvChange: (SolidColorHsv) -> Unit,
    onCollapse: () -> Unit,
) {
    val cookieShape = MaterialShapes.Cookie7Sided.toShape()
    val hexText = remember(selectedArgb) { formatSolidColorHex(selectedArgb) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = cookieShape,
                color = Color(selectedArgb),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                content = {},
            )
            Text(
                text = stringResource(R.string.wallpaper_solid_palette),
                style = MaterialTheme.typography.titleMedium,
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = hexText,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFeatureSettings = "tnum",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onCollapse) {
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(R.string.wallpaper_effects_collapse),
                )
            }
        }

        SaturationValueBoard(
            hue = hsv.hue,
            saturation = hsv.saturation,
            value = hsv.value,
            selectedColor = Color(selectedArgb),
            animationsEnabled = animationsEnabled,
            onSaturationValueChange = { s, v ->
                onHsvChange(hsv.copy(saturation = s, value = v))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(PaletteBoardHeight),
        )

        HueSliderTrack(
            hue = hsv.hue,
            animationsEnabled = animationsEnabled,
            onHueChange = { newHue ->
                onHsvChange(hsv.copy(hue = newHue))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(PaletteHueHeight),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SaturationValueBoard(
    hue: Float,
    saturation: Float,
    value: Float,
    selectedColor: Color,
    animationsEnabled: Boolean,
    onSaturationValueChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnChange by rememberUpdatedState(onSaturationValueChange)
    var pressed by remember { mutableStateOf(false) }
    val handleRadiusState = animateDpAsState(
        targetValue = if (pressed) 14.dp else 11.dp,
        animationSpec = if (animationsEnabled) {
            MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
        } else {
            snap<Dp>()
        },
        label = "svHandleRadius",
    )
    val pureHueColor = remember(hue) { Color.hsv(hue.coerceIn(0f, 359.99f), 1f, 1f) }
    val description = stringResource(R.string.wallpaper_solid_sv_description)
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val boardClipPath = remember { Path() }

    Canvas(
        modifier = modifier
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    fun emitAt(position: Offset) {
                        val width = size.width.coerceAtLeast(1).toFloat()
                        val height = size.height.coerceAtLeast(1).toFloat()
                        val s = (position.x / width).coerceIn(0f, 1f)
                        val v = (1f - position.y / height).coerceIn(0f, 1f)
                        latestOnChange(s, v)
                    }
                    down.consume()
                    emitAt(down.position)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                                ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            change.consume()
                            emitAt(change.position)
                        }
                    } finally {
                        pressed = false
                    }
                }
            },
    ) {
        val cornerPx = PaletteBoardCorner.toPx()
        val cornerRadius = CornerRadius(cornerPx, cornerPx)
        boardClipPath.rewind()
        boardClipPath.addRoundRect(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                cornerRadius = cornerRadius,
            ),
        )
        clipPath(boardClipPath) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.White, pureHueColor),
                ),
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                ),
            )
        }
        drawRoundRect(
            color = outlineColor,
            cornerRadius = cornerRadius,
            style = Stroke(width = 1.dp.toPx()),
        )

        val cx = saturation.coerceIn(0f, 1f) * size.width
        val cy = (1f - value.coerceIn(0f, 1f)) * size.height
        val radiusPx = handleRadiusState.value.toPx()
        val center = Offset(cx, cy)

        drawCircle(
            color = Color.Black.copy(alpha = 0.28f),
            radius = radiusPx + 2.dp.toPx(),
            center = center,
        )
        drawCircle(
            color = Color.White,
            radius = radiusPx,
            center = center,
        )
        drawCircle(
            color = selectedColor,
            radius = (radiusPx - 3.dp.toPx()).coerceAtLeast(2f),
            center = center,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HueSliderTrack(
    hue: Float,
    animationsEnabled: Boolean,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnChange by rememberUpdatedState(onHueChange)
    var pressed by remember { mutableStateOf(false) }
    val handleRadiusState = animateDpAsState(
        targetValue = if (pressed) 13.dp else 11.dp,
        animationSpec = if (animationsEnabled) {
            MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
        } else {
            snap<Dp>()
        },
        label = "hueHandleRadius",
    )
    val pureHueColor = remember(hue) { Color.hsv(hue.coerceIn(0f, 359.99f), 1f, 1f) }
    val description = stringResource(R.string.wallpaper_solid_hue_description)
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val trackClipPath = remember { Path() }

    Canvas(
        modifier = modifier
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    fun emitAt(x: Float) {
                        val edgePad = (size.height / 2f).coerceAtLeast(1f)
                        val usable = (size.width - edgePad * 2f).coerceAtLeast(1f)
                        val fraction = ((x - edgePad) / usable).coerceIn(0f, 1f)
                        latestOnChange((fraction * 360f).coerceIn(0f, 359.99f))
                    }
                    down.consume()
                    emitAt(down.position.x)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                                ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            change.consume()
                            emitAt(change.position.x)
                        }
                    } finally {
                        pressed = false
                    }
                }
            },
    ) {
        val halfHeight = size.height / 2f
        val cornerRadius = CornerRadius(halfHeight, halfHeight)
        trackClipPath.rewind()
        trackClipPath.addRoundRect(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                cornerRadius = cornerRadius,
            ),
        )
        clipPath(trackClipPath) {
            drawRect(brush = Brush.horizontalGradient(colors = HueSpectrumColors))
        }
        drawRoundRect(
            color = outlineColor,
            cornerRadius = cornerRadius,
            style = Stroke(width = 1.dp.toPx()),
        )

        val radiusPx = handleRadiusState.value.toPx()
        val usableWidth = (size.width - halfHeight * 2f).coerceAtLeast(1f)
        val cx = halfHeight + (hue.coerceIn(0f, 360f) / 360f) * usableWidth
        val center = Offset(cx, halfHeight)

        drawCircle(
            color = Color.Black.copy(alpha = 0.25f),
            radius = radiusPx + 1.5.dp.toPx(),
            center = center,
        )
        drawCircle(
            color = pureHueColor,
            radius = radiusPx,
            center = center,
        )
        drawCircle(
            color = Color.White,
            radius = radiusPx - 1.5.dp.toPx(),
            center = center,
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}
