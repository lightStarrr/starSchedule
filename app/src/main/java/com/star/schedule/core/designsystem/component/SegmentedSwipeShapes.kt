package com.star.schedule.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp

/**
 * 拖动时的分段形状：只把朝向被拖走那一侧的两个圆角，从本行原本的半径渐变到「组首/组尾」的大半径。
 *
 * 另外两个角原样保留——第一项的上角不会因为邻居被拖走而变小。
 * 半径不是猜的：直接从 M3 原生形状的 Outline 里读出来。
 *
 * [topMorph] / [bottomMorph] 是 0..1 的进度：0 表示保持本行原生圆角，1 表示完全变成组首/组尾。
 */
@Composable
fun morphedSegmentedShapes(
    shapeIndex: Int,
    shapeCount: Int,
    topMorph: Float,
    bottomMorph: Float,
): ListItemShapes? {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val native = ListItemDefaults.segmentedShapes(shapeIndex, shapeCount).shape
    // 组里只有一项时的形状：四个角都是「组首/组尾」的大圆角。
    val outer = ListItemDefaults.segmentedShapes(0, 1).shape

    val nativeRadii = native.cornerRadiiPx(density, direction) ?: return null
    val outerRadii = outer.cornerRadiiPx(density, direction) ?: return null
    val outerRadiusDp = (outerRadii[0] / density.density).dp

    fun lerpDp(fromPx: Float, fraction: Float) =
        lerp((fromPx / density.density).dp, outerRadiusDp, fraction.coerceIn(0f, 1f))

    val top = lerpDp(nativeRadii[0], topMorph)
    val bottom = lerpDp(nativeRadii[2], bottomMorph)
    val shape = RoundedCornerShape(
        topStart = top,
        topEnd = top,
        bottomStart = bottom,
        bottomEnd = bottom,
    )
    return ListItemShapes(shape, shape, shape, shape, shape, shape)
}

/** 从形状的 Outline 里读出四个角的半径（px）。圆角形状都是 Outline.Rounded。 */
private fun Shape.cornerRadiiPx(density: Density, direction: LayoutDirection): FloatArray? {
    val rounded = (createOutline(Size(400f, 200f), direction, density) as? Outline.Rounded)?.roundRect
        ?: return null
    return floatArrayOf(
        rounded.topLeftCornerRadius.x,
        rounded.topRightCornerRadius.x,
        rounded.bottomRightCornerRadius.x,
        rounded.bottomLeftCornerRadius.x,
    )
}
