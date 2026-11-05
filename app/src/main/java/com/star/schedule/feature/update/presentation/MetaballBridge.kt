package com.star.schedule.feature.update.presentation

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal data class BridgePoint(val x: Float, val y: Float)

internal data class MetaballBridge(
    val start1: BridgePoint,
    val control1: BridgePoint,
    val control2: BridgePoint,
    val end1: BridgePoint,
    val start2: BridgePoint,
    val control3: BridgePoint,
    val control4: BridgePoint,
    val end2: BridgePoint,
)

private val HALF_PI = (Math.PI / 2).toFloat()

private fun polar(cx: Float, cy: Float, angle: Float, radius: Float) = BridgePoint(
    x = cx + radius * cos(angle),
    y = cy + radius * sin(angle),
)

internal fun metaballBridge(
    c1x: Float,
    c1y: Float,
    r1: Float,
    c2x: Float,
    c2y: Float,
    r2: Float,
    maxDistance: Float,
    spread: Float = 0.5f,
    handleSize: Float = 2.4f,
): MetaballBridge? {
    val dx = c2x - c1x
    val dy = c2y - c1y
    val d = sqrt(dx * dx + dy * dy)
    val radiusSum = r1 + r2
    if (d >= maxDistance || d <= abs(r1 - r2)) return null

    val u1: Float
    val u2: Float
    if (d < radiusSum) {
        u1 = acos(((r1 * r1 + d * d - r2 * r2) / (2 * r1 * d)).coerceIn(-1f, 1f))
        u2 = acos(((r2 * r2 + d * d - r1 * r1) / (2 * r2 * d)).coerceIn(-1f, 1f))
    } else {
        u1 = 0f
        u2 = 0f
    }
    val angle1 = atan2(dy, dx)
    val angle2 = acos(((r1 - r2) / d).coerceIn(-1f, 1f))

    // 分离时让颈部随距离线性收窄，到 maxDistance 归零，断开不是"啪"地消失。
    val v = if (d > radiusSum) {
        spread * (1f - (d - radiusSum) / (maxDistance - radiusSum))
    } else {
        spread
    }

    val angle1a = angle1 + u1 + (angle2 - u1) * v
    val angle1b = angle1 - u1 - (angle2 - u1) * v
    val angle2a = angle1 + Math.PI.toFloat() - u2 - (Math.PI.toFloat() - u2 - angle2) * v
    val angle2b = angle1 - Math.PI.toFloat() + u2 + (Math.PI.toFloat() - u2 - angle2) * v

    val p1a = polar(c1x, c1y, angle1a, r1)
    val p1b = polar(c1x, c1y, angle1b, r1)
    val p2a = polar(c2x, c2y, angle2a, r2)
    val p2b = polar(c2x, c2y, angle2b, r2)

    val gap = sqrt(
        (p1a.x - p2a.x) * (p1a.x - p2a.x) + (p1a.y - p2a.y) * (p1a.y - p2a.y),
    )
    val d2 = min(v * handleSize, gap / radiusSum) * min(1f, 2 * d / radiusSum)
    val r1h = r1 * d2
    val r2h = r2 * d2

    return MetaballBridge(
        start1 = p1a,
        control1 = polar(p1a.x, p1a.y, angle1a - HALF_PI, r1h),
        control2 = polar(p2a.x, p2a.y, angle2a + HALF_PI, r2h),
        end1 = p2a,
        start2 = p2b,
        control3 = polar(p2b.x, p2b.y, angle2b - HALF_PI, r2h),
        control4 = polar(p1b.x, p1b.y, angle1b + HALF_PI, r1h),
        end2 = p1b,
    )
}
