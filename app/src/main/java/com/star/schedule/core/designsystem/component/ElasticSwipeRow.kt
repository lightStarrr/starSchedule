package com.star.schedule.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 一组「左右滑删除」的行共享的状态。
 *
 * 被拖的那一行把自己的进度写进来，其余行据此被「带着走」一点——所以状态要提到列表这一层，
 * 每行只拿到自己的那份 [ElasticSwipeInfo]。
 */
@Stable
class ElasticSwipeState internal constructor() {
    internal var activeIndex by mutableStateOf<Int?>(null)
    internal var progress by mutableFloatStateOf(0f)

    /** 手势方向：-1 往左、+1 往右。 */
    internal var direction by mutableFloatStateOf(0f)
}

@Composable
fun rememberElasticSwipeState(): ElasticSwipeState = remember { ElasticSwipeState() }

/**
 * 一行的几何信息，交给 content 自己决定怎么用（改形状、改内边距都行）。
 */
@Immutable
data class ElasticSwipeInfo(
    /** 这一行作为邻居被带动的量，带符号（-1..1）；0 表示不受影响。 */
    val follow: Float,
    /** 需要「让开」的边：-1 朝向上面那行、1 朝向下面那行、0 不需要。 */
    val morphSide: Int,
)

/**
 * 手感参数。
 *
 * @param detachProgress 滑开「本行实际宽度」多少比例算删除；过了这个点邻居就不跟着走了。
 * @param neighborRows 最多影响上下各几行。
 * @param neighborDrag 紧邻那一行被带动的最大位移，更远的按 1/距离 衰减。
 * @param springOmega 邻居跟手用的弹簧角频率，越大越硬。
 * @param springZeta 阻尼比，<1 会有回弹。
 * @param exitDurationMillis 松手判定删除后，行滑出去用多久。
 */
@Immutable
data class ElasticSwipeConfig(
    val detachProgress: Float = 0.35f,
    val neighborRows: Int = 4,
    val neighborDrag: Dp = 10.dp,
    val springOmega: Float = 22f,
    val springZeta: Float = 0.65f,
    val exitDurationMillis: Int = 160,
)

/**
 * 可左右滑删除的一行：不铺底色，内容跟手移动并淡出，滑过 [ElasticSwipeConfig.detachProgress]
 * 松手即删除，否则弹回原位。相邻的若干行会被带着走一点（越远越弱），并在越过阈值后收回。
 *
 * @param index 在列表里的下标，用来算「离被拖的行有多远」。
 */
@Composable
fun ElasticSwipeRow(
    index: Int,
    state: ElasticSwipeState,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    config: ElasticSwipeConfig = ElasticSwipeConfig(),
    content: @Composable (ElasticSwipeInfo) -> Unit,
) {
    val density = LocalDensity.current
    // 行的实际宽度：进度、阈值、飞出去的距离全部按它算，不写死 dp。
    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableFloatStateOf(0f) }
    // 手指是否按着：按着时方向实时跟手（同一手势可以反拖），松手后锁成松手瞬间的方向，
    // 免得回弹弹簧过冲穿过原点时把方向翻过来。
    var fingerDown by remember { mutableStateOf(false) }
    var releasedSign by remember { mutableFloatStateOf(0f) }
    // 收尾动画放在组件自己的 scope 里，并且可以被新的按下打断：
    // onDragStopped 里如果直接 await 动画，这个节点的 pointerInput 会一直挂着，
    // 动画跑完之前新的一次拖动根本进不来。
    val scope = rememberCoroutineScope()
    var settleJob by remember { mutableStateOf<Job?>(null) }

    /** 本次拖动方向上的「朝外」位移：反方向的过冲不算，否则行会在回弹途中又淡一次。 */
    fun outwardPx(sign: Float): Float = when {
        sign < 0f -> -offset
        sign > 0f -> offset
        else -> 0f
    }

    fun currentSign(): Float = when {
        fingerDown && offset < 0f -> -1f
        fingerDown && offset > 0f -> 1f
        else -> releasedSign
    }

    /** 把当前进度写到共享状态里，供邻居使用。必须从 state 现算，不能读组合期的值。 */
    fun publish() {
        val sign = currentSign()
        val width = rowWidthPx
        if (sign == 0f || width <= 0f) {
            state.progress = 0f
            return
        }
        state.activeIndex = index
        state.direction = sign
        state.progress = (outwardPx(sign) / width).coerceIn(0f, 1f)
    }

    /**
     * 一次拖动结束（弹回原位或删除）后把列表级状态交还干净。
     *
     * 两条收尾路径都必须做：`activeIndex` 停在旧下标时，删除导致列表重排后，
     * 新列表里落在同一位置的那行会被当成「正在被拖」，邻居也会按上一次的进度定位。
     * 回弹分支原本就清了大部分，删除分支漏了，这里统一。
     */
    fun releaseSharedState() {
        state.activeIndex = null
        state.progress = 0f
        state.direction = 0f
        releasedSign = 0f
    }

    val dragging = state.activeIndex == index
    /**
     * 自己的淡出进度只看自己的位移，不读列表级的 [ElasticSwipeState.progress]。
     *
     * 删除收尾时 `releaseSharedState()` 会把 progress 归零；如果透明度跟着它走，
     * 那一帧整行会从全透明跳回不透明，在已经滑到屏幕左侧的位置露出一条卡片残影，
     * 再被列表移除慢慢淡掉——就是「侧边闪一下」。
     */
    val ownProgress = if (rowWidthPx > 0f) {
        (abs(offset) / rowWidthPx).coerceIn(0f, 1f)
    } else {
        0f
    }

    // 邻居跟手：目标值随进度给（越过阈值后变 0），实际位移用逐帧积分的阻尼弹簧追它，
    // 所以跟随、脱离、反向再拉回来都是同一条连续曲线。
    val rowDistance = state.activeIndex?.let { abs(index - it) } ?: Int.MAX_VALUE
    val isNeighbor = !dragging && rowDistance in 1..config.neighborRows
    val followTarget = if (isNeighbor) {
        val p = (state.progress / config.detachProgress).coerceIn(0f, 1f)
        val shape = if (p < 1f) p * p * (3f - 2f * p) else 0f
        shape * state.direction / rowDistance
    } else {
        0f
    }
    var follow by remember { mutableFloatStateOf(0f) }
    var followVelocity by remember { mutableFloatStateOf(0f) }
    val latestTarget = rememberUpdatedState(followTarget)
    LaunchedEffect(isNeighbor) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = now
            val goal = if (isNeighbor) latestTarget.value else 0f
            val accel = config.springOmega * config.springOmega * (goal - follow) -
                2f * config.springZeta * config.springOmega * followVelocity
            followVelocity += accel * dt
            follow += followVelocity * dt
            // 停下来就不再写 state，避免空闲时每帧重组。
            if (!isNeighbor && abs(goal - follow) < 0.002f && abs(followVelocity) < 0.02f) {
                follow = 0f
                followVelocity = 0f
                break
            }
        }
    }

    // 让开哪一侧记在状态里：拖动结束后 activeIndex 会被清空，靠它继续把动画收尾。
    var morphSide by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.activeIndex) {
        when (state.activeIndex) {
            index - 1 -> morphSide = -1
            index + 1 -> morphSide = 1
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { rowWidthPx = it.width.toFloat() }
            .offset { IntOffset((offset + follow * with(density) { config.neighborDrag.toPx() }).roundToInt(), 0) }
            .alpha(1f - ownProgress)
            .draggable(
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    settleJob?.cancel()
                    settleJob = null
                    fingerDown = true
                },
                state = rememberDraggableState { delta ->
                    // 左右都不设上限，想拖多远拖多远。
                    offset += delta
                    publish()
                },
                onDragStopped = {
                    fingerDown = false
                    val sign = when {
                        offset < 0f -> -1f
                        offset > 0f -> 1f
                        else -> 0f
                    }
                    releasedSign = sign
                    val width = rowWidthPx
                    val releasedProgress = if (sign == 0f || width <= 0f) {
                        0f
                    } else {
                        (outwardPx(sign) / width).coerceIn(0f, 1f)
                    }
                    if (releasedProgress >= config.detachProgress) {
                        // 已经滑过阈值，松手就是删除：先顺着惯性滑出去（同时淡到 0）。
                        settleJob = scope.launch {
                            animate(
                                initialValue = offset,
                                targetValue = if (offset < 0f) -width else width,
                                animationSpec = tween(
                                    durationMillis = config.exitDurationMillis,
                                    easing = LinearEasing,
                                ),
                            ) { value, _ ->
                                offset = value
                                publish()
                            }
                            // 行马上会从列表里消失，但 activeIndex / progress 是列表级的：
                            // 交还给状态再删，列表重排后不会有行被误判成「正在被拖」。
                            // 放在 onDelete 之后、同一帧内完成，避免消隐前再闪一下。
                            onDelete()
                            releaseSharedState()
                        }
                    } else {
                        // 回弹的每一帧都要继续上报进度，相邻的行才会跟着一起回来。
                        settleJob = scope.launch {
                            animate(
                                initialValue = offset,
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            ) { value, _ ->
                                offset = value
                                publish()
                            }
                            releaseSharedState()
                        }
                    }
                },
            ),
    ) {
        content(ElasticSwipeInfo(follow = follow, morphSide = morphSide))
    }
}
