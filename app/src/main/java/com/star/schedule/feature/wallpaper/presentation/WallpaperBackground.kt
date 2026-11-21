package com.star.schedule.feature.wallpaper.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.star.schedule.feature.wallpaper.domain.WallpaperState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WallpaperBackground(
    state: WallpaperState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        WallpaperState.None -> Unit
        is WallpaperState.Ready -> WallpaperBackground(
            path = state.localPath,
            blurRadius = 0f,
            modifier = modifier,
        )
        is WallpaperState.Generated -> GeneratedWallpaperBackground(state.design, modifier)
    }
}

/**
 * 按路径直接画壁纸（预览页还没写成 [WallpaperState] 时用）；
 * [blurRadius] > 0 时对整图加 RenderEffect 模糊，只供预览页实时预览——
 * 正式应用时模糊会烘焙进文件，这里永远传 0。
 */
@Composable
fun WallpaperBackground(
    path: String?,
    blurRadius: Float,
    modifier: Modifier = Modifier,
) {
    // 首次布局前尺寸可能为 0，等待真实窗口尺寸后再解码，避免估算尺寸造成重复裁切。
    val targetSize = LocalWindowInfo.current.containerSize
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        path,
        targetSize,
    ) {
        value = if (path == null || targetSize.width <= 0 || targetSize.height <= 0) {
            null
        } else {
            withContext(Dispatchers.IO) {
                decodeWallpaper(path, targetSize.width, targetSize.height)
            }
        }
    }
    // 位图交给 Image 后会由渲染线程在后续帧继续读取，销毁时机与最后一次绘制
    // 没有先后保证。主动 recycle 会让渲染线程访问已释放的像素内存并 abort，
    // 因此这里不回收，改由 GC 释放。
    bitmap?.let { image ->
        val imageBitmap = remember(image) { image.asImageBitmap() }
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxSize()
                .then(
                    if (blurRadius > 0f) {
                        Modifier.blur(blurRadius.dp, BlurredEdgeTreatment.Rectangle)
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

/**
 * 只解码屏幕可见的那部分原图，返回与屏幕尺寸接近 1:1 的位图。
 *
 * 使用 [BitmapRegionDecoder] 而不是先整图解码再交给 Image 缩放，是为了让超大壁纸的
 * 内存占用只跟屏幕大小相关。
 */
private fun decodeWallpaper(
    path: String,
    targetWidth: Int,
    targetHeight: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)

    val spec = calculateWallpaperCrop(
        sourceWidth = bounds.outWidth,
        sourceHeight = bounds.outHeight,
        targetWidth = targetWidth,
        targetHeight = targetHeight,
    )
    val options = BitmapFactory.Options().apply { inSampleSize = spec.sampleSize }
    val region = Rect(spec.left, spec.top, spec.left + spec.width, spec.top + spec.height)

    val regionBitmap = runCatching {
        val decoder = BitmapRegionDecoder.newInstance(path)
        try {
            decoder.decodeRegion(region, options)
        } finally {
            decoder.recycle()
        }
    }.getOrNull()
    if (regionBitmap != null) return regionBitmap

    // 区域解码失败时退回整图解码，尺寸仍受 sampleSize 约束。
    return runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull()
}
