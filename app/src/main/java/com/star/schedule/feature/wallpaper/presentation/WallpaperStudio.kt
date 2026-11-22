package com.star.schedule.feature.wallpaper.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import com.star.schedule.feature.emoji.domain.EMOJI_LAYOUT_SEED
import com.star.schedule.feature.emoji.domain.EmojiPattern
import com.star.schedule.feature.emoji.domain.EmojiWallpaperSpec
import com.star.schedule.feature.emoji.domain.emojiWallpaperPalettes
import com.star.schedule.feature.wallpaper.domain.WallpaperDesign

@Composable
internal fun WallpaperStudio(
    emojiFontInstalled: Boolean,
    onMakeEmojiWallpaper: () -> Unit,
    onPreview: (WallpaperDesign?) -> Unit,
) {
    val context = LocalContext.current
    val systemSeed = remember(context) { dynamicLightColorScheme(context).primary.toArgb() }
    val colors = remember(systemSeed) { emojiWallpaperPalettes(systemSeed).first() }
    val emoji = remember(colors) {
        WallpaperDesign(
            emoji = EmojiWallpaperSpec(
                emojis = listOf("🐢", "✌"),
                pattern = EmojiPattern.STACK,
                scale = 0.5f,
                colors = colors,
                seed = EMOJI_LAYOUT_SEED,
            ),
        )
    }
    val solid = MaterialTheme.colorScheme.primaryContainer.toArgb()
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            stringResource(R.string.wallpaper_studio),
            style = MaterialTheme.typography.titleMedium,
        )
        StudioTile(
            label = stringResource(R.string.emoji_wallpaper_title),
            onClick = onMakeEmojiWallpaper,
            aspectRatio = 2.08f,
        ) {
            if (emojiFontInstalled) {
                GeneratedWallpaperBackground(emoji, Modifier.fillMaxSize())
            } else {
                // 未下载 Noto Emoji 字体前不渲染系统表情，改在卡片中央显示表情图标占位。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(colors.backgroundArgb)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EmojiEmotions,
                        contentDescription = null,
                        tint = Color(colors.emojiArgb),
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StudioTile(
                label = stringResource(R.string.wallpaper_solid),
                onClick = { onPreview(WallpaperDesign(solidArgb = solid)) },
                modifier = Modifier.weight(1f),
                aspectRatio = 2f,
            ) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer))
            }
            StudioTile(
                label = stringResource(R.string.wallpaper_none),
                onClick = { onPreview(null) },
                modifier = Modifier.weight(1f),
                aspectRatio = 2f,
                outlined = true,
            ) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer))
            }
        }
    }
}

@Composable
private fun StudioTile(
    label: String,
    onClick: () -> Unit,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(32.dp),
            border = if (outlined) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .heightIn(min = 64.dp)
                .semantics { contentDescription = label },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
        }
        Text(
            label,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
