package com.star.schedule.feature.wallpaper.domain

import com.star.schedule.feature.wallpaper.domain.RecentWallpaperEntry.Companion.KIND_DEFAULT
import com.star.schedule.feature.wallpaper.domain.RecentWallpaperEntry.Companion.KIND_IMAGE
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentWallpaperListTest {
    private fun image(hash: String) = RecentWallpaperEntry(
        kind = KIND_IMAGE,
        fileName = "wallpaper-$hash",
        contentHash = hash,
        seedArgb = null,
    )

    private val default = RecentWallpaperEntry(kind = KIND_DEFAULT)

    @Test
    fun `applied entry moves to the front`() {
        val old = listOf(image("a".repeat(64)), default, image("b".repeat(64)))

        assertEquals(
            listOf(default, image("a".repeat(64)), image("b".repeat(64))),
            promoteRecentEntry(old, default),
        )
    }

    @Test
    fun `same image is deduplicated by content hash`() {
        val hash = "c".repeat(64)
        val old = listOf(image(hash), default)
        val applied = image(hash).copy(fileName = "wallpaper-$hash", seedArgb = 1)

        assertEquals(
            listOf(applied, default),
            promoteRecentEntry(old, applied),
        )
    }

    @Test
    fun `image entries are capped and the oldest is evicted`() {
        val old = buildList {
            repeat(10) { add(image(("%064x").format(it + 1))) }
            add(default)
        }

        val updated = promoteRecentEntry(old, image("f".repeat(64)))

        // 新图在最前，最旧的第 10 张（hash=10）被淘汰，default 不算进上限。
        assertEquals(11, updated.size)
        assertEquals("f".repeat(64), updated.first().contentHash)
        assertEquals(10, updated.count { it.kind == KIND_IMAGE })
        assertEquals(
            listOf(default),
            updated.filter { it.kind == KIND_DEFAULT },
        )
        assertEquals(
            emptyList<RecentWallpaperEntry>(),
            updated.filter { it.contentHash == ("%064x").format(10) },
        )
    }

    @Test
    fun `default does not count toward the image cap`() {
        val old = buildList {
            add(default)
            repeat(10) { add(image(("%064x").format(it + 1))) }
        }

        val updated = promoteRecentEntry(old, default)

        assertEquals(11, updated.size)
        assertEquals(default, updated.first())
    }
}
