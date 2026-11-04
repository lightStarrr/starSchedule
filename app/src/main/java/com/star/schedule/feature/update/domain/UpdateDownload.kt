package com.star.schedule.feature.update.domain

import kotlinx.coroutines.flow.Flow

enum class UpdateDownloadFailure {
    Unavailable,
    Network,
    Storage,
    InsufficientSpace,
    MissingTask,
    MissingFile,
    SizeMismatch,
    HashMismatch,
    PackageMismatch,
    VersionMismatch,
    SignatureMismatch,
    Unknown,
}

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState

    data class Starting(val target: UpdateRelease) : UpdateDownloadState

    data class Downloading(
        val target: UpdateRelease,
        val downloadId: Long,
        val bytesDownloaded: Long,
        val totalBytes: Long?,
        val waiting: Boolean,
    ) : UpdateDownloadState

    data class Verifying(val target: UpdateRelease) : UpdateDownloadState

    data class Ready(val target: UpdateRelease) : UpdateDownloadState

    data class Failed(
        val target: UpdateRelease?,
        val reason: UpdateDownloadFailure,
    ) : UpdateDownloadState
}

data class VerifiedUpdatePackage(
    val identity: String,
    val localPath: String,
)

sealed interface UpdateInstallPreparation {
    data class Ready(val verifiedPackage: VerifiedUpdatePackage) : UpdateInstallPreparation

    data class Failure(val reason: UpdateDownloadFailure) : UpdateInstallPreparation
}

interface UpdateDownloadRepository {
    fun observe(): Flow<UpdateDownloadState>

    suspend fun restore()

    suspend fun start(target: UpdateRelease)

    suspend fun cancel()

    suspend fun prepareInstallation(): UpdateInstallPreparation
}

interface UpdateDownloader {
    fun isAvailable(): Boolean

    suspend fun enqueue(target: UpdateRelease, destinationPath: String): Long

    suspend fun query(downloadId: Long): UpdateDownloadQuery?

    suspend fun cancel(downloadId: Long)
}

enum class UpdateDownloadStatus { Pending, Running, Paused, Successful, Failed }

data class UpdateDownloadQuery(
    val status: UpdateDownloadStatus,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val waiting: Boolean,
    val localPath: String? = null,
    val failure: UpdateDownloadFailure? = null,
)

data class UpdatePackageFacts(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val signerSha256: Set<String>,
)

interface UpdatePackageVerifier {
    suspend fun packageFacts(filePath: String): UpdatePackageFacts?

    suspend fun installedPackageFacts(): UpdatePackageFacts?
}

object UpdatePackageValidation {
    fun validate(
        fileSize: Long,
        fileSha256: String,
        facts: UpdatePackageFacts,
        target: UpdateRelease,
        installedVersionCode: Long,
        expectedPackageName: String,
        expectedSigners: Set<String>,
    ): UpdateDownloadFailure? = when {
        fileSize != target.apkSize -> UpdateDownloadFailure.SizeMismatch
        !fileSha256.equals(target.apkSha256, ignoreCase = true) ->
            UpdateDownloadFailure.HashMismatch
        facts.packageName != expectedPackageName -> UpdateDownloadFailure.PackageMismatch
        facts.versionName != target.versionName ||
            facts.versionCode != target.versionCode -> UpdateDownloadFailure.VersionMismatch
        facts.versionCode < installedVersionCode -> UpdateDownloadFailure.VersionMismatch
        facts.signerSha256.isEmpty() ||
            expectedSigners.isEmpty() ||
            facts.signerSha256 != expectedSigners -> UpdateDownloadFailure.SignatureMismatch
        else -> null
    }
}
