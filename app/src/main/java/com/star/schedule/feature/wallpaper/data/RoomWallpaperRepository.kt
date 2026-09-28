package com.star.schedule.feature.wallpaper.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.star.schedule.core.common.Constants
import com.star.schedule.core.database.ScheduleDao
import com.star.schedule.feature.wallpaper.domain.WallpaperPreference
import com.star.schedule.feature.wallpaper.domain.WallpaperRepository
import com.star.schedule.feature.wallpaper.domain.WallpaperState
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomWallpaperRepository(
    context: Context,
    private val dao: ScheduleDao,
) : WallpaperRepository {
    private val appContext = context.applicationContext
    private val cacheStore = WallpaperCacheStore(appContext)

    override fun observe(timetableId: Long): Flow<WallpaperState> =
        dao.getPreferenceFlow(Constants.timetableWallpaperPreference(timetableId))
            .map { value ->
                val preference = WallpaperPreferenceFormat.decode(value)
                if (preference == null) {
                    if (!value.isNullOrBlank()) {
                        dao.setPreference(
                            Constants.timetableWallpaperPreference(timetableId),
                            "",
                        )
                    }
                    WallpaperState.None
                } else {
                    val file = cacheStore.file(timetableId, preference.fileName)
                    if (!WallpaperFileHash.matches(file, preference.contentHash) || !isDecodable(file)) {
                        cacheStore.delete(timetableId, preference.fileName)
                        dao.setPreference(
                            Constants.timetableWallpaperPreference(timetableId),
                            "",
                        )
                        WallpaperState.None
                    } else {
                        val seedArgb = if (
                            WallpaperPreferenceFormat.canReuseSeed(
                                preference,
                                preference.contentHash,
                                WallpaperSeedColorExtractor.VERSION,
                            )
                        ) {
                            preference.seedArgb
                        } else {
                            WallpaperSeedColorExtractor.extract(file)
                        }
                        if (seedArgb == null) {
                            cacheStore.delete(timetableId, preference.fileName)
                            dao.setPreference(
                                Constants.timetableWallpaperPreference(timetableId),
                                "",
                            )
                            return@map WallpaperState.None
                        }
                        if (preference.seedArgb != seedArgb ||
                            preference.extractorVersion != WallpaperSeedColorExtractor.VERSION
                        ) {
                            dao.setPreference(
                                Constants.timetableWallpaperPreference(timetableId),
                                WallpaperPreferenceFormat.encode(
                                    preference.copy(
                                        seedArgb = seedArgb,
                                        extractorVersion = WallpaperSeedColorExtractor.VERSION,
                                    ),
                                ),
                            )
                        }
                        WallpaperState.Ready(
                            localPath = file.absolutePath,
                            contentHash = preference.contentHash,
                            seedArgb = seedArgb,
                        )
                    }
                }
            }
            .flowOn(Dispatchers.IO)

    override suspend fun importFromUri(timetableId: Long, uriString: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = appContext.contentResolver
                val uri = Uri.parse(uriString)
                val temporaryFile = cacheStore.createTemporaryFile(timetableId)
                var createdTarget: java.io.File? = null
                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    resolver.openInputStream(uri)?.use { input ->
                        temporaryFile.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                digest.update(buffer, 0, read)
                                output.write(buffer, 0, read)
                            }
                        }
                    } ?: error("Unable to open wallpaper Uri")

                    val contentHash = digest.digest().toHexString()
                    val fileName = "wallpaper-$contentHash"
                    val targetFile = cacheStore.file(timetableId, fileName)
                    val oldPreference = dao.getPreferenceFlow(
                        Constants.timetableWallpaperPreference(timetableId),
                    ).firstOrNull().let(WallpaperPreferenceFormat::decode)

                    if (targetFile.exists()) {
                        temporaryFile.delete()
                    } else if (!temporaryFile.renameTo(targetFile)) {
                        error("Unable to store wallpaper cache")
                    } else {
                        createdTarget = targetFile
                    }

                    check(isDecodable(targetFile)) { "Wallpaper is not a supported image" }
                    val seedArgb = if (
                        WallpaperPreferenceFormat.canReuseSeed(
                            oldPreference,
                            contentHash,
                            WallpaperSeedColorExtractor.VERSION,
                        )
                    ) {
                        oldPreference?.seedArgb
                    } else {
                        WallpaperSeedColorExtractor.extract(targetFile)
                    }
                    check(seedArgb != null) { "Unable to extract wallpaper color" }

                    dao.setPreference(
                        Constants.timetableWallpaperPreference(timetableId),
                        WallpaperPreferenceFormat.encode(
                            WallpaperPreference(
                                fileName = fileName,
                                contentHash = contentHash,
                                seedArgb = seedArgb,
                                extractorVersion = WallpaperSeedColorExtractor.VERSION,
                            ),
                        ),
                    )
                    oldPreference?.takeIf { it.fileName != fileName }?.let {
                        cacheStore.delete(timetableId, it.fileName)
                    }
                } catch (error: Throwable) {
                    temporaryFile.delete()
                    createdTarget?.delete()
                    if (error is CancellationException) throw error
                    throw error
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }
        }

    override suspend fun clear(timetableId: Long) {
        withContext(Dispatchers.IO) {
            val key = Constants.timetableWallpaperPreference(timetableId)
            val oldPreference = WallpaperPreferenceFormat.decode(
                dao.getPreferenceFlow(key).firstOrNull(),
            )
            dao.setPreference(key, "")
            oldPreference?.let { cacheStore.delete(timetableId, it.fileName) }
        }
    }

    private fun isDecodable(file: java.io.File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0
    }

    private fun ByteArray.toHexString(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 8 * 1024
    }
}
