package com.star.schedule.feature.wallpaper.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    DisposableEffect(bitmap) {
        onDispose { bitmap?.recycle() }
    }
    bitmap?.let { image ->
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
    }
}
