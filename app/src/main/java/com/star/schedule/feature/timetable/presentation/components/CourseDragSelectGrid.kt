package com.star.schedule.feature.timetable.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.star.schedule.core.designsystem.rememberAnimationsEnabled
import com.star.schedule.feature.timetable.domain.CourseRules
import kotlinx.coroutines.withTimeoutOrNull

@Immutable
data class CourseDragSelectGridItem(
    val value: Int,
    val label: String,
    val supporting: String? = null,
)

/**
 * 支持「点按单选 + 手指按住拖动连选」的多选网格，用于节次选择和周次选择。
 *
 * 手势采用单通道 `awaitEachGesture`：短按直接切换单格；滑动超过触控阈值立即放行给外层
 * 页面垂直滚动；长按触发触觉反馈后锁定手势，按首格状态统一批量选中或取消连续区间。
 */
@Composable
fun CourseDragSelectGrid(
    items: List<CourseDragSelectGridItem>,
    selected: Set<Int>,
    columns: Int,
    onSelectionChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeColumns = columns.coerceAtLeast(1)
    val animationsEnabled = rememberAnimationsEnabled()
    val haptic = LocalHapticFeedback.current

    var gridRootOffset by remember { mutableStateOf(Offset.Zero) }
    val itemBounds = remember { mutableStateMapOf<Int, Rect>() }

    LaunchedEffect(items) {
        val activeValues = items.map { it.value }.toSet()
        itemBounds.keys.retainAll(activeValues)
    }

    val currentItems by rememberUpdatedState(items)
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)
    val currentHaptic by rememberUpdatedState(haptic)

    val rows = remember(items, safeColumns) { items.chunked(safeColumns) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { gridRootOffset = it.positionInRoot() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val anchor = findItemAt(down.position, itemBounds) ?: return@awaitEachGesture

                    var abortedByScrollOrCancel = false
                    var releasedAsTap = false
                    val waitResult = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                            if (change == null || change.isConsumed) {
                                abortedByScrollOrCancel = true
                                break
                            }
                            if (change.changedToUpIgnoreConsumed()) {
                                change.consume()
                                releasedAsTap = true
                                break
                            }
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                abortedByScrollOrCancel = true
                                break
                            }
                        }
                    }

                    if (releasedAsTap) {
                        val before = currentSelected
                        val after = if (anchor in before) before - anchor else before + anchor
                        currentOnSelectionChange(after)
                        currentHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        return@awaitEachGesture
                    }

                    if (abortedByScrollOrCancel || waitResult != null) {
                        return@awaitEachGesture
                    }

                    // 超时未抬起且未滑动：进入按住拖动范围连选模式
                    val baseSelection = currentSelected
                    val selectMode = anchor !in baseSelection
                    val allowedValues = currentItems.map { it.value }
                    currentOnSelectionChange(
                        CourseRules.computeRangeDragSelection(
                            baseSelection = baseSelection,
                            anchor = anchor,
                            current = anchor,
                            selectMode = selectMode,
                            allowedValues = allowedValues,
                        ),
                    )
                    currentHaptic.performHapticFeedback(HapticFeedbackType.LongPress)

                    var lastHovered = anchor
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                        if (change == null || !change.pressed) {
                            change?.consume()
                            break
                        }
                        change.consume()
                        val hovered = findItemAt(change.position, itemBounds)
                        if (hovered != null && hovered != lastHovered) {
                            lastHovered = hovered
                            val nextSelection = CourseRules.computeRangeDragSelection(
                                baseSelection = baseSelection,
                                anchor = anchor,
                                current = hovered,
                                selectMode = selectMode,
                                allowedValues = currentItems.map { it.value },
                            )
                            currentOnSelectionChange(nextSelection)
                            currentHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { item ->
                    val isSelected = item.value in selected
                    CourseDragGridCell(
                        item = item,
                        isSelected = isSelected,
                        animationsEnabled = animationsEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coordinates ->
                                itemBounds[item.value] =
                                    coordinates.boundsInRoot().translate(-gridRootOffset)
                            },
                    )
                }
                repeat(safeColumns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CourseDragGridCell(
    item: CourseDragSelectGridItem,
    isSelected: Boolean,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorSpec = if (animationsEnabled) tween<Color>(160) else snap()
    val cornerSpec = if (animationsEnabled) tween<androidx.compose.ui.unit.Dp>(180) else snap()

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = colorSpec,
        label = "drag_cell_container_${item.value}",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = colorSpec,
        label = "drag_cell_content_${item.value}",
    )
    val supportingColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = colorSpec,
        label = "drag_cell_supporting_${item.value}",
    )
    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 24.dp else 12.dp,
        animationSpec = cornerSpec,
        label = "drag_cell_corner_${item.value}",
    )

    Surface(
        modifier = modifier
            .heightIn(min = if (item.supporting != null) 52.dp else 44.dp)
            .semantics {
                role = Role.Checkbox
                selected = isSelected
            },
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.supporting != null) {
                Text(
                    text = item.supporting,
                    style = MaterialTheme.typography.labelSmall,
                    color = supportingColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun findItemAt(
    position: Offset,
    itemBounds: Map<Int, Rect>,
): Int? = itemBounds.entries.firstOrNull { (_, rect) -> rect.contains(position) }?.key
