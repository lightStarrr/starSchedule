package com.star.schedule.feature.timetable.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import com.star.schedule.feature.timetable.presentation.LessonTimeField
import kotlin.math.roundToInt

/** 错误提示进出的时长：进得慢一点（要让用户看清是什么错），收回时快一点，别挡着继续改时间。 */
private const val ERROR_ENTER_MILLIS = 280
private const val ERROR_EXIT_MILLIS = 160

/** 出现时从下方浮起的距离，只要「轻微上浮」。 */
private val ERROR_LIFT = 12.dp

/** 出现时的起始模糊半径：配合透明度从糊到清楚，比单纯淡入更能看出「浮出来」。 */
private val ERROR_BLUR_RADIUS = 10.dp

/**
 * 「新建 / 编辑课程时间」浮层：上面两张卡片选开始或结束，下面的时钟改选中的那一端。
 *
 * 目前课表设置里的两个二级页面还是空的，这个浮层先作为独立组件留着，
 * 等页面开始做的时候直接接上：时间由调用方持有，时钟只在改动时回调 [onTimeChange]。
 *
 * 时钟取值发生在重组之外，所以用 [snapshotFlow] 把指针位置转成回调；
 * 切换卡片时 `key` 会用新的时间重建时钟，不会把上一条时间带过去。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LessonTimeEditorSheet(
    title: String,
    startTime: String,
    endTime: String,
    activeField: LessonTimeField,
    onSelectField: (LessonTimeField) -> Unit,
    onTimeChange: (LessonTimeField, hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    confirmEnabled: Boolean = true,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val activeTime = when (activeField) {
        LessonTimeField.Start -> startTime
        LessonTimeField.End -> endTime
    }
    // 退场动画期间还要有字可画：errorText 一变 null，直接读它就只剩一片空的渐隐区域。
    var shownError by remember { mutableStateOf(errorText) }
    LaunchedEffect(errorText) {
        if (errorText != null) shownError = errorText
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LessonTimeFieldCard(
                    label = stringResource(R.string.label_start_time),
                    time = startTime,
                    selected = activeField == LessonTimeField.Start,
                    onClick = { onSelectField(LessonTimeField.Start) },
                    modifier = Modifier.weight(1f),
                )
                LessonTimeFieldCard(
                    label = stringResource(R.string.label_end_time),
                    time = endTime,
                    selected = activeField == LessonTimeField.End,
                    onClick = { onSelectField(LessonTimeField.End) },
                    modifier = Modifier.weight(1f),
                )
            }

            val (hour, minute) = remember(activeTime) { parseHourMinute(activeTime) }
            key(activeField) {
                val pickerState = rememberTimePickerState(
                    initialHour = hour,
                    initialMinute = minute,
                    is24Hour = true,
                )
                LaunchedEffect(pickerState, activeField) {
                    snapshotFlow { pickerState.hour to pickerState.minute }
                        .collect { (hourOfDay, minuteOfHour) ->
                            onTimeChange(activeField, hourOfDay, minuteOfHour)
                        }
                }
                TimePicker(state = pickerState)
            }

            // 提示和按钮包成一层：外层 spacedBy 的间距就不作用在提示上，
            // 没有错误时这块高度就等于按钮本身，不会凭空多出一段空白。
            Column(modifier = Modifier.fillMaxWidth()) {
                // 高度、透明度、上浮、模糊全部由这一个进度推着走。
                // 之前用 AnimatedVisibility 串 fadeIn + expand + slide：高度是「布局」在动、
                // 位移是「图层」在动，两条曲线各走各的，进场几乎看不出浮起（消失时才明显）。
                val appear = remember { Animatable(if (errorText != null) 1f else 0f) }
                LaunchedEffect(errorText != null) {
                    val visible = errorText != null
                    appear.animateTo(
                        targetValue = if (visible) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = if (visible) ERROR_ENTER_MILLIS else ERROR_EXIT_MILLIS,
                            easing = if (visible) LinearOutSlowInEasing else FastOutLinearInEasing,
                        ),
                    )
                }
                val appearProgress = appear.value
                // 先撑开位置、后显影：透明度、模糊都取平方曲线，前段字几乎看不见，
                // 打开空间那一下就不会和字的出现抢注意力。
                val appearOpacity = appearProgress * appearProgress
                val appearBlur = ERROR_BLUR_RADIUS * (1f - appearProgress * appearProgress)
                val liftPx = with(LocalDensity.current) { ERROR_LIFT.toPx() }

                Text(
                    text = shownError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        // 高度跟着进度长出来把按钮推开，但内容不裁切：
                        // 高度还没长够时字照样完整，只是暂时压在按钮上方。
                        .errorAppearHeight(appearProgress)
                        .graphicsLayer {
                            alpha = appearOpacity
                            // 从下方一点点浮上来；数值小，只是让出现有方向感。
                            translationY = (1f - appearProgress) * liftPx
                        }
                        .blur(
                            radius = appearBlur,
                            // 模糊时边缘会往外扩，不放开边界就会看到被切掉的一圈。
                            edgeTreatment = BlurredEdgeTreatment.Unbounded,
                        ),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = onConfirm,
                        shapes = ButtonDefaults.shapes(),
                        enabled = confirmEnabled,
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonTimeFieldCard(
    label: String,
    time: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        // 时间卡用普通圆角：Cookie 形状在小尺寸上把「08:00」挤得很怪，
        // expressive 的形状语汇只留给下面的按钮。
        shape = RoundedCornerShape(20.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = time,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** 只用来给时钟定位，格式不对时退回 00:00，具体校验交给调用方。 */
private fun parseHourMinute(text: String): Pair<Int, Int> {
    val parts = text.split(":")
    val hour = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
    return hour.coerceIn(0, 23) to minute.coerceIn(0, 59)
}

/**
 * 按 [progress] 压缩这块的布局高度，但内容仍按完整尺寸测量、绘制。
 *
 * 也就是 expandVertically(clip = false) 里「改高度」的那一半：父布局以为它只有这么高，
 * 文字照旧完整画出来，所以高度还没长够时不会看到半截字。
 */
private fun Modifier.errorAppearHeight(progress: Float): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val height = (placeable.height * progress).roundToInt().coerceAtLeast(0)
        layout(placeable.width, height) {
            // 贴顶放置：高度从 0 长起来时字留在原位，不会跟着下边一起漂。
            placeable.placeRelative(0, 0)
        }
    }
