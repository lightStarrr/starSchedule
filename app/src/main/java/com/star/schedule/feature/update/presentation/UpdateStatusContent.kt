package com.star.schedule.feature.update.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.star.schedule.R
import com.star.schedule.feature.update.domain.UpdateChannel
import com.star.schedule.feature.update.domain.UpdateRelease

@Composable
internal fun updateStatusText(
    state: SoftwareUpdateUiState,
    downloadTarget: UpdateRelease?,
    isDownloadFailure: Boolean = false,
): String {
    if (downloadTarget != null && !isDownloadFailure) {
        return ""
    }
    if (downloadTarget != null) {
        return if (downloadTarget.channel == UpdateChannel.Snapshot) {
            stringResource(R.string.update_status_new_snapshot)
        } else {
            stringResource(R.string.update_status_new_stable)
        }
    }
    val result = state.result ?: return when {
        state.isChecking || state.isRestoringDownloads -> stringResource(R.string.update_checking)
        else -> stringResource(R.string.update_status_not_checked)
    }
    return when {
        result.target != null -> stringResource(
            if (result.target.channel == UpdateChannel.Snapshot) {
                R.string.update_status_new_snapshot
            } else {
                R.string.update_status_new_stable
            },
        )

        result.displayedRelease != null -> stringResource(R.string.update_latest_version)

        state.isChecking -> stringResource(R.string.update_checking)

        else -> stringResource(R.string.update_status_check_failed_network)
    }
}
