package com.star.schedule.feature.wallpaper.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 壁纸缩略图排的尺寸分配。
 *
 * 这里锁两件事：方块占固定比例且有上限；剩余宽度先按最小宽度数槽位，
 * 再把余量平均分掉，所以 square + count * (gap + width) 必须恰好铺满可用宽度。
 */
class WallpaperStripLayoutTest {

    @Test
    fun `typical width splits the leftover evenly between recents`() {
        val layout = wallpaperStripLayout(328f)

        assertEquals(328f * 0.38f, layout.squareSize, 0.001f)
        assertEquals(3, layout.recentCount)
        // 方块 + 每个槽位（间距 + 宽度）恰好铺满可用宽度。
        assertEquals(
            328f,
            layout.squareSize + layout.recentCount * (8f + layout.recentWidth),
            0.001f,
        )
    }

    @Test
    fun `remaining narrower than one recent slot yields none`() {
        // 剩余 55.8 < 52 + 8，一个槽位都放不下。
        val layout = wallpaperStripLayout(90f)

        assertEquals(0, layout.recentCount)
        assertEquals(0f, layout.recentWidth, 0.001f)
    }

    @Test
    fun `wide strip caps the square and adds slots`() {
        val layout = wallpaperStripLayout(688f)

        assertEquals(200f, layout.squareSize, 0.001f)
        assertEquals(8, layout.recentCount)
        assertEquals(
            688f,
            layout.squareSize + layout.recentCount * (8f + layout.recentWidth),
            0.001f,
        )
    }

    @Test
    fun `inner radius follows the concentric rule`() {
        assertEquals(12f, wallpaperThumbnailCornerRadius(28f, 16f), 0.001f)
    }

    @Test
    fun `inner radius never goes below the floor`() {
        assertEquals(4f, wallpaperThumbnailCornerRadius(10f, 16f), 0.001f)
    }

    @Test
    fun `recents do not repeat the current option`() {
        assertEquals(
            emptyList<WallpaperOption>(),
            recentWallpaperOptions(
                WallpaperOption.Default,
                listOf(WallpaperOption.Default),
            ),
        )
    }

    @Test
    fun `default shows in recents when current is an image`() {
        assertEquals(
            listOf(WallpaperOption.Default),
            recentWallpaperOptions(
                WallpaperOption.Image("/data/w.png", "a".repeat(64)),
                listOf(WallpaperOption.Default),
            ),
        )
    }

    @Test
    fun `current image is filtered by content hash even on another path`() {
        // 当前壁纸在课表目录、历史项在共享目录：路径不同但哈希相同，视为同一项。
        val hash = "b".repeat(64)
        assertEquals(
            listOf(WallpaperOption.Default),
            recentWallpaperOptions(
                WallpaperOption.Image("/data/wallpapers/1/wallpaper-$hash", hash),
                listOf(
                    WallpaperOption.Image("/data/wallpapers/recent/wallpaper-$hash", hash),
                    WallpaperOption.Default,
                ),
            ),
        )
    }

    @Test
    fun `zero width yields an empty layout`() {
        val layout = wallpaperStripLayout(0f)

        assertEquals(0f, layout.squareSize, 0.001f)
        assertEquals(0, layout.recentCount)
        assertEquals(0f, layout.recentWidth, 0.001f)
    }
}
