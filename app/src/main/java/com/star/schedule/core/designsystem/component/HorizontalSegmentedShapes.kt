package com.star.schedule.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 列表卡片外圈的圆角半径（组首/组尾那个大圆角）。
 *
 * 从 M3 原生形状的 Outline 里量出来，不写死：M3 调规格时这里跟着变。
 */
@Composable
fun rememberListItemOuterCornerRadius(): Dp {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    return ListItemDefaults.segmentedShapes(0, 1).shape
        .cornerRadiiPx(density, direction)
        ?.get(0)
        ?.let { (it / density.density).dp }
        ?: FALLBACK_OUTER_RADIUS
}

/**
 * 嵌在大圆角卡片里的内容该用多大圆角：外圆角减去内容到卡片边缘的距离。
 *
 * 两段圆弧同心才是平行的，否则内圆角过大看起来像被压扁的胶囊，过小又像一块生硬的直角。
 */
@Composable
fun nestedCornerRadius(inset: Dp): Dp {
    val outer = rememberListItemOuterCornerRadius()
    return (outer - inset).coerceAtLeast(0.dp)
}

/**
 * 横向并排的卡片形状，语义对齐 M3 的 `ListItemDefaults.segmentedShapes(index, count)`，
 * 只是把「首尾」从上下换成左右：
 *
 * - 只有一块：四角都是「组首/组尾」的大圆角
 * - 第一块：左侧大、右侧小（相邻处）
 * - 最后一块：左侧小、右侧大
 * - 中间块：左右都小
 *
 * M3 的分段形状只处理竖向分组，横向得自己拼。
 */
@Composable
fun rememberHorizontalSegmentedShape(index: Int, count: Int): Shape {
    // 单独一块时直接用原生形状，不用自己拼
    if (count <= 1) return ListItemDefaults.segmentedShapes(0, 1).shape

    val outerRadius = rememberListItemOuterCornerRadius()
    // 竖向两段里的第一项：下方两个角就是相邻处的小圆角
    val innerRadius = innerListItemCornerRadius()

    val isFirst = index <= 0
    val isLast = index >= count - 1
    val leftRadius = if (isFirst) outerRadius else innerRadius
    val rightRadius = if (isLast) outerRadius else innerRadius

    return RoundedCornerShape(
        topStart = leftRadius,
        bottomStart = leftRadius,
        topEnd = rightRadius,
        bottomEnd = rightRadius,
    )
}

@Composable
private fun innerListItemCornerRadius(): Dp {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    return ListItemDefaults.segmentedShapes(0, 2).shape
        .cornerRadiiPx(density, direction)
        ?.get(2)
        ?.let { (it / density.density).dp }
        ?: FALLBACK_INNER_RADIUS
}

private val FALLBACK_OUTER_RADIUS = 28.dp
private val FALLBACK_INNER_RADIUS = 8.dp
