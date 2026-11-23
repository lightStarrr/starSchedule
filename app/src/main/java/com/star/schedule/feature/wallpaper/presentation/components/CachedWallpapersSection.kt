package com.star.schedule.feature.wallpaper.presentation.components

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import com.star.schedule.feature.wallpaper.domain.CachedWallpaper
import com.star.schedule.feature.wallpaper.domain.RecentWallpaper
import com.star.schedule.feature.wallpaper.domain.formatSolidColorHex
import com.star.schedule.feature.wallpaper.presentation.GeneratedWallpaperBackground
import com.star.schedule.feature.wallpaper.presentation.WallpaperThumbnail

private const val CACHED_GRID_COLUMNS = 3
private val CACHED_GRID_SPACING = 12.dp
private const val CACHED_CARD_ASPECT_RATIO = 0.75f

/**
 * 壁纸页的「已缓存的壁纸」管理区块：
 * 展示共享缓存中的照片、表情与纯色历史壁纸，支持点击重新预览应用以及移除未在使用的壁纸。
 */
@Composable
internal fun CachedWallpapersSection(
    items: List<CachedWallpaper>,
    onPreview: (RecentWallpaper) -> Unit,
    onDelete: (RecentWallpaper) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.wallpaper_cached_title),
            style = MaterialTheme.typography.titleMedium,
        )

        if (items.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.wallpaper_cached_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            val previewViewport = LocalWindowInfo.current.containerSize
            val density = LocalDensity.current
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val cellWidthDp = (maxWidth - CACHED_GRID_SPACING * (CACHED_GRID_COLUMNS - 1)) /
                    CACHED_GRID_COLUMNS
                val cellWidthPx = with(density) { cellWidthDp.roundToPx().coerceAtLeast(1) }
                val cellHeightPx = (cellWidthPx / CACHED_CARD_ASPECT_RATIO).toInt().coerceAtLeast(1)
                val targetSizePx = IntSize(cellWidthPx, cellHeightPx)
                val targetMaxPx = maxOf(cellWidthPx, cellHeightPx)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items.chunked(CACHED_GRID_COLUMNS).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(CACHED_GRID_SPACING),
                        ) {
                            rowItems.forEach { item ->
                                CachedWallpaperTile(
                                    item = item,
                                    previewViewport = previewViewport,
                                    targetSizePx = targetSizePx,
                                    targetMaxPx = targetMaxPx,
                                    onPreview = { onPreview(item.wallpaper) },
                                    onDelete = { onDelete(item.wallpaper) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(CACHED_GRID_COLUMNS - rowItems.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CachedWallpaperTile(
    item: CachedWallpaper,
    previewViewport: IntSize,
    targetSizePx: IntSize,
    targetMaxPx: Int,
    onPreview: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val label = when (val wallpaper = item.wallpaper) {
        is RecentWallpaper.Image -> if (item.sizeBytes > 0L) {
            stringResource(
                R.string.wallpaper_cached_photo_label,
                Formatter.formatShortFileSize(context, item.sizeBytes),
            )
        } else {
            stringResource(R.string.wallpaper_cached_photo_fallback)
        }
        is RecentWallpaper.Generated -> when {
            wallpaper.design.emoji != null -> stringResource(R.string.emoji_wallpaper_title)
            wallpaper.design.solidArgb != null -> formatSolidColorHex(wallpaper.design.solidArgb)
            else -> stringResource(R.string.wallpaper_title)
        }
        RecentWallpaper.Default -> stringResource(R.string.wallpaper_none)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            onClick = onPreview,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CACHED_CARD_ASPECT_RATIO),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                when (val wallpaper = item.wallpaper) {
                    is RecentWallpaper.Image -> WallpaperThumbnail(
                        path = wallpaper.path,
                        cacheKey = wallpaper.contentHash,
                        preferredTargetPx = targetMaxPx,
                        modifier = Modifier.fillMaxSize(),
                    )
                    is RecentWallpaper.Generated -> GeneratedWallpaperBackground(
                        design = wallpaper.design,
                        previewViewport = previewViewport,
                        preferredSizePx = targetSizePx,
                        modifier = Modifier.fillMaxSize(),
                    )
                    RecentWallpaper.Default -> Unit
                }
                if (!item.inUse) {
                    Surface(
                        onClick = onDelete,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        ),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(
                                    R.string.wallpaper_cached_delete_description,
                                ),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
