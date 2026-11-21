package com.star.schedule.feature.wallpaper.data

import com.star.schedule.feature.wallpaper.domain.WallpaperPreference
import com.star.schedule.feature.wallpaper.domain.WallpaperState
import com.star.schedule.feature.wallpaper.domain.WallpaperDesign
import kotlinx.serialization.json.Json

internal object WallpaperPreferenceFormat {
    private val json = Json { ignoreUnknownKeys = true }
    private val hashPattern = Regex("[0-9a-f]{64}")

    fun encodeGenerated(design: WallpaperDesign): String =
        "generated:" + json.encodeToString(WallpaperDesign.serializer(), design)

    fun decodeGenerated(value: String?): WallpaperState.Generated? = value
        ?.takeIf { it.startsWith("generated:") }
        ?.removePrefix("generated:")
        ?.let { raw ->
            runCatching { json.decodeFromString(WallpaperDesign.serializer(), raw) }
                .getOrNull()
        }
        ?.takeIf(WallpaperDesign::isValid)
        ?.let(WallpaperState::Generated)

    fun encode(preference: WallpaperPreference): String =
        json.encodeToString(WallpaperPreference.serializer(), preference)

    fun decode(value: String?): WallpaperPreference? =
        value?.takeIf(String::isNotBlank)?.let {
            runCatching {
                json.decodeFromString(WallpaperPreference.serializer(), it)
            }.getOrNull()
        }?.takeIf { preference ->
            preference.contentHash.matches(hashPattern) &&
                preference.fileName == "wallpaper-${preference.contentHash}"
        }

    fun canReuseSeed(
        preference: WallpaperPreference?,
        contentHash: String,
        extractorVersion: Int,
    ): Boolean = preference != null &&
        preference.contentHash == contentHash &&
        preference.extractorVersion == extractorVersion &&
        preference.seedArgb != null
}
