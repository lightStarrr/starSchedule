package com.star.schedule.feature.settings.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class LiveNotificationIconStore(context: Context) {
    private val appContext = context.applicationContext
    private val iconFile: File
        get() = File(File(appContext.filesDir, "live_icons").apply { mkdirs() }, FILE_NAME)

    suspend fun importFromUri(uriString: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val source = appContext.contentResolver.openInputStream(Uri.parse(uriString))
                ?: error("Unable to open image")
            source.use { input ->
                val original = BitmapFactory.decodeStream(input)
                    ?: error("Unable to read image")
                val targetSize = (256 * appContext.resources.displayMetrics.density)
                    .toInt()
                    .coerceAtLeast(64)
                val scaled = scaleToMaxSide(original, targetSize)
                val target = iconFile
                File(target.parentFile, "$FILE_NAME.tmp").apply {
                    outputStream().use { output ->
                        check(scaled.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                            "Unable to save image"
                        }
                    }
                    if (target.exists()) target.delete()
                    if (!renameTo(target)) {
                        delete()
                        error("Unable to replace image")
                    }
                }
                target.absolutePath
            }
        }
    }

    fun delete(path: String?) {
        path?.takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
        if (iconFile.exists()) runCatching { iconFile.delete() }
    }

    private fun scaleToMaxSide(bitmap: Bitmap, targetSize: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
        if (maxSide <= targetSize) return bitmap
        return bitmap.scale(
            (bitmap.width * targetSize / maxSide).coerceAtLeast(1),
            (bitmap.height * targetSize / maxSide).coerceAtLeast(1),
        )
    }

    private companion object {
        const val FILE_NAME = "custom_live_icon.png"
    }
}
