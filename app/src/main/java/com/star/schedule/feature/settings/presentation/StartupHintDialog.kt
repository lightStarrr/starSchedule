package com.star.schedule.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R

@Composable
fun StartupHintRoute(viewModel: StartupHintViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    (state as? StartupHintUiState.Visible)?.let { visibleState ->
        StartupHintDialog(
            state = visibleState,
            onDoNotShowAgainChange = viewModel::setDoNotShowAgain,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismiss,
        )
    }
}

@Composable
internal fun StartupHintDialog(
    state: StartupHintUiState.Visible,
    onDoNotShowAgainChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.startup_hint_primary)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.startup_hint_secondary))
                if (state.saveFailed) {
                    Text(
                        text = stringResource(R.string.startup_hint_save_failed),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 用 Box 撑开剩余宽度、让可点区域只包住内容：
                // 既保证「关闭」贴右，又不会让水波纹横跨整行空到右边。
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(CircleShape)
                            .toggleable(
                                value = state.doNotShowAgain,
                                enabled = !state.isSaving,
                                role = Role.Checkbox,
                                onValueChange = onDoNotShowAgainChange,
                            )
                            // 放在 toggleable 之后，波纹会把这段留白也包进去，
                            // 复选框和文字就不会贴着胶囊边缘。
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = state.doNotShowAgain,
                            onCheckedChange = null,
                            enabled = !state.isSaving,
                        )
                        Text(
                            text = stringResource(R.string.startup_hint_do_not_show_again),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                TextButton(onClick = onConfirm, enabled = !state.isSaving) {
                    Text(stringResource(R.string.action_close))
                }
            }
        },
    )
}
