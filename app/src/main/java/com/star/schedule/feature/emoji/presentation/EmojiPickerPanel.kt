package com.star.schedule.feature.emoji.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EmojiFlags
import androidx.compose.material.icons.rounded.EmojiObjects
import androidx.compose.material.icons.rounded.EmojiSymbols
import androidx.compose.material.icons.rounded.FlightTakeoff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.star.schedule.R

@Composable
internal fun EmojiPickerPanel(
    ready: EmojiWallpaperUiState.Ready,
    onSelectPickerGroup: (String) -> Unit,
    onAddEmoji: (String) -> Unit,
    onRemoveEmoji: (Int) -> Unit,
    onClearEmojis: () -> Unit,
    onConfirmPicker: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // 已选表情卡片：点表情移除，✓ 至少留一个才可点。
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(
                        items = ready.emojis,
                        key = { index, emoji -> "$index:$emoji" },
                        contentType = { _, _ -> "pickerSelectedEmoji" },
                    ) { index, emoji ->
                        EmojiGlyph(
                            emoji = emoji,
                            typeface = ready.typeface,
                            size = 26.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onRemoveEmoji(index) }
                                .padding(4.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(
                        R.string.emoji_wallpaper_selected_count,
                        ready.emojis.size,
                        EMOJI_WALLPAPER_MAX_EMOJIS,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onClearEmojis) {
                    Text(stringResource(R.string.emoji_wallpaper_clear_all))
                }
                IconButton(
                    onClick = onConfirmPicker,
                    enabled = ready.emojis.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(
                            R.string.emoji_wallpaper_done,
                        ),
                    )
                }
            }
        }

        // 分类页签 + 表情网格。
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    item(key = EMOJI_GROUP_RECENT, contentType = "groupTab") {
                        PickerGroupTab(
                            icon = Icons.Rounded.History,
                            description = stringResource(R.string.emoji_group_recent),
                            selected = ready.pickerGroupId == EMOJI_GROUP_RECENT,
                            onClick = { onSelectPickerGroup(EMOJI_GROUP_RECENT) },
                        )
                    }
                    items(
                        items = ready.groups,
                        key = { it.id },
                        contentType = { "groupTab" },
                    ) { group ->
                        PickerGroupTab(
                            icon = pickerGroupIcon(group.id),
                            description = stringResource(pickerGroupNameRes(group.id)),
                            selected = ready.pickerGroupId == group.id,
                            onClick = { onSelectPickerGroup(group.id) },
                        )
                    }
                }
                val shown = if (ready.pickerGroupId == EMOJI_GROUP_RECENT) {
                    ready.recents
                } else {
                    ready.groups.firstOrNull { it.id == ready.pickerGroupId }?.emojis
                }.orEmpty()
                if (shown.isEmpty()) {
                    Text(
                        text = stringResource(R.string.emoji_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 48.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .padding(horizontal = 8.dp),
                    ) {
                        items(
                            items = shown,
                            key = { it },
                            contentType = { "emojiCell" },
                        ) { emoji ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .clickable { onAddEmoji(emoji) },
                                contentAlignment = Alignment.Center,
                            ) {
                                EmojiGlyph(
                                    emoji = emoji,
                                    typeface = ready.typeface,
                                    size = 26.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerGroupTab(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                LocalContentColor.current
            },
        )
    }
}

private fun pickerGroupIcon(groupId: String): ImageVector = when (groupId) {
    "smileys" -> Icons.Rounded.EmojiEmotions
    "people" -> Icons.Rounded.People
    "animals" -> Icons.Rounded.Pets
    "food" -> Icons.Rounded.Restaurant
    "travel" -> Icons.Rounded.FlightTakeoff
    "activities" -> Icons.Rounded.EmojiEvents
    "objects" -> Icons.Rounded.EmojiObjects
    "symbols" -> Icons.Rounded.EmojiSymbols
    else -> Icons.Rounded.EmojiFlags
}

private fun pickerGroupNameRes(groupId: String): Int = when (groupId) {
    "smileys" -> R.string.emoji_group_smileys
    "people" -> R.string.emoji_group_people
    "animals" -> R.string.emoji_group_animals
    "food" -> R.string.emoji_group_food
    "travel" -> R.string.emoji_group_travel
    "activities" -> R.string.emoji_group_activities
    "objects" -> R.string.emoji_group_objects
    "symbols" -> R.string.emoji_group_symbols
    else -> R.string.emoji_group_flags
}
