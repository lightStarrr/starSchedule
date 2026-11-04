package com.star.schedule.feature.update.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import com.star.schedule.feature.update.domain.UpdateChannel
import com.star.schedule.feature.update.domain.UpdateCheckResult
import com.star.schedule.feature.update.domain.UpdateIssue
import com.star.schedule.feature.update.domain.UpdateIssueReason
import com.star.schedule.feature.update.domain.UpdateRelease
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun UpdateIssueArea(
    result: UpdateCheckResult?,
) {
    result ?: return
    val notes = buildList {
        result.issues.forEach { issue ->
            add(
                issueTextWithChannel(issue),
            )
        }
        if (result.isStale) add(stringResource(R.string.update_status_cached))
    }
    if (notes.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
    ) {
        notes.forEach { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun issueTextWithChannel(issue: UpdateIssue): String {
    val base = updateIssueText(issue.reason)
    val channelLabel = when (issue.channel) {
        UpdateChannel.Stable -> stringResource(R.string.update_channel_stable)
        UpdateChannel.Snapshot -> stringResource(R.string.update_channel_snapshot)
        null -> null
    }
    val retry = issue.retryAtEpochMillis?.let { retryAt ->
        stringResource(
            R.string.update_status_retry_at,
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(retryAt)),
        )
    }
    val message = if (channelLabel != null) {
        stringResource(R.string.update_channel_issue, channelLabel, base)
    } else {
        base
    }
    return if (retry != null) {
        stringResource(R.string.update_issue_with_retry, message, retry)
    } else {
        message
    }
}

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

        state.isChecking && result.displayedRelease == null && result.issues.isEmpty() ->
            stringResource(R.string.update_checking)

        result.issues.isEmpty() && !result.isStale -> if (
            state.installed.channel == UpdateChannel.Snapshot && !result.includeSnapshots
        ) {
            stringResource(R.string.update_status_no_newer_stable)
        } else {
            stringResource(R.string.update_latest_version)
        }

        result.displayedRelease == null -> result.issues.firstOrNull()?.let {
            updateIssueText(it.reason)
        } ?: stringResource(R.string.update_status_cached)

        else -> stringResource(R.string.update_status_partial)
    }
}

@Composable
private fun updateIssueText(reason: UpdateIssueReason): String = stringResource(
    when (reason) {
        UpdateIssueReason.Network -> R.string.update_status_check_failed_network
        UpdateIssueReason.RateLimited -> R.string.update_status_rate_limited
        UpdateIssueReason.ReleaseUnavailable -> R.string.update_status_release_unavailable
        UpdateIssueReason.MetadataUnavailable,
        UpdateIssueReason.InvalidMetadata,
        -> R.string.update_status_metadata_unavailable
        UpdateIssueReason.UnknownCommitRelation -> R.string.update_status_unknown_relation
        UpdateIssueReason.Storage -> R.string.update_status_storage
    },
)
