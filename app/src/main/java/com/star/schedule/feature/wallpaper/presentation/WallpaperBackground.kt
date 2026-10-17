package com.star.schedule.feature.wallpaper.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.star.schedule.feature.wallpaper.domain.WallpaperState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WallpaperBackground(
    state: WallpaperState,
    modifier: Modifier = Modifier,
) {
    val path = (state as? WallpaperState.Ready)?.localPath
    val bitmap by produceState<Bitmap?>(initialValue = null, path) {
        value = path?.let {
            withContext(Dispatchers.IO) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(it, bounds)
                BitmapFactory.decodeFile(
                    it,
                    BitmapFactory.Options().apply {
                        inSampleSize = maxOf(bounds.outWidth, bounds.outHeight)
                            .coerceAtLeast(2048) / 2048
                    },
                )
            }
        }
    }
    // 位图交给 Image 后会由渲染线程在后续帧继续读取，销毁时机与最后一次绘制
    // 没有先后保证。主动 recycle 会让渲染线程访问已释放的像素内存并 abort，
    // 因此这里不回收，改由 GC 释放。
    bitmap?.let { image ->
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
    }
}
