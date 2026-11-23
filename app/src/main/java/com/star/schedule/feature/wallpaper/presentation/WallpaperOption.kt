package com.star.schedule.feature.wallpaper.presentation

import androidx.compose.runtime.Immutable
import com.star.schedule.feature.wallpaper.domain.WallpaperDesign

/**
 * 壁纸选项：系统默认背景，或一张本地图片。
 * [Image.contentHash] 是同一张图的稳定标识：当前壁纸在课表目录、
 * 最近壁纸在共享目录，路径不同但哈希相同，比较是否同一项要用它。
 */
@Immutable
internal sealed interface WallpaperOption {
    data object Default : WallpaperOption
    data class Generated(val design: WallpaperDesign) : WallpaperOption
    data class Image(
        val path: String,
        val contentHash: String,
        val scrimEnabled: Boolean = true,
    ) : WallpaperOption
}

/**
 * 是否是同一个壁纸项：「默认」与「默认」相同；图片按内容哈希判等，
 * 不比较路径（同一文件存到不同目录后路径会变）。
 */
internal fun WallpaperOption.sameAs(other: WallpaperOption?): Boolean = when {
    this is WallpaperOption.Default && other is WallpaperOption.Default -> true
    this is WallpaperOption.Generated && other is WallpaperOption.Generated ->
        design == other.design
    this is WallpaperOption.Image && other is WallpaperOption.Image ->
        contentHash == other.contentHash
    else -> false
}

/**
 * 最近壁纸列表。「默认」与图片一样也是历史中的一项；当前正在用的不重复出现
 * （图片按内容哈希判等，当前项与历史项的存储路径不同）。
 */
internal fun recentWallpaperOptions(
    current: WallpaperOption,
    history: List<WallpaperOption>,
): List<WallpaperOption> = history.filterNot { it.sameAs(current) }

/**
 * 壁纸条目的显示顺序（参考系统「壁纸与风格」）：选中项原地展开成方形，
 * 其余项位置不动；只有显示的选中项不在可见格内时才整排重建、把选中项排第一。
 *
 * [previous] 上一次组合出的顺序；[available] 是 [实际当前] 加去重后的最近
 * （最新在前）；[selected] 是显示为选中的项（含乐观选中）；[slotCount] 可见格数。
 * 返回完整顺序，可能长于 [slotCount]，渲染时截断。
 */
internal fun reconcileWallpaperOrder(
    previous: List<WallpaperOption>,
    available: List<WallpaperOption>,
    selected: WallpaperOption,
    slotCount: Int,
): List<WallpaperOption> {
    fun rebuild() = listOf(selected) + available.filterNot { it.sameAs(selected) }
    if (previous.isEmpty() ||
        previous.take(slotCount).none { it.sameAs(selected) } ||
        available.none { it.sameAs(selected) }
    ) {
        return rebuild()
    }
    // 保留旧顺序，换成 available 里的最新对象（路径可能更新），已不存在的项丢弃；
    // available 里没在旧顺序出现的新项追加到末尾。
    val kept = previous.mapNotNull { p -> available.firstOrNull { it.sameAs(p) } }
    return kept + available.filterNot { a -> kept.any { it.sameAs(a) } }
}
