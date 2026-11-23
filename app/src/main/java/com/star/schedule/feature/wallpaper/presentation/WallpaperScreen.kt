package com.star.schedule.feature.wallpaper.presentation

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.CompactPageTopBar
import com.star.schedule.core.designsystem.navigationBarBottomInset
import com.star.schedule.feature.datasource.domain.OptionalResourceState
import com.star.schedule.feature.emoji.presentation.WallpaperViewModel
import com.star.schedule.feature.wallpaper.domain.CachedWallpaper
import com.star.schedule.feature.wallpaper.domain.RecentWallpaper
import com.star.schedule.feature.wallpaper.domain.WallpaperDesign
import com.star.schedule.feature.wallpaper.presentation.components.CachedWallpapersSection

@Composable
fun WallpaperRoute(
    viewModel: WallpaperViewModel,
    onBack: () -> Unit,
    onPickPhoto: () -> Unit,
    onMakeEmojiWallpaper: () -> Unit,
    onPreview: (WallpaperDesign?) -> Unit,
    onPreviewCachedPhoto: (String) -> Unit,
) {
    val fontState by viewModel.emojiFontState.collectAsStateWithLifecycle()
    val cachedWallpapers by viewModel.cachedWallpapers.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val deleteFailedMessage = stringResource(R.string.wallpaper_cached_delete_failed)

    LaunchedEffect(Unit) {
        viewModel.cacheActionFailed.collect {
            snackbarHostState.showSnackbar(deleteFailedMessage)
        }
    }

    WallpaperScreen(
        emojiFontState = fontState,
        emojiFontExpectedBytes = viewModel.emojiFontExpectedBytes,
        cachedWallpapers = cachedWallpapers,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onPickPhoto = onPickPhoto,
        onMakeEmojiWallpaper = onMakeEmojiWallpaper,
        onPreview = onPreview,
        onPreviewCached = { wallpaper ->
            when (wallpaper) {
                RecentWallpaper.Default -> onPreview(null)
                is RecentWallpaper.Generated -> onPreview(wallpaper.design)
                is RecentWallpaper.Image -> onPreviewCachedPhoto(wallpaper.path)
            }
        },
        onDeleteCached = viewModel::deleteCachedWallpaper,
        onDownloadFont = viewModel::downloadEmojiFont,
        onCancelFontDownload = viewModel::cancelEmojiFontDownload,
    )
}

/**
 * 壁纸页：选照片、壁纸工作室，以及已缓存壁纸管理。
 * 表情字体没下载时点「制作表情壁纸」弹下载对话框，下完自动进编辑器。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperScreen(
    emojiFontState: OptionalResourceState,
    emojiFontExpectedBytes: Long,
    cachedWallpapers: List<CachedWallpaper> = emptyList(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onBack: () -> Unit,
    onPickPhoto: () -> Unit,
    onMakeEmojiWallpaper: () -> Unit,
    onPreview: (WallpaperDesign?) -> Unit,
    onPreviewCached: (RecentWallpaper) -> Unit = {},
    onDeleteCached: (RecentWallpaper) -> Unit = {},
    onDownloadFont: () -> Unit,
    onCancelFontDownload: () -> Unit,
) {
    var showFontDialog by rememberSaveable { mutableStateOf(false) }

    // 对话框里下载完成的瞬间：自动关掉并直接进编辑器，不用用户再点一次。
    LaunchedEffect(emojiFontState) {
        if (showFontDialog && emojiFontState is OptionalResourceState.Installed) {
            showFontDialog = false
            onMakeEmojiWallpaper()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = navigationBarBottomInset()),
            )
        },
        topBar = {
            CompactPageTopBar(
                title = stringResource(R.string.wallpaper_title),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp + navigationBarBottomInset()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            FilledTonalButton(
                onClick = onPickPhoto,
                modifier = Modifier.height(56.dp),
            ) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.wallpaper_pick_photo))
            }
            Spacer(Modifier.height(24.dp))
            WallpaperStudio(
                emojiFontInstalled = emojiFontState is OptionalResourceState.Installed,
                onMakeEmojiWallpaper = {
                    if (emojiFontState is OptionalResourceState.Installed) {
                        onMakeEmojiWallpaper()
                    } else {
                        showFontDialog = true
                    }
                },
                onPreview = onPreview,
            )
            Spacer(Modifier.height(28.dp))
            CachedWallpapersSection(
                items = cachedWallpapers,
                onPreview = onPreviewCached,
                onDelete = onDeleteCached,
            )
        }
    }

    if (showFontDialog) {
        EmojiFontDialog(
            state = emojiFontState,
            expectedBytes = emojiFontExpectedBytes,
            onDownload = onDownloadFont,
            onCancelDownload = onCancelFontDownload,
            onDismiss = { showFontDialog = false },
        )
    }
}

@Composable
private fun EmojiFontDialog(
    state: OptionalResourceState,
    expectedBytes: Long,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wallpaper_emoji_font_required_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state) {
                    is OptionalResourceState.Downloading -> {
                        CircularProgressIndicator(
                            progress = {
                                if (state.totalBytes > 0) {
                                    (state.downloadedBytes.toFloat() / state.totalBytes)
                                        .coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                            },
                        )
                        Text(
                            stringResource(
                                R.string.wallpaper_emoji_downloading,
                                Formatter.formatShortFileSize(context, state.downloadedBytes),
                                Formatter.formatShortFileSize(context, state.totalBytes),
                            ),
                        )
                    }
                    is OptionalResourceState.Failed -> Text(
                        stringResource(
                            R.string.wallpaper_emoji_download_failed,
                            state.error,
                        ),
                    )
                    else -> Text(
                        stringResource(
                            R.string.wallpaper_emoji_font_required_body,
                            Formatter.formatShortFileSize(context, expectedBytes),
                        ),
                    )
                }
            }
        },
        confirmButton = {
            when (state) {
                is OptionalResourceState.Downloading -> TextButton(
                    onClick = onCancelDownload,
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
                is OptionalResourceState.Failed -> TextButton(onClick = onDownload) {
                    Text(stringResource(R.string.action_retry))
                }
                else -> TextButton(onClick = onDownload) {
                    Text(stringResource(R.string.action_download))
                }
            }
        },
        // 下载中只有一个「取消」（中止下载）；其余状态是「取消」关掉对话框。
        dismissButton = if (state is OptionalResourceState.Downloading) {
            null
        } else {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}
