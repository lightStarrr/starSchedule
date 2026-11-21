package com.star.schedule.feature.timetable.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import kotlin.math.roundToInt

internal data class TimetableActionMenu(
    val timetableId: Long,
    val bounds: Rect,
)

internal const val TimetableActionScrimAlpha = 0.12f
internal const val TimetableActionMenuFadeInMillis = 180
internal const val TimetableActionMenuFadeOutMillis = 140

private const val TimetableActionButtonInitialScale = 0.6f
private const val TimetableActionButtonStaggerMillis = 60

private val TimetableActionButtonMinSize = 48.dp
private val TimetableActionButtonMaxSize = 88.dp
private val TimetableActionButtonGap = 12.dp

/**
 * 卡片中心浮出两个按钮：编辑名称 / 删除。
 *
 * 整层铺满屏幕接管点击，点按钮以外任何地方都收起；按钮尺寸按卡片短边收敛，
 * 卡片折叠变小的时候不会顶出卡片外。
 */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun TimetableCardActionOverlay(
    bounds: Rect,
    visible: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val horizontal = bounds.width >= bounds.height
    val buttonSize = with(density) {
        (minOf(bounds.width, bounds.height) * 0.3f).toDp()
    }.coerceIn(TimetableActionButtonMinSize, TimetableActionButtonMaxSize)
    val stackWidth =
        if (horizontal) buttonSize * 2 + TimetableActionButtonGap else buttonSize
    val stackHeight =
        if (horizontal) buttonSize else buttonSize * 2 + TimetableActionButtonGap

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Box(
            modifier = Modifier.offset {
                IntOffset(
                    (bounds.center.x - stackWidth.toPx() / 2f).roundToInt(),
                    (bounds.center.y - stackHeight.toPx() / 2f).roundToInt(),
                )
            },
        ) {
            val editButton: @Composable () -> Unit = {
                AnimatedCardActionButton(visible = visible, delayMillis = 0) {
                    TimetableActionButton(
                        label = stringResource(R.string.timetable_action_edit_name),
                        icon = Icons.Rounded.Edit,
                        shape = MaterialShapes.Cookie4Sided.toShape(),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        size = buttonSize,
                        onClick = onRename,
                    )
                }
            }
            val deleteButton: @Composable () -> Unit = {
                AnimatedCardActionButton(
                    visible = visible,
                    delayMillis = TimetableActionButtonStaggerMillis,
                ) {
                    TimetableActionButton(
                        label = stringResource(R.string.action_delete),
                        icon = Icons.Rounded.Delete,
                        shape = MaterialShapes.Ghostish.toShape(),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        size = buttonSize,
                        onClick = onDelete,
                    )
                }
            }
            if (horizontal) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(TimetableActionButtonGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    editButton()
                    deleteButton()
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(TimetableActionButtonGap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    editButton()
                    deleteButton()
                }
            }
        }
    }
}

@Composable
private fun TimetableActionButton(
    label: String,
    icon: ImageVector,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    size: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(size * 0.45f),
        )
    }
}

@Composable
internal fun RenameTimetableDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timetable_action_edit_name)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.timetable_edit_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun AnimatedCardActionButton(
    visible: Boolean,
    delayMillis: Int = 0,
    content: @Composable () -> Unit,
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible
    AnimatedVisibility(
        visibleState = transitionState,
        enter = fadeIn(tween(TimetableActionMenuFadeInMillis, delayMillis = delayMillis)) +
            scaleIn(
                initialScale = TimetableActionButtonInitialScale,
                animationSpec = tween(TimetableActionMenuFadeInMillis, delayMillis = delayMillis),
            ),
        exit = fadeOut(tween(TimetableActionMenuFadeOutMillis)) +
            scaleOut(
                targetScale = TimetableActionButtonInitialScale,
                animationSpec = tween(TimetableActionMenuFadeOutMillis),
            ),
    ) {
        content()
    }
}
