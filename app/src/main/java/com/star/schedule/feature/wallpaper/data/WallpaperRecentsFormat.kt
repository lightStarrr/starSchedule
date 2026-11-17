package com.star.schedule.feature.wallpaper.data

import com.star.schedule.feature.wallpaper.domain.RecentWallpaperEntry
import com.star.schedule.feature.wallpaper.domain.RecentWallpaperEntry.Companion.KIND_DEFAULT
import com.star.schedule.feature.wallpaper.domain.RecentWallpaperEntry.Companion.KIND_IMAGE
import kotlinx.serialization.json.Json

/**
 * 最近壁纸偏好 JSON 的编解码与校验。
 * 非法条目（未知 kind、image 缺字段/哈希格式不对）直接丢弃；
 * 整个值缺失或完全无法解析时返回 null，由调用方换成默认列表。
 */
internal object WallpaperRecentsFormat {
    private val json = Json { ignoreUnknownKeys = true }
    private val hashPattern = Regex("[0-9a-f]{64}")

    fun encode(entries: List<RecentWallpaperEntry>): String =
        json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(RecentWallpaperEntry.serializer()),
            entries,
        )

    fun decode(value: String?): List<RecentWallpaperEntry>? =
        value?.takeIf(String::isNotBlank)?.let {
            runCatching {
                json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(
                        RecentWallpaperEntry.serializer(),
                    ),
                    it,
                )
            }.getOrNull()
        }?.filter(::isValid)

    private fun isValid(entry: RecentWallpaperEntry): Boolean = when (entry.kind) {
        KIND_DEFAULT -> true
        KIND_IMAGE -> {
            val hash = entry.contentHash
            hash != null &&
                hash.matches(hashPattern) &&
                entry.fileName == "wallpaper-$hash"
        }
        else -> false
    }
}
