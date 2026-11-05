package com.star.schedule.app

import android.content.Context
import android.os.Environment
import com.star.schedule.BuildConfig
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.feature.update.data.DefaultUpdateDownloadRepository
import com.star.schedule.feature.update.data.DefaultUpdateRepository
import com.star.schedule.feature.update.data.FileUpdateCacheStore
import com.star.schedule.feature.update.data.GitHubReleaseSource
import com.star.schedule.feature.update.data.RoomUpdatePreferences
import com.star.schedule.feature.update.domain.CurrentInstallation
import com.star.schedule.feature.update.domain.UpdateChannel
import com.star.schedule.feature.update.domain.UpdateDownloadRepository
import com.star.schedule.feature.update.domain.UpdateRepository
import com.star.schedule.feature.update.presentation.StartupUpdateGate
import com.star.schedule.platform.update.AndroidUpdateDownloader
import com.star.schedule.platform.update.AndroidUpdateInstaller
import com.star.schedule.platform.update.AndroidUpdatePackageVerifier
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class UpdateDependencies(
    context: Context,
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val installed: CurrentInstallation = CurrentInstallation(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE.toLong(),
        commitSha = BuildConfig.GIT_FULL_SHA,
        channel = when (BuildConfig.BUILD_CHANNEL) {
            "snapshot" -> UpdateChannel.Snapshot
            else -> UpdateChannel.Stable
        },
    )

    private val httpClient = OkHttpClient.Builder()
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val cacheStore = FileUpdateCacheStore(
        directory = File(context.noBackupFilesDir, "update-cache"),
        ioDispatcher = ioDispatcher,
    )

    private val source = GitHubReleaseSource(
        httpClient = httpClient,
        ioDispatcher = ioDispatcher,
    )

    val repository: UpdateRepository = DefaultUpdateRepository(
        source = source,
        preferences = RoomUpdatePreferences(DatabaseProvider.dao()),
        installed = installed,
        scope = scope,
        ioDispatcher = ioDispatcher,
    )

    val startupGate: StartupUpdateGate = StartupUpdateGate(repository, scope)

    private val updatesDirectory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?.let { File(it, "updates") }

    val downloadRepository: UpdateDownloadRepository = DefaultUpdateDownloadRepository(
        downloader = AndroidUpdateDownloader(context, ioDispatcher),
        verifier = AndroidUpdatePackageVerifier(context, ioDispatcher),
        recordStore = cacheStore,
        updatesDirectory = updatesDirectory,
        installed = installed,
        expectedPackageName = context.packageName,
        scope = scope,
        ioDispatcher = ioDispatcher,
    )

    val installer = AndroidUpdateInstaller(context, updatesDirectory)

    init {
        scope.launch {
            (downloadRepository as? DefaultUpdateDownloadRepository)?.cleanupAfterInstall()
        }
    }
}
