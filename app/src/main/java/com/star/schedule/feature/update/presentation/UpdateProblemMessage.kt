package com.star.schedule.feature.update.presentation

import androidx.annotation.StringRes
import com.star.schedule.R
import com.star.schedule.feature.update.domain.UpdateDownloadFailure

sealed interface UpdateProblem {
    data class Download(val reason: UpdateDownloadFailure) : UpdateProblem
    data object InstallerUnavailable : UpdateProblem
    data object ReleasePageUnavailable : UpdateProblem
    data object SettingsUnavailable : UpdateProblem
}

data class UpdateProblemMessage(
    val token: Long,
    val problem: UpdateProblem,
)

@StringRes
internal fun UpdateProblem.messageRes(): Int = when (this) {
    is UpdateProblem.Download -> when (reason) {
        UpdateDownloadFailure.Unavailable ->
            R.string.update_download_failed_unavailable
        UpdateDownloadFailure.Network ->
            R.string.update_download_failed_network
        UpdateDownloadFailure.Storage ->
            R.string.update_status_storage
        UpdateDownloadFailure.InsufficientSpace ->
            R.string.update_download_failed_space
        UpdateDownloadFailure.MissingTask ->
            R.string.update_download_failed_task
        UpdateDownloadFailure.MissingFile ->
            R.string.update_download_failed_file
        UpdateDownloadFailure.SizeMismatch ->
            R.string.update_download_failed_size
        UpdateDownloadFailure.HashMismatch ->
            R.string.update_download_failed_hash
        UpdateDownloadFailure.PackageMismatch ->
            R.string.update_download_failed_package
        UpdateDownloadFailure.VersionMismatch ->
            R.string.update_download_failed_version
        UpdateDownloadFailure.SignatureMismatch ->
            R.string.update_download_failed_signature
        UpdateDownloadFailure.Unknown ->
            R.string.update_download_failed_unknown
    }

    UpdateProblem.InstallerUnavailable ->
        R.string.update_open_installer_failed
    UpdateProblem.ReleasePageUnavailable ->
        R.string.update_open_release_failed
    UpdateProblem.SettingsUnavailable ->
        R.string.update_open_settings_failed
}
