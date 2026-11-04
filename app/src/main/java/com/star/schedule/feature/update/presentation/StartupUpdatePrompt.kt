package com.star.schedule.feature.update.presentation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.feature.settings.presentation.StartupHintUiState
import com.star.schedule.feature.update.domain.UpdateRelease

@Composable
fun StartupUpdateRoute(
    viewModel: StartupUpdateViewModel,
    startupHintState: StartupHintUiState,
    onOpenUpdate: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow
        .collectAsState()
    val interactive = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
        startupHintState is StartupHintUiState.Hidden
    val release = state.release
    LaunchedEffect(interactive, release?.identity) {
        if (interactive && release != null && !state.dismissed) {
            viewModel.onInteractive(release.identity)
        }
    }
    if (interactive && release != null && state.canShowPrompt && !state.dismissed) {
        StartupUpdatePrompt(
            release = release,
            onViewUpdate = {
                viewModel.dismiss()
                onOpenUpdate()
            },
            onDismiss = viewModel::dismiss,
        )
    }
}

@Composable
internal fun StartupUpdatePrompt(
    release: UpdateRelease,
    onViewUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_prompt_title)) },
        text = {
            Text(stringResource(R.string.update_prompt_message, release.versionName))
        },
        confirmButton = {
            TextButton(onClick = onViewUpdate) {
                Text(stringResource(R.string.update_prompt_view))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_prompt_later))
            }
        },
    )
}
