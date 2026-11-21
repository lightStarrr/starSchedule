package com.star.schedule.feature.wallpaper.presentation

import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import com.star.schedule.feature.emoji.data.EmojiTypefaceLoader
import com.star.schedule.feature.emoji.domain.emojiWallpaperLayout
import com.star.schedule.feature.emoji.presentation.drawEmojiWallpaper
import com.star.schedule.feature.wallpaper.domain.WallpaperDesign
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun GeneratedWallpaperBackground(
    design: WallpaperDesign,
    modifier: Modifier = Modifier,
    previewViewport: IntSize? = null,
) {
    val context = LocalContext.current
    val fontFile = remember(context) {
        File(context.filesDir, "optional-resources/noto-emoji/NotoEmoji-3.002.ttf")
    }
    val typeface by produceState(Typeface.DEFAULT_BOLD, fontFile, fontFile.lastModified()) {
        value = withContext(Dispatchers.IO) {
            fontFile.takeIf(File::isFile)
                ?.let { runCatching { EmojiTypefaceLoader.load(it) }.getOrNull() }
                ?: Typeface.DEFAULT_BOLD
        }
    }
    val spec = design.emoji
    Box(
        modifier.fillMaxSize()
            .then(
                if (design.blurRadius > 0f) {
                    Modifier.blur(design.blurRadius.dp, BlurredEdgeTreatment.Rectangle)
                } else {
                    Modifier
                },
            )
            .drawWithCache {
                // 缩略图模拟完整窗口，再缩放裁切；不能拿方形槽位重新生成堆叠图案，
                // 因为图案会根据窗口比例切换行列，结果与课表实际壁纸不同。
                val viewport = previewViewport?.takeIf { it.width > 0 && it.height > 0 }
                val width = viewport?.width?.toFloat() ?: size.width
                val height = viewport?.height?.toFloat() ?: size.height
                val scale = maxOf(size.width / width, size.height / height)
                val layout = spec?.let {
                    emojiWallpaperLayout(
                        pattern = it.pattern,
                        emojiCount = it.emojis.size,
                        scale = it.scale,
                        seed = it.seed,
                        width = width,
                        height = height,
                    )
                }
                onDrawBehind {
                    drawRect(Color(spec?.colors?.backgroundArgb ?: requireNotNull(design.solidArgb)))
                    if (spec != null && layout != null) {
                        withTransform({
                            translate((size.width - width * scale) / 2, (size.height - height * scale) / 2)
                            scale(scale, scale, androidx.compose.ui.geometry.Offset.Zero)
                        }) {
                            drawIntoCanvas { canvas ->
                                drawEmojiWallpaper(
                                    canvas = canvas.nativeCanvas,
                                    width = width.toInt(),
                                    height = height.toInt(),
                                    spec = spec,
                                    layout = layout,
                                    typeface = typeface,
                                )
                            }
                        }
                    }
                }
            },
    )
}

