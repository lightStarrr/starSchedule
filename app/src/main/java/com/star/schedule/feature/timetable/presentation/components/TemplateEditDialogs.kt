package com.star.schedule.feature.timetable.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import com.star.schedule.feature.timetable.presentation.RenameTemplateDialog
import com.star.schedule.feature.timetable.presentation.RenameTemplateError

/**
 * 模板重命名。
 *
 * 打开时全选原名：想改名直接打字，想微调也能用方向键。
 * 和保存为模板的对话框一样自动聚焦，省一次点按。
 */
@Composable
fun RenameTemplateDialog(
    state: RenameTemplateDialog,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    // 选区只在打开时全选一次。用 TextFieldValue 交给本地状态持有：
    // 如果每次重组都用 TextRange(0, length) 重建，输入会变成「打一个字替换一次」，只剩最后一个字符。
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(state.name, TextRange(0, state.name.length)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.label_template_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fieldValue,
                    onValueChange = { value ->
                        fieldValue = value
                        onNameChange(value.text)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                    isError = state.error != null,
                )
                Text(
                    text = state.error?.let { stringResource(it.textRes) }
                        ?: stringResource(R.string.rename_template_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
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

/** 有未保存改动时返回：说清楚「返回就会丢」，两个按钮都写明后果。 */
@Composable
fun DiscardTemplateChangesDialog(
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(R.string.title_discard_changes)) },
        text = { Text(stringResource(R.string.text_discard_changes)) },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text(
                    text = stringResource(R.string.action_discard),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) {
                Text(stringResource(R.string.action_keep_editing))
            }
        },
    )
}

private val RenameTemplateError.textRes: Int
    get() = when (this) {
        RenameTemplateError.EmptyName -> R.string.error_template_name_empty
        RenameTemplateError.NameTooLong -> R.string.error_template_name_too_long
        RenameTemplateError.NameTaken -> R.string.error_template_name_taken
    }
