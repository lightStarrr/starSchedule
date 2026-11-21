package com.star.schedule.feature.wallpaper.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.star.schedule.core.common.Constants
import com.star.schedule.core.database.ScheduleDao
import com.star.schedule.feature.wallpaper.domain.RecentWallpaper
import com.star.schedule.feature.wallpaper.domain.RecentWallpaperEntry
import com.star.schedule.feature.wallpaper.domain.RecentWallpaperEntry.Companion.KIND_DEFAULT
import com.star.schedule.feature.wallpaper.domain.RecentWallpaperEntry.Companion.KIND_IMAGE
import com.star.schedule.feature.wallpaper.domain.StagedWallpaper
import com.star.schedule.feature.wallpaper.domain.WallpaperBlurSpec
import com.star.schedule.feature.wallpaper.domain.WallpaperPreference
import com.star.schedule.feature.wallpaper.domain.WallpaperRepository
import com.star.schedule.feature.wallpaper.domain.WallpaperState
import com.star.schedule.feature.wallpaper.domain.WallpaperDesign
import com.star.schedule.feature.wallpaper.domain.promoteRecentEntry
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomWallpaperRepository(
    context: Context,
    private val dao: ScheduleDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WallpaperRepository {
    private val appContext = context.applicationContext
    private val cacheStore = WallpaperCacheStore(appContext)

    /** 预览页暂存目录：放还没决定要不要用的照片，与正式壁纸分开。 */
    private val previewDir = appContext.cacheDir.resolve("wallpaper-preview")

    override fun observe(timetableId: Long): Flow<WallpaperState> =
        dao.getPreferenceFlow(Constants.timetableWallpaperPreference(timetableId))
            .map { value ->
                WallpaperPreferenceFormat.decodeGenerated(value)?.let { return@map it }
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
            .flowOn(ioDispatcher)

    override suspend fun applyDesign(
        timetableId: Long,
        design: WallpaperDesign,
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            commitDesign(timetableId, design)
        }.onFailure { error ->
            if (error is CancellationException) throw error
        }
    }

    private suspend fun commitDesign(timetableId: Long, design: WallpaperDesign) {
        require(design.isValid())
        val key = Constants.timetableWallpaperPreference(timetableId)
        val old = WallpaperPreferenceFormat.decode(dao.getPreferenceFlow(key).firstOrNull())
        dao.setPreference(key, WallpaperPreferenceFormat.encodeGenerated(design))
        old?.let { cacheStore.delete(timetableId, it.fileName) }
        recordRecent(
            RecentWallpaperEntry(kind = RecentWallpaperEntry.KIND_GENERATED, design = design),
            sourceFile = null,
        )
    }

    override fun observeRecents(): Flow<List<RecentWallpaper>> =
        dao.getPreferenceFlow(Constants.PREF_WALLPAPER_RECENTS)
            .map { value ->
                // 键缺失时等价于只有「默认」。
                val entries = WallpaperRecentsFormat.decode(value)
                    ?: listOf(defaultEntry())
                val recents = entries.mapNotNull { entry ->
                    when (entry.kind) {
                        KIND_DEFAULT -> RecentWallpaper.Default
                        RecentWallpaperEntry.KIND_GENERATED -> entry.design
                            ?.takeIf(WallpaperDesign::isValid)
                            ?.let(RecentWallpaper::Generated)
                        KIND_IMAGE -> {
                            val hash = entry.contentHash ?: return@mapNotNull null
                            val file = cacheStore.recentFile(hash)
                            if (WallpaperFileHash.matches(file, hash)) {
                                RecentWallpaper.Image(file.absolutePath, hash)
                            } else {
                                null
                            }
                        }
                        else -> null
                    }
                }
                // 「默认」永远可选（即使它还没进历史）：当前是图片时用户随时能换回去。
                if (recents.any { it is RecentWallpaper.Default }) {
                    recents
                } else {
                    recents + RecentWallpaper.Default
                }
            }
            .flowOn(ioDispatcher)

    override suspend fun importFromUri(
        timetableId: Long,
        uriString: String,
    ): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val resolver = appContext.contentResolver
                val uri = Uri.parse(uriString)
                val temporaryFile = cacheStore.createTemporaryFile(timetableId)
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

                    commitWallpaper(
                        timetableId,
                        temporaryFile,
                        digest.digest().toHexString(),
                    )
                    Unit
                } finally {
                    temporaryFile.delete()
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }
        }

    override suspend fun stagePreview(uriString: String): Result<StagedWallpaper> =
        withContext(ioDispatcher) {
            runCatching {
                previewDir.mkdirs()
                val staged = File.createTempFile("preview-", ".img", previewDir)
                try {
                    val resolver = appContext.contentResolver
                    resolver.openInputStream(Uri.parse(uriString))?.use { input ->
                        staged.outputStream().use(input::copyTo)
                    } ?: error("Unable to open wallpaper Uri")
                    check(isDecodable(staged)) { "Wallpaper is not a supported image" }
                    val seedArgb = WallpaperSeedColorExtractor.extract(staged)
                    check(seedArgb != null) { "Unable to extract wallpaper color" }
                    StagedWallpaper(path = staged.absolutePath, seedArgb = seedArgb)
                } catch (error: Throwable) {
                    staged.delete()
                    if (error is CancellationException) throw error
                    throw error
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }
        }

    override fun discardStaged(path: String) {
        val file = File(path)
        // 只允许删预览目录里的文件，避免调用方传错路径把正式壁纸或别的文件删掉。
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return
        val parent = runCatching { previewDir.canonicalFile }.getOrNull() ?: return
        if (canonical.parentFile == parent) {
            file.delete()
        }
    }

    override suspend fun applyStaged(
        timetableId: Long,
        stagedPath: String,
        blur: WallpaperBlurSpec?,
    ): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val staged = File(stagedPath)
                check(staged.isFile) { "Staged wallpaper is missing" }

                // 开启模糊时先烘焙成新文件（放在目标课表目录旁的临时文件里），
                // 未开启时直接用暂存文件。
                val baked: File? = if (blur != null && blur.radiusPx > 0f) {
                    cacheStore.createTemporaryFile(timetableId).also { temp ->
                        WallpaperBaker.bake(staged, blur, temp)
                    }
                } else {
                    null
                }
                val source = baked ?: staged
                try {
                    commitWallpaper(timetableId, source, WallpaperFileHash.sha256(source))
                    Unit
                } finally {
                    baked?.delete()
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }
        }

    override suspend fun applyRecent(
        timetableId: Long,
        recent: RecentWallpaper,
    ): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                when (recent) {
                    RecentWallpaper.Default -> {
                        clearInternal(timetableId)
                        recordRecent(defaultEntry(), sourceFile = null)
                    }
                    is RecentWallpaper.Generated -> {
                        commitDesign(timetableId, recent.design)
                    }
                    is RecentWallpaper.Image -> {
                        val source = File(recent.path)
                        check(source.isFile) { "Recent wallpaper file is missing" }
                        // commitWallpaper 会负责复制进课表目录、写偏好并把该图提到
                        // 最近列表最前（共享文件已存在，storeRecent 会去重）。
                        commitWallpaper(timetableId, source, recent.contentHash)
                    }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }
        }

    override suspend fun clear(timetableId: Long) {
        withContext(ioDispatcher) {
            clearInternal(timetableId)
        }
    }

    /**
     * 把 [source]（内容哈希 [contentHash]）落为 [timetableId] 的壁纸：
     * 去重/复制进课表目录 → 校验可解码 → 取 seed（可复用旧偏好）→ 写偏好 →
     * 清理旧文件 → 记入最近列表。失败时不会留下半成品。
     */
    private suspend fun commitWallpaper(
        timetableId: Long,
        source: File,
        contentHash: String,
    ) {
        val fileName = "wallpaper-$contentHash"
        val targetFile = cacheStore.file(timetableId, fileName)
        var createdTarget: File? = null
        try {
            val oldPreference = dao.getPreferenceFlow(
                Constants.timetableWallpaperPreference(timetableId),
            ).firstOrNull().let(WallpaperPreferenceFormat::decode)

            if (!targetFile.exists()) {
                targetFile.parentFile?.mkdirs()
                source.copyTo(targetFile)
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
            recordRecent(
                RecentWallpaperEntry(
                    kind = KIND_IMAGE,
                    fileName = fileName,
                    contentHash = contentHash,
                    seedArgb = seedArgb,
                ),
                sourceFile = targetFile,
            )
        } catch (error: Throwable) {
            createdTarget?.delete()
            if (error is CancellationException) throw error
            throw error
        }
    }

    /**
     * 把一条记录提到最近列表最前；图片条目先确保共享目录里有对应文件，
     * 超出上限被淘汰的图片项连同其共享文件一起删掉。
     */
    private suspend fun recordRecent(
        entry: RecentWallpaperEntry,
        sourceFile: File?,
    ) {
        if (entry.kind == KIND_IMAGE) {
            val hash = entry.contentHash ?: return
            if (sourceFile != null && sourceFile.isFile) {
                cacheStore.storeRecent(hash, sourceFile)
            }
        }

        val key = Constants.PREF_WALLPAPER_RECENTS
        val old = WallpaperRecentsFormat.decode(
            dao.getPreferenceFlow(key).firstOrNull(),
        ) ?: listOf(defaultEntry())
        val updated = promoteRecentEntry(old, entry)
        if (updated != old) {
            dao.setPreference(key, WallpaperRecentsFormat.encode(updated))
        }

        val keptHashes = updated
            .filter { it.kind == KIND_IMAGE }
            .mapNotNull { it.contentHash }
            .toSet()
        old.filter { it.kind == KIND_IMAGE }
            .mapNotNull { it.contentHash }
            .filter { it !in keptHashes }
            .forEach(cacheStore::deleteRecent)
    }

    private suspend fun clearInternal(timetableId: Long) {
        val key = Constants.timetableWallpaperPreference(timetableId)
        val oldPreference = WallpaperPreferenceFormat.decode(
            dao.getPreferenceFlow(key).firstOrNull(),
        )
        dao.setPreference(key, "")
        oldPreference?.let { cacheStore.delete(timetableId, it.fileName) }
    }

    private fun defaultEntry() = RecentWallpaperEntry(kind = KIND_DEFAULT)

    private fun isDecodable(file: File): Boolean {
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
