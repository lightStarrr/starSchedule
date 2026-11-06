package com.star.schedule.feature.update.presentation

import com.star.schedule.feature.update.domain.UpdateChannel
import com.star.schedule.feature.update.domain.UpdateChangelog
import com.star.schedule.feature.update.domain.UpdateDownloadState
import com.star.schedule.feature.update.domain.UpdateRelease
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateDownloadProgressTest {

    private fun release(apkSize: Long) = UpdateRelease(
        releaseId = 130,
        metadataAssetId = 1,
        apkAssetId = 2,
        tag = "v1.3.0",
        channel = UpdateChannel.Stable,
        versionName = "1.3.0",
        versionCode = 130,
        commitSha = "b".repeat(40),
        apkName = "app.apk",
        apkSize = apkSize,
        apkSha256 = "d".repeat(64),
        apkDownloadUrl = "https://example.com/app.apk",
        htmlUrl = "https://example.com",
        changelog = UpdateChangelog(added = listOf("x")),
    )

    private fun downloading(
        bytes: Long,
        total: Long?,
        apkSize: Long = 100,
    ) = UpdateDownloadState.Downloading(
        target = release(apkSize),
        downloadId = 1,
        bytesDownloaded = bytes,
        totalBytes = total,
        waiting = false,
    )

    @Test
    fun `null total falls back to apkSize`() {
        val state = downloading(bytes = 50, total = null, apkSize = 200)
        assertEquals(200L, state.effectiveTotalBytes())
        assertEquals(0.25f, state.progressFraction(), 1e-6f)
    }

    @Test
    fun `non-positive total falls back to apkSize`() {
        val state = downloading(bytes = 50, total = 0, apkSize = 200)
        assertEquals(200L, state.effectiveTotalBytes())
        assertEquals(0.25f, state.progressFraction(), 1e-6f)
    }

    @Test
    fun `positive total is used directly`() {
        val state = downloading(bytes = 60, total = 120, apkSize = 200)
        assertEquals(120L, state.effectiveTotalBytes())
        assertEquals(0.5f, state.progressFraction(), 1e-6f)
    }

    @Test
    fun `progress is clamped when bytes exceed total`() {
        val state = downloading(bytes = 300, total = null, apkSize = 200)
        assertEquals(1f, state.progressFraction(), 1e-6f)
    }

    @Test
    fun `progress is zero when both totals are non-positive`() {
        val state = downloading(bytes = 50, total = null, apkSize = 0)
        assertEquals(0L, state.effectiveTotalBytes())
        assertEquals(0f, state.progressFraction(), 1e-6f)
    }
}
