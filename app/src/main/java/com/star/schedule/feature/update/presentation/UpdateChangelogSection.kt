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

/**
 * 更新日志分区：静态示例，模拟远端发布说明的展示形态，尚未接入真实更新源。
 */
@Composable
internal fun UpdateChangelogSection(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        UpdateChangelogGroup(
            title = stringResource(R.string.update_changelog_added),
            entries = listOf(
                stringResource(R.string.update_changelog_sample_added),
            ),
        )
        UpdateChangelogGroup(
            title = stringResource(R.string.update_changelog_improved),
            entries = listOf(
                stringResource(R.string.update_changelog_sample_improved_layout),
                stringResource(R.string.update_changelog_sample_improved_dark_mode),
                stringResource(R.string.update_changelog_sample_improved_update_card),
                stringResource(R.string.update_changelog_sample_improved_update_screen),
            ),
        )
        UpdateChangelogGroup(
            title = stringResource(R.string.update_changelog_fixed),
            entries = listOf(
                stringResource(R.string.update_changelog_sample_fixed),
                stringResource(R.string.update_changelog_sample_fixed_switching),
            ),
        )
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
