package com.star.schedule.feature.timetable.presentation.components

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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.star.schedule.R

/** 浮层当前在改开始还是结束。 */
enum class LessonTimeField { Start, End }

/**
 * 「新建 / 编辑课程时间」浮层：上面两张卡片选开始或结束，下面的时钟改选中的那一端。
 *
 * 目前课表设置里的两个二级页面还是空的，这个浮层先作为独立组件留着，
 * 等页面开始做的时候直接接上：时间由调用方持有，时钟只在改动时回调 [onTimeChange]。
 *
 * 时钟取值发生在重组之外，所以用 [snapshotFlow] 把指针位置转成回调；
 * 切换卡片时 `key` 会用新的时间重建时钟，不会把上一条时间带过去。
 */
@OptIn(ExperimentalMaterial3Api::class)
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

            if (errorText != null) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                ) {
                    Text(stringResource(R.string.action_save))
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
