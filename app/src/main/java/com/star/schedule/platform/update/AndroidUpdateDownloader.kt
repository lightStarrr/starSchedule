package com.star.schedule.platform.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.star.schedule.feature.update.domain.UpdateDownloadQuery
import com.star.schedule.feature.update.domain.UpdateDownloadStatus
import com.star.schedule.feature.update.domain.UpdateDownloader
import com.star.schedule.feature.update.domain.UpdateRelease
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidUpdateDownloader(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UpdateDownloader {
    private val appContext = context.applicationContext

    override fun isAvailable(): Boolean =
        appContext.getSystemService(DownloadManager::class.java) != null

    override suspend fun enqueue(target: UpdateRelease, destinationPath: String): Long =
        withContext(ioDispatcher) {
            val manager = appContext.getSystemService(DownloadManager::class.java)
                ?: error("DownloadManager unavailable")
            val request = DownloadManager.Request(Uri.parse(target.apkDownloadUrl))
                .setMimeType(APK_MIME_TYPE)
                .setTitle(target.apkName)
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                )
                .setDestinationUri(Uri.fromFile(File(destinationPath)))
            manager.enqueue(request)
        }

    override suspend fun query(downloadId: Long): UpdateDownloadQuery? =
        withContext(ioDispatcher) {
            val manager = appContext.getSystemService(DownloadManager::class.java) ?: return@withContext null
            manager.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (cursor == null || !cursor.moveToFirst()) return@withContext null
                val status = when (
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                ) {
                    DownloadManager.STATUS_SUCCESSFUL -> UpdateDownloadStatus.Successful
                    DownloadManager.STATUS_FAILED -> UpdateDownloadStatus.Failed
                    DownloadManager.STATUS_PAUSED -> UpdateDownloadStatus.Paused
                    DownloadManager.STATUS_PENDING -> UpdateDownloadStatus.Pending
                    else -> UpdateDownloadStatus.Running
                }
                val reason = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON),
                )
                val localUri = runCatching {
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI),
                    )
                }.getOrNull()
                UpdateDownloadQuery(
                    status = status,
                    bytesDownloaded = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                    ),
                    totalBytes = cursor.getLong(
                        cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                    ),
                    waiting = status == UpdateDownloadStatus.Paused && reason in setOf(
                        DownloadManager.PAUSED_WAITING_FOR_NETWORK,
                        DownloadManager.PAUSED_WAITING_TO_RETRY,
                        DownloadManager.PAUSED_QUEUED_FOR_WIFI,
                    ),
                    localPath = localUri
                        ?.takeIf { it.startsWith("file://") }
                        ?.let { Uri.parse(it).path },
                    failure = if (status == UpdateDownloadStatus.Failed) {
                        when (reason) {
                            DownloadManager.ERROR_INSUFFICIENT_SPACE ->
                                com.star.schedule.feature.update.domain.UpdateDownloadFailure
                                    .InsufficientSpace
                            DownloadManager.ERROR_DEVICE_NOT_FOUND,
                            DownloadManager.ERROR_CANNOT_RESUME,
                            -> com.star.schedule.feature.update.domain.UpdateDownloadFailure
                                .MissingFile
                            else -> com.star.schedule.feature.update.domain
                                .UpdateDownloadFailure.Network
                        }
                    } else {
                        null
                    },
                )
            }
        }

    override suspend fun cancel(downloadId: Long) {
        withContext(ioDispatcher) {
            appContext.getSystemService(DownloadManager::class.java)?.remove(downloadId)
        }
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
