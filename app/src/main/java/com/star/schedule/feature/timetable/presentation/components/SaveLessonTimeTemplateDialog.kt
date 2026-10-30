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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import com.star.schedule.feature.timetable.presentation.SaveTemplateDialog
import com.star.schedule.feature.timetable.presentation.SaveTemplateError

/**
 * 「保存为节次时间模板」：只问一个名字，节次时间就是当前列表里的那份。
 *
 * 输入框自动聚焦，省掉一次多余的点按；保存中禁用全部按钮，避免连点写出两条模板。
 */
@Composable
fun SaveLessonTimeTemplateDialog(
    state: SaveTemplateDialog,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_save_template)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    enabled = !state.isSaving,
                    singleLine = true,
                    label = { Text(stringResource(R.string.label_template_name)) },
                    isError = state.error != null,
                )
                Text(
                    text = state.error?.let { stringResource(it.textRes) }
                        ?: stringResource(R.string.save_template_helper),
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
            TextButton(onClick = onConfirm, enabled = !state.isSaving) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** 同名模板已存在：覆盖会丢掉旧的那份，所以单独问一次。 */
@Composable
fun OverwriteLessonTimeTemplateDialog(
    name: String,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_overwrite_template)) },
        text = { Text(stringResource(R.string.text_overwrite_template, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSaving) {
                Text(stringResource(R.string.action_overwrite))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private val SaveTemplateError.textRes: Int
    get() = when (this) {
        SaveTemplateError.EmptyName -> R.string.error_template_name_empty
        SaveTemplateError.NameTooLong -> R.string.error_template_name_too_long
        SaveTemplateError.Failed -> R.string.error_template_save_failed
    }
