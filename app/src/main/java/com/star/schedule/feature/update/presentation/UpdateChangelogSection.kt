package com.star.schedule.feature.update.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import com.star.schedule.feature.update.domain.UpdateRelease

@Composable
internal fun UpdateChangelogSection(
    release: UpdateRelease?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        when {
            release == null -> Text(
                text = stringResource(
                    if (isLoading) {
                        R.string.update_changelog_loading
                    } else {
                        R.string.update_changelog_unavailable
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> {
                val changelog = release.changelog
                if (changelog.added.isEmpty() &&
                    changelog.changed.isEmpty() &&
                    changelog.fixed.isEmpty()
                ) {
                    Text(
                        text = stringResource(R.string.update_changelog_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    if (changelog.added.isNotEmpty()) {
                        UpdateChangelogGroup(
                            title = stringResource(R.string.update_changelog_added),
                            entries = changelog.added,
                        )
                    }
                    if (changelog.changed.isNotEmpty()) {
                        UpdateChangelogGroup(
                            title = stringResource(R.string.update_changelog_improved),
                            entries = changelog.changed,
                        )
                    }
                    if (changelog.fixed.isNotEmpty()) {
                        UpdateChangelogGroup(
                            title = stringResource(R.string.update_changelog_fixed),
                            entries = changelog.fixed,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateChangelogGroup(
    title: String,
    entries: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        entries.forEach { entry ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .size(4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                )
                Text(
                    text = entry,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
