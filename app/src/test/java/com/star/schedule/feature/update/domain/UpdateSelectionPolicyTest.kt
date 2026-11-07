package com.star.schedule.feature.update.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateSelectionPolicyTest {
    private val installedStable = CurrentInstallation(
        versionName = "1.2.0",
        versionCode = 120,
        commitSha = SHA_A,
        channel = UpdateChannel.Stable,
    )
    private val installedSnapshot = installedStable.copy(
        versionName = "1.2.0-snapshot.${SHA_A.take(7)}",
        channel = UpdateChannel.Snapshot,
    )

    private fun release(
        channel: UpdateChannel,
        versionCode: Long,
        versionName: String = "1.${versionCode - 100}.0",
        sha: String = SHA_B,
    ) = UpdateRelease(
        releaseId = versionCode * 10 + channel.ordinal,
        metadataAssetId = versionCode * 100 + 1,
        apkAssetId = versionCode * 100 + 2,
        tag = if (channel == UpdateChannel.Stable) "v$versionName" else "snapshot",
        channel = channel,
        versionName = if (channel == UpdateChannel.Snapshot) {
            "$versionName-snapshot.${sha.take(7)}"
        } else {
            versionName
        },
        versionCode = versionCode,
        commitSha = sha,
        apkName = "app.apk",
        apkSize = 10_000,
        apkSha256 = "f".repeat(64),
        apkDownloadUrl = "https://github.com/lightStarrr/starSchedule/releases/download/t/app.apk",
        htmlUrl = "https://github.com/lightStarrr/starSchedule/releases/tag/t",
        changelog = UpdateChangelog(),
    )

    private fun select(
        installed: CurrentInstallation = installedStable,
        includeSnapshots: Boolean = false,
        stable: UpdateRelease? = null,
        snapshot: UpdateRelease? = null,
    ) = UpdateSelectionPolicy.select(
        installed,
        includeSnapshots,
        stable,
        snapshot,
    )

    @Test
    fun `higher stable versionCode is the target for any installed channel`() {
        val remote = release(UpdateChannel.Stable, 130)
        assertEquals(remote, select(stable = remote).target)
        assertEquals(remote, select(installed = installedSnapshot, stable = remote).target)
    }

    @Test
    fun `same or lower stable versionCode never updates`() {
        assertNull(select(stable = release(UpdateChannel.Stable, 120)).target)
        assertNull(select(stable = release(UpdateChannel.Stable, 110)).target)
    }

    @Test
    fun `snapshot is ignored entirely when switch is off`() {
        val remote = release(UpdateChannel.Snapshot, 130)
        val result = select(includeSnapshots = false, snapshot = remote)
        assertNull(result.target)
        assertEquals(remote, result.displayedRelease)
    }

    @Test
    fun `snapshot with higher versionCode is a candidate when enabled`() {
        val remote = release(UpdateChannel.Snapshot, 130)
        assertEquals(
            remote,
            select(includeSnapshots = true, snapshot = remote).target,
        )
    }

    @Test
    fun `same code snapshot updates whenever the build differs`() {
        val remote = release(UpdateChannel.Snapshot, 120, sha = SHA_B)
        assertEquals(
            remote,
            select(
                installed = installedSnapshot,
                includeSnapshots = true,
                snapshot = remote,
            ).target,
        )
    }

    @Test
    fun `same code snapshot with identical sha never updates`() {
        val remote = release(UpdateChannel.Snapshot, 120, sha = SHA_A)
        val result = select(
            installed = installedSnapshot,
            includeSnapshots = true,
            snapshot = remote,
        )
        assertNull(result.target)
        assertEquals(remote, result.displayedRelease)
    }

    @Test
    fun `missing local sha counts as a different build`() {
        val installed = installedSnapshot.copy(commitSha = "")
        val remote = release(UpdateChannel.Snapshot, 120, sha = SHA_B)
        assertEquals(
            remote,
            select(
                installed = installed,
                includeSnapshots = true,
                snapshot = remote,
            ).target,
        )
        val higher = release(UpdateChannel.Snapshot, 130, sha = SHA_B)
        assertEquals(
            higher,
            select(installed = installed, includeSnapshots = true, snapshot = higher).target,
        )
    }

    @Test
    fun `tie between eligible channels prefers higher code then snapshot`() {
        val stable = release(UpdateChannel.Stable, 130, sha = SHA_A)
        val snapshot = release(UpdateChannel.Snapshot, 130, sha = SHA_B)

        assertEquals(
            snapshot,
            select(
                includeSnapshots = true,
                stable = stable,
                snapshot = snapshot,
            ).target,
        )
        val sameCommit = release(UpdateChannel.Snapshot, 130, sha = SHA_A)
        assertEquals(
            stable,
            select(
                includeSnapshots = true,
                stable = stable,
                snapshot = sameCommit,
            ).target,
        )
    }

    @Test
    fun `higher code wins over a tie`() {
        val stable = release(UpdateChannel.Stable, 130, sha = SHA_A)
        val snapshot = release(UpdateChannel.Snapshot, 140, sha = SHA_B)
        assertEquals(
            snapshot,
            select(
                includeSnapshots = true,
                stable = stable,
                snapshot = snapshot,
            ).target,
        )
        val lowerSnapshot = release(UpdateChannel.Snapshot, 125, sha = SHA_B)
        assertEquals(
            stable,
            select(
                includeSnapshots = true,
                stable = stable,
                snapshot = lowerSnapshot,
            ).target,
        )
    }

    @Test
    fun `displayed release falls back to fetched stable then snapshot`() {
        val stable = release(UpdateChannel.Stable, 120)
        assertEquals(stable, select(stable = stable).displayedRelease)
        val snapshot = release(UpdateChannel.Snapshot, 120, sha = SHA_A)
        assertEquals(
            snapshot,
            select(
                installed = installedSnapshot,
                includeSnapshots = true,
                snapshot = snapshot,
            ).displayedRelease,
        )
    }

    private companion object {
        const val SHA_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val SHA_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}
