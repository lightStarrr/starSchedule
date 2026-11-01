package com.star.schedule.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 横向并排的卡片形状，语义对齐 M3 的 `ListItemDefaults.segmentedShapes(index, count)`，
 * 只是把「首尾」从上下换成左右：
 *
 * - 只有一块：四角都是「组首/组尾」的大圆角
 * - 第一块：左侧大、右侧小（相邻处）
 * - 最后一块：左侧小、右侧大
 * - 中间块：左右都小
 *
 * M3 的分段形状只处理竖向分组，横向得自己拼。半径不写死，而是从原生形状的 Outline 里量出来，
 * 这样 M3 调整圆角规格时这里会跟着变。
 */
@Composable
fun rememberHorizontalSegmentedShape(index: Int, count: Int): Shape {
    // 单独一块时直接用原生形状，不用自己拼
    if (count <= 1) return ListItemDefaults.segmentedShapes(0, 1).shape

    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current

    val outerRadius = ListItemDefaults.segmentedShapes(0, 1).shape
        .cornerRadiiPx(density, direction)
        ?.get(0)
        ?.let { (it / density.density).dp }
        ?: FALLBACK_OUTER_RADIUS
    // 竖向两段里的第一项：下方两个角就是相邻处的小圆角
    val innerRadius = ListItemDefaults.segmentedShapes(0, 2).shape
        .cornerRadiiPx(density, direction)
        ?.get(2)
        ?.let { (it / density.density).dp }
        ?: FALLBACK_INNER_RADIUS

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

private val FALLBACK_OUTER_RADIUS = 28.dp
private val FALLBACK_INNER_RADIUS = 8.dp
