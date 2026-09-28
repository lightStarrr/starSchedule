package com.star.schedule.feature.wallpaper.domain

import kotlinx.serialization.Serializable

sealed interface WallpaperState {
    data object None : WallpaperState

    data class Ready(
        val localPath: String,
        val contentHash: String,
        val seedArgb: Int?,
    ) : WallpaperState
}

@Serializable
data class WallpaperPreference(
    val fileName: String,
    val contentHash: String,
    val seedArgb: Int?,
    val extractorVersion: Int,
)
