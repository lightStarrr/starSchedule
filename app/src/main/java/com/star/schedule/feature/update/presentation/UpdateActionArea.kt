package com.star.schedule.feature.update.presentation

import android.text.format.Formatter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.star.schedule.R
import com.star.schedule.feature.update.domain.UpdateDownloadState
import com.star.schedule.feature.update.domain.UpdateRelease

@Composable
internal fun UpdateActionArea(
    state: SoftwareUpdateUiState,
    downloadState: UpdateDownloadState,
    target: UpdateRelease?,
    onCheckForUpdates: () -> Unit,
    onDownload: (UpdateRelease) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenRelease: (UpdateRelease) -> Unit,
) {
    val checking = state.isChecking || state.isRestoringDownloads
    var leftCircle: UpdateCircleAction? = null
    var rightCircle: UpdateCircleAction? = null
    val cancelCircle = UpdateCircleAction(
        icon = rememberVectorPainter(Icons.Rounded.Close),
        contentDescription = stringResource(R.string.update_cancel),
        enabled = true,
        onClick = onCancelDownload,
    )
    val pill: UpdatePillContent = when (downloadState) {
        is UpdateDownloadState.Starting -> {
            rightCircle = cancelCircle
            UpdatePillContent.Progress(
                progress = 0f,
                label = stringResource(R.string.update_download_starting),
            )
        }

        is UpdateDownloadState.Downloading -> {
            val total = downloadState.totalBytes
            rightCircle = cancelCircle
            UpdatePillContent.Progress(
                progress = if (total != null && total > 0) {
                    (downloadState.bytesDownloaded.toFloat() / total).coerceIn(0f, 1f)
                } else {
                    null
                },
                label = downloadProgressLabel(downloadState),
            )
        }

        is UpdateDownloadState.Verifying -> {
            rightCircle = cancelCircle
            UpdatePillContent.Progress(
                progress = 1f,
                label = stringResource(R.string.update_download_verifying),
            )
        }

        is UpdateDownloadState.Ready -> UpdatePillContent.Label(
            text = stringResource(
                if (state.isPreparingInstall) {
                    R.string.update_download_verifying
                } else {
                    R.string.update_install
                },
            ),
            enabled = !state.isPreparingInstall,
            onClick = onInstall,
        )

        is UpdateDownloadState.Failed, UpdateDownloadState.Idle -> {
            val availableTarget = (downloadState as? UpdateDownloadState.Failed)
                ?.target ?: target
            if (availableTarget != null) {
                leftCircle = UpdateCircleAction(
                    icon = painterResource(R.drawable.ic_github),
                    contentDescription = stringResource(
                        R.string.update_open_github_download,
                    ),
                    enabled = !checking,
                    onClick = { onOpenRelease(availableTarget) },
                )
                UpdatePillContent.Label(
                    text = stringResource(R.string.update_download),
                    enabled = !checking,
                    onClick = { onDownload(availableTarget) },
                )
            } else {
                UpdatePillContent.Label(
                    text = stringResource(
                        if (checking) {
                            R.string.update_checking
                        } else {
                            R.string.update_check_for_updates
                        },
                    ),
                    enabled = !checking,
                    onClick = onCheckForUpdates,
                )
            }
        }
    }
    UpdateMetaballActions(
        pill = pill,
        leftCircle = leftCircle,
        rightCircle = rightCircle,
    )
}

@Composable
private fun formatBytes(bytes: Long): String =
    Formatter.formatShortFileSize(LocalContext.current, bytes.coerceAtLeast(0))

@Composable
private fun downloadProgressLabel(
    downloadState: UpdateDownloadState.Downloading,
): String {
    val total = downloadState.totalBytes
    return when {
        downloadState.waiting -> stringResource(R.string.update_download_waiting)
        total != null && total > 0 -> stringResource(
            R.string.update_download_progress,
            formatBytes(downloadState.bytesDownloaded),
            formatBytes(total),
        )

        else -> stringResource(
            R.string.update_download_progress_unknown,
            formatBytes(downloadState.bytesDownloaded),
        )
    }
}
