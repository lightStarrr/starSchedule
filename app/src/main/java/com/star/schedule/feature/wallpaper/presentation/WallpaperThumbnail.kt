package com.star.schedule.feature.wallpaper.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 进程级缩略图缓存：宽度动画、格子换位、选用换路径时都能直接命中，
// 不必先空白一帧再解码。按实际占用字节计容，上限约 16MB。
private val thumbnailCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
}

/**
 * 壁纸缩略图：按自身尺寸取「宽高中的较大值」做方形目标采样解码——
 * 格子宽度动画期间解码目标不变，不会每帧重解。
 *
 * [cacheKey] 默认用路径；同一张图存在不同目录（当前项在课表目录、
 * 最近项在共享目录）时调用方传内容哈希，跨路径命中同一条缓存。
 *
 * 位图不主动 recycle：Image 会在后续帧继续读像素，回收时机没有保证
 * （与 WallpaperBackground 相同的考虑），交给 LRU 与 GC 释放。
 */
@Composable
internal fun WallpaperThumbnail(
    path: String,
    modifier: Modifier = Modifier,
    cacheKey: String = path,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val target = max(size.width, size.height)
    val key = "$cacheKey@$target"
    // produceState 的 value 跨 key 变化保留，新 key 先查缓存，切换不会闪空白。
    val bitmap by produceState<Bitmap?>(initialValue = null, key) {
        value = if (target <= 0) {
            null
        } else {
            thumbnailCache[key]
                ?: withContext(Dispatchers.IO) {
                    decodeThumbnail(path, target, target)
                        ?.also { thumbnailCache.put(key, it) }
                }
        }
    }
    Box(modifier.onSizeChanged { size = it }) {
        bitmap?.let { image ->
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun decodeThumbnail(path: String, targetWidth: Int, targetHeight: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (
        bounds.outWidth / (sample * 2) >= targetWidth &&
        bounds.outHeight / (sample * 2) >= targetHeight
    ) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull()
}
