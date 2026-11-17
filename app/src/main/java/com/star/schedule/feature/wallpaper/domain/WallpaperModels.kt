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

/**
 * 预览页暂存的照片：还没应用，只是复制到应用缓存、取了取色 seed 的一张候选图。
 */
data class StagedWallpaper(
    val path: String,
    val seedArgb: Int,
)

/**
 * 把模糊烘焙进壁纸文件时需要的信息。
 *
 * [radiusPx] 是 Compose `Modifier.blur` 同款语义：dp 半径乘 density 后的像素值，
 * 之后按 Skia 的 radius→sigma 公式换算（见 WallpaperBlur）。窗口尺寸决定
 * 烘焙时图片放大到多少像素——保证烘焙结果与运行时模糊在屏幕上是 1:1。
 */
data class WallpaperBlurSpec(
    val radiusPx: Float,
    val windowWidthPx: Int,
    val windowHeightPx: Int,
)

/**
 * 最近用过的壁纸，跨课表共享。「默认」表示无壁纸的系统底色。
 */
sealed interface RecentWallpaper {
    data object Default : RecentWallpaper

    data class Image(
        val path: String,
        val contentHash: String,
    ) : RecentWallpaper
}

@Serializable
data class WallpaperPreference(
    val fileName: String,
    val contentHash: String,
    val seedArgb: Int?,
    val extractorVersion: Int,
)

/**
 * 最近壁纸列表里的一条记录，序列化进偏好 JSON。
 * [kind] 只会是 [KIND_DEFAULT] 或 [KIND_IMAGE]；image 条目必须带文件名与内容哈希。
 */
@Serializable
data class RecentWallpaperEntry(
    val kind: String,
    val fileName: String? = null,
    val contentHash: String? = null,
    val seedArgb: Int? = null,
) {
    companion object {
        const val KIND_DEFAULT = "default"
        const val KIND_IMAGE = "image"
    }
}
