package com.star.schedule.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = state.doNotShowAgain,
                            enabled = !state.isSaving,
                            role = Role.Checkbox,
                            onValueChange = onDoNotShowAgainChange,
                        ),
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
                TextButton(onClick = onConfirm, enabled = !state.isSaving) {
                    Text(stringResource(R.string.action_close))
                }
            }
        },
    )
}
