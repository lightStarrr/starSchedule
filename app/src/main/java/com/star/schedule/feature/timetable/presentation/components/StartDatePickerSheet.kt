package com.star.schedule.feature.timetable.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 「开学日期」浮层：和「新建 / 编辑课程时间」同款 ModalBottomSheet——
 * 标题、日历、右下角取消 / 保存按钮排。
 *
 * 日期点选即生效：取值发生在重组之外，用 [snapshotFlow] 把日历选中项转成回调，
 * 名称行能实时跟着日期联动；「保存」只是收起浮层，语义和时钟那边一致。
 *
 * DatePicker 内部按 UTC 毫秒处理，初始化和回读都走 UTC，避免时区把日期推前一天。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StartDatePickerSheet(
    title: String,
    initialDate: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli(),
    )
    LaunchedEffect(pickerState) {
        snapshotFlow { pickerState.selectedDateMillis }
            .collect { millis ->
                if (millis != null) {
                    onDateChange(
                        Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate(),
                    )
                }
            }
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
            // 日历默认底色是 surfaceContainerHigh，比浮层的 surfaceContainerLow 亮一档，
            // 直接放进来会像贴了张卡片；透明化后透出浮层底色，只留文字和选中态。
            DatePicker(
                state = pickerState,
                colors = DatePickerDefaults.colors(containerColor = Color.Transparent),
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
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}
