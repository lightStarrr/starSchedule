package com.star.schedule.feature.wallpaper.data

import android.content.Context
import java.io.File

internal class WallpaperCacheStore(
    context: Context,
) {
    private val root = context.applicationContext.filesDir
        .resolve("wallpapers")

    fun directory(timetableId: Long): File = root.resolve(timetableId.toString())

    fun file(timetableId: Long, fileName: String): File =
        directory(timetableId).resolve(fileName)

    fun createTemporaryFile(timetableId: Long): File {
        val directory = directory(timetableId).apply { mkdirs() }
        return File.createTempFile("wallpaper-", ".tmp", directory)
    }

    fun delete(timetableId: Long, fileName: String) {
        file(timetableId, fileName).takeIf(File::exists)?.delete()
    }
}
