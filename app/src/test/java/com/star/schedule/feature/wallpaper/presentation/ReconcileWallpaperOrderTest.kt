package com.star.schedule.feature.wallpaper.presentation

import com.star.schedule.feature.wallpaper.presentation.WallpaperOption.Default
import com.star.schedule.feature.wallpaper.presentation.WallpaperOption.Image
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 壁纸条目的显示顺序：点最近项原地展开、顺序不变；
 * 只有选中项落在可见格之外（或首次、新课表）才重建、选中项排第一。
 */
class ReconcileWallpaperOrderTest {

    private fun image(hash: String, path: String = "/recent/$hash") = Image(path, hash)

    @Test
    fun `first build puts selected first then the rest of available`() {
        val a = image("a")
        val b = image("b")
        val available = listOf(a, Default, b)

        val order = reconcileWallpaperOrder(emptyList(), available, a, slotCount = 4)

        assertEquals(listOf(a, Default, b), order)
    }

    @Test
    fun `selecting a visible item keeps the order unchanged`() {
        val a = image("a")
        val b = image("b")
        val c = image("c")
        val available = listOf(a, Default, b, c)
        val previous = reconcileWallpaperOrder(emptyList(), available, a, slotCount = 4)

        // 点第 3 格 b：它原地展开，顺序不变。
        val order = reconcileWallpaperOrder(previous, available, b, slotCount = 4)

        assertEquals(previous, order)
        assertEquals(2, order.indexOfFirst { it.sameAs(b) })
    }

    @Test
    fun `applied item stays in place even though the repository promoted it`() {
        val a = image("a")
        val b = image("b")
        val c = image("c")
        val previous = reconcileWallpaperOrder(
            emptyList(),
            listOf(a, Default, b, c),
            a,
            slotCount = 4,
        )
        // 应用成功后仓库把 b 提到最近列表最前，b 变成当前项（路径换成课表目录）。
        val current = image("b", path = "/timetable/b")
        val available = listOf(current, image("b"), Default, a, c)

        val order = reconcileWallpaperOrder(previous, available, current, slotCount = 4)

        // 显示顺序不动；b 换成新的当前对象（课表目录路径）；重复的 b 不再追加。
        assertEquals(listOf(a, Default, current, c), order)
    }

    @Test
    fun `a current wallpaper absent from previous rebuilds with it first`() {
        val a = image("a")
        val b = image("b")
        val previous = reconcileWallpaperOrder(
            emptyList(),
            listOf(a, Default, b),
            a,
            slotCount = 4,
        )
        // 预览页刚应用了一张从没在条上出现过的新图。
        val fresh = image("fresh")
        val available = listOf(fresh, a, Default, b)

        val order = reconcileWallpaperOrder(previous, available, fresh, slotCount = 4)

        assertEquals(listOf(fresh, a, Default, b), order)
    }

    @Test
    fun `evicted previous items are removed and new ones appended at the end`() {
        val a = image("a")
        val b = image("b")
        val c = image("c")
        val evicted = image("gone")
        val previous = reconcileWallpaperOrder(
            emptyList(),
            listOf(a, Default, evicted, b),
            a,
            slotCount = 4,
        )
        // 最近列表淘汰 evicted、新来了 c。
        val available = listOf(a, Default, b, c)

        val order = reconcileWallpaperOrder(previous, available, a, slotCount = 4)

        assertEquals(listOf(a, Default, b, c), order)
        assertTrue(order.none { it.sameAs(evicted) })
    }

    @Test
    fun `selected beyond the visible slots rebuilds with it first`() {
        val a = image("a")
        val b = image("b")
        val c = image("c")
        val d = image("d")
        val available = listOf(a, Default, b, c, d)
        val previous = reconcileWallpaperOrder(emptyList(), available, a, slotCount = 3)

        // 实际当前切到 d：它在 previous 里但落在 3 个可见格之外 → 重建排第一。
        val order = reconcileWallpaperOrder(previous, available, d, slotCount = 3)

        assertEquals(listOf(d, a, Default, b, c), order)
    }

    @Test
    fun `same hash with a different path is the same item and adopts the new path`() {
        val a = image("a")
        val recent = image("b", path = "/recent/b")
        val available = listOf(a, Default, recent)
        val previous = reconcileWallpaperOrder(emptyList(), available, a, slotCount = 4)

        // b 被选用后存进课表目录：路径变了、哈希没变。
        val current = image("b", path = "/timetable/1/b")
        val newAvailable = listOf(current, Default, a)

        val order = reconcileWallpaperOrder(previous, newAvailable, current, slotCount = 4)

        assertEquals(2, order.indexOfFirst { it.sameAs(current) })
        assertEquals(current, order[2])
    }
}
