package com.star.schedule.feature.wallpaper.domain

import com.star.schedule.feature.wallpaper.domain.RecentWallpaperEntry.Companion.KIND_IMAGE
import com.star.schedule.feature.wallpaper.domain.RecentWallpaperEntry.Companion.KIND_GENERATED

const val MAX_RECENT_WALLPAPER_IMAGES = 10

/**
 * 每次成功设置壁纸后更新最近列表：
 * 同一项（default 或同内容哈希的图片）先去重、再放到最前；
 * 图片与参数壁纸合计最多 [maxImages] 个（default 不计），超出时淘汰最旧的。
 */
internal fun promoteRecentEntry(
    entries: List<RecentWallpaperEntry>,
    applied: RecentWallpaperEntry,
    maxImages: Int = MAX_RECENT_WALLPAPER_IMAGES,
): List<RecentWallpaperEntry> {
    val rest = entries.filterNot { existing -> sameRecentEntry(existing, applied) }
    val promoted = listOf(applied) + rest
    var images = 0
    return promoted.filter { entry ->
        (entry.kind != KIND_IMAGE && entry.kind != KIND_GENERATED) || ++images <= maxImages
    }
}

internal fun sameRecentEntry(
    a: RecentWallpaperEntry,
    b: RecentWallpaperEntry,
): Boolean = when {
    a.kind != b.kind -> false
    a.kind == KIND_IMAGE -> a.contentHash != null && a.contentHash == b.contentHash
    a.kind == KIND_GENERATED -> a.design == b.design
    else -> true
}

/** 从最近壁纸列表中移除指定条目（不移除系统「默认」条目）。 */
internal fun removeRecentEntry(
    entries: List<RecentWallpaperEntry>,
    target: RecentWallpaperEntry,
): List<RecentWallpaperEntry> {
    if (target.kind == RecentWallpaperEntry.KIND_DEFAULT) return entries
    return entries.filterNot { existing -> sameRecentEntry(existing, target) }
}
