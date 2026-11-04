package com.star.schedule.feature.update.data

import com.star.schedule.feature.update.domain.UpdateCacheStore
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileUpdateCacheStore(
    private val directory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UpdateCacheStore {
    private val lock = Any()

    override suspend fun read(key: String): String? = withContext(ioDispatcher) {
        synchronized(lock) {
            fileFor(key).takeIf { it.isFile }?.readText(Charsets.UTF_8)
        }
    }

    override suspend fun write(key: String, value: String): Unit = withContext(ioDispatcher) {
        synchronized(lock) {
            directory.mkdirs()
            val target = fileFor(key)
            val temp = File(directory, "${target.name}.tmp")
            temp.writeText(value, Charsets.UTF_8)
            try {
                Files.move(
                    temp.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun fileFor(key: String) = File(directory, "$key.json")
}
