package com.star.schedule.feature.wallpaper.presentation

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.star.schedule.feature.emoji.data.EmojiTypefaceLoader
import com.star.schedule.feature.emoji.data.NotoEmojiFontResource
import com.star.schedule.feature.emoji.domain.EmojiWallpaperSpec
import com.star.schedule.feature.emoji.presentation.peekCachedEmojiWallpaper
import com.star.schedule.feature.emoji.presentation.renderOrGetEmojiWallpaper
import com.star.schedule.feature.wallpaper.domain.WallpaperDesign
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 计算表情壁纸离屏渲染尺寸：
 * - 无 [previewViewport] 时按容器实际像素渲染；
 * - 有 [previewViewport] 时（如壁纸条缩略图），以容器宽高较大值作为稳定边长，按窗口比例缩放：
 *   既保持与完整窗口一致的宽高比（防止图案重排行列），又保证槽位宽度展开/收窄动画期间
 *   缓存键恒定，避免动画帧反复重绘。
 */
internal fun computeEmojiRenderSize(
    canvasSize: IntSize,
    previewViewport: IntSize?,
): IntSize {
    if (canvasSize.width <= 0 || canvasSize.height <= 0) return IntSize.Zero
    val viewport = previewViewport?.takeIf { it.width > 0 && it.height > 0 }
        ?: return canvasSize
    val maxDim = maxOf(canvasSize.width, canvasSize.height)
    val scale = maxOf(
        maxDim.toFloat() / viewport.width.toFloat(),
        maxDim.toFloat() / viewport.height.toFloat(),
    ).coerceAtMost(1f)
    val width = (viewport.width * scale).roundToInt().coerceAtLeast(1)
    val height = (viewport.height * scale).roundToInt().coerceAtLeast(1)
    return IntSize(width, height)
}

@Composable
internal fun GeneratedWallpaperBackground(
    design: WallpaperDesign,
    modifier: Modifier = Modifier,
    previewViewport: IntSize? = null,
) {
    val spec = design.emoji
    if (spec == null) {
        // 纯色壁纸直接铺底色，无需字体加载、离屏位图或 RenderEffect 模糊层。
        Box(
            modifier
                .fillMaxSize()
                .background(Color(requireNotNull(design.solidArgb))),
        )
        return
    }

    val context = LocalContext.current
    val fontFile = remember(context) { NotoEmojiFontResource.fontFile(context.filesDir) }
    val cachedTypeface = remember(fontFile) { EmojiTypefaceLoader.peekCached(fontFile) }
    // 字体加载完成前保持 null，绝不回退到系统默认字体，避免先画系统彩色 emoji 再跳变到 Noto Emoji。
    val typeface by produceState<Typeface?>(
        initialValue = cachedTypeface,
        key1 = fontFile,
    ) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            fontFile.takeIf(File::isFile)
                ?.let { runCatching { EmojiTypefaceLoader.load(it) }.getOrNull() }
        }
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val renderSize = remember(canvasSize, previewViewport) {
        computeEmojiRenderSize(canvasSize, previewViewport)
    }

    val loadedTypeface = typeface
    val cachedBitmap = remember(spec, renderSize, loadedTypeface) {
        if (loadedTypeface != null && renderSize.width > 0 && renderSize.height > 0) {
            peekCachedEmojiWallpaper(spec, renderSize.width, renderSize.height, loadedTypeface)
        } else {
            null
        }
    }
    var renderedSpec by remember { mutableStateOf<EmojiWallpaperSpec?>(null) }
    val bitmap by produceState<Bitmap?>(
        initialValue = cachedBitmap,
        key1 = spec,
        key2 = renderSize,
        key3 = loadedTypeface,
    ) {
        if (loadedTypeface == null || renderSize.width <= 0 || renderSize.height <= 0) {
            value = null
            renderedSpec = null
            return@produceState
        }
        val hit = peekCachedEmojiWallpaper(spec, renderSize.width, renderSize.height, loadedTypeface)
        if (hit != null) {
            value = hit
            renderedSpec = spec
            return@produceState
        }
        if (renderedSpec != spec) {
            value = null
        }
        val rendered = withContext(Dispatchers.Default) {
            renderOrGetEmojiWallpaper(spec, renderSize.width, renderSize.height, loadedTypeface)
        }
        value = rendered
        renderedSpec = spec
    }

    val displayBitmap = cachedBitmap ?: bitmap
    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .background(Color(spec.colors.backgroundArgb))
            .then(
                if (design.blurRadius > 0f) {
                    Modifier.blur(design.blurRadius.dp, BlurredEdgeTreatment.Rectangle)
                } else {
                    Modifier
                },
            ),
    ) {
        if (displayBitmap != null) {
            val imageBitmap = remember(displayBitmap) { displayBitmap.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
