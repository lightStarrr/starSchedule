package com.star.schedule.feature.wallpaper.domain

import kotlinx.serialization.Serializable
import com.star.schedule.feature.emoji.domain.EmojiWallpaperSpec

sealed interface WallpaperState {
    data object None : WallpaperState

    data class Generated(val design: WallpaperDesign) : WallpaperState

    data class Ready(
        val localPath: String,
        val contentHash: String,
        val seedArgb: Int?,
        val scrimEnabled: Boolean = true,
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

    data class Generated(val design: WallpaperDesign) : RecentWallpaper

    data class Image(
        val path: String,
        val contentHash: String,
        val scrimEnabled: Boolean = true,
    ) : RecentWallpaper
}

/**
 * 已缓存的壁纸条目（不含系统「默认」），供壁纸页展示占用空间与清理管理。
 * [inUse] 为 true 表示正被至少一张现存课表使用，不可删除。
 */
data class CachedWallpaper(
    val wallpaper: RecentWallpaper,
    val sizeBytes: Long,
    val inUse: Boolean = false,
)

@Serializable
data class WallpaperPreference(
    val fileName: String,
    val contentHash: String,
    val seedArgb: Int?,
    val extractorVersion: Int,
    val scrimEnabled: Boolean = true,
)

/**
 * 最近壁纸列表里的一条记录，序列化进偏好 JSON。
 * image 条目带文件名与内容哈希，generated 条目保存绘制参数，default 表示默认背景。
 */
@Serializable
data class RecentWallpaperEntry(
    val kind: String,
    val fileName: String? = null,
    val contentHash: String? = null,
    val seedArgb: Int? = null,
    val design: WallpaperDesign? = null,
    val scrimEnabled: Boolean = true,
) {
    companion object {
        const val KIND_DEFAULT = "default"
        const val KIND_IMAGE = "image"
        const val KIND_GENERATED = "generated"
    }
}

/** 保存参数而非整屏位图；每个窗口按实际画布尺寸重新计算表情布局。 */
@Serializable
data class WallpaperDesign(
    val emoji: EmojiWallpaperSpec? = null,
    val solidArgb: Int? = null,
    val blurRadius: Float = 0f,
    val scrimEnabled: Boolean = true,
) {
    val seedArgb: Int get() = emoji?.colors?.seedArgb ?: requireNotNull(solidArgb)

    fun isValid(): Boolean =
        (emoji == null) != (solidArgb == null) &&
            blurRadius.isFinite() && blurRadius in 0f..25f &&
            (solidArgb == null || solidArgb ushr 24 == 255) &&
            (emoji?.let { spec ->
                spec.emojis.size in 1..14 &&
                    spec.emojis.all { it.isNotBlank() && it.length <= 64 } &&
                    spec.scale.isFinite() && spec.scale in 0f..1f &&
                    spec.colors.backgroundArgb ushr 24 == 255 &&
                    spec.colors.emojiArgb ushr 24 == 255
            } ?: true)
}

val WallpaperState.seedArgb: Int?
    get() = when (this) {
        WallpaperState.None -> null
        is WallpaperState.Ready -> seedArgb
        is WallpaperState.Generated -> design.seedArgb
    }

val WallpaperState.showsScrim: Boolean
    get() = when (this) {
        WallpaperState.None -> false
        is WallpaperState.Ready -> scrimEnabled
        is WallpaperState.Generated -> design.scrimEnabled
    }
