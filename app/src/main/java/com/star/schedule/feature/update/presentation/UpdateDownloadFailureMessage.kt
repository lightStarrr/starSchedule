package com.star.schedule.feature.update.presentation

import com.star.schedule.R
import com.star.schedule.feature.update.domain.UpdateDownloadFailure

data class UpdateDownloadFailureMessage(
    val token: Long,
    val reason: UpdateDownloadFailure,
)

internal fun downloadFailureText(reason: UpdateDownloadFailure) =
    when (reason) {
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
