package com.star.schedule.feature.update.presentation

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    when (downloadState) {
        is UpdateDownloadState.Starting -> Text(
            text = stringResource(R.string.update_download_starting),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 16.dp),
        )

        is UpdateDownloadState.Downloading -> {
            val total = downloadState.totalBytes
            val progress = if (total != null && total > 0) {
                (downloadState.bytesDownloaded.toFloat() / total).coerceIn(0f, 1f)
            } else {
                null
            }
            UpdateProgressActions(
                progress = progress,
                label = downloadProgressLabel(downloadState),
                onCancel = onCancelDownload,
            )
        }

        is UpdateDownloadState.Verifying -> UpdateProgressActions(
            progress = 1f,
            label = stringResource(R.string.update_download_verifying),
            onCancel = onCancelDownload,
        )

        is UpdateDownloadState.Ready -> Button(
            onClick = onInstall,
            enabled = !state.isPreparingInstall,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text(
                text = stringResource(
                    if (state.isPreparingInstall) {
                        R.string.update_download_verifying
                    } else {
                        R.string.update_install
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        is UpdateDownloadState.Failed, UpdateDownloadState.Idle -> {
            val availableTarget = (downloadState as? UpdateDownloadState.Failed)
                ?.target ?: target
            if (availableTarget != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledIconButton(
                        onClick = { onOpenRelease(availableTarget) },
                        enabled = !checking,
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_github),
                            contentDescription = stringResource(
                                R.string.update_open_github_download,
                            ),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Button(
                        onClick = { onDownload(availableTarget) },
                        enabled = !checking,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.update_download),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            } else {
                Button(
                    onClick = onCheckForUpdates,
                    enabled = !checking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (checking) {
                                R.string.update_checking
                            } else {
                                R.string.update_check_for_updates
                            },
                        ),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            }
        }
    }
}


@Composable
private fun formatBytes(bytes: Long): String =
    Formatter.formatShortFileSize(LocalContext.current, bytes.coerceAtLeast(0))

@Composable
private fun UpdateProgressActions(
    progress: Float?,
    label: String,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UpdateProgressCapsule(
            progress = progress,
            label = label,
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(
            onClick = onCancel,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.update_cancel),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

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

@Composable
private fun UpdateProgressCapsule(
    progress: Float?,
    label: String,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.primaryContainer
    val fill = MaterialTheme.colorScheme.primary
    if (progress != null) {
        Box(
            modifier = modifier
                .heightIn(min = 56.dp)
                .progressSemantics(progress)
                .clip(CircleShape)
                .background(track)
                .drawBehind {
                    drawRect(fill, size = Size(size.width * progress, size.height))
                },
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .drawWithContent {
                        clipRect(right = size.width * progress) { this@drawWithContent.drawContent() }
                    }
                    .clearAndSetSemantics { }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    } else {
        Box(
            modifier = modifier
                .heightIn(min = 56.dp)
                .progressSemantics()
                .clip(CircleShape)
                .background(track),
        ) {
            LinearProgressIndicator(
                color = fill.copy(alpha = 0.20f),
                trackColor = track,
                modifier = Modifier
                    .matchParentSize()
                    .clearAndSetSemantics { },
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
