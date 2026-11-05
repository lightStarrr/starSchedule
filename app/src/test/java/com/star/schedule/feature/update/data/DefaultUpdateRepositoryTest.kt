package com.star.schedule.feature.update.data

import com.star.schedule.feature.update.domain.CommitRelation
import com.star.schedule.feature.update.domain.UpdateChannel
import com.star.schedule.feature.update.domain.UpdateChangelog
import com.star.schedule.feature.update.domain.UpdateCheckTrigger
import com.star.schedule.feature.update.domain.UpdateFetchOutcome
import com.star.schedule.feature.update.domain.UpdateIssue
import com.star.schedule.feature.update.domain.UpdateIssueReason
import com.star.schedule.feature.update.domain.UpdatePreferences
import com.star.schedule.feature.update.domain.UpdateRelease
import com.star.schedule.feature.update.domain.UpdateReleaseSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultUpdateRepositoryTest {
    private fun kotlinx.coroutines.test.TestScope.testScope() =
        CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    private class FakePreferences : UpdatePreferences {
        val includeSnapshots = MutableStateFlow(false)

        override fun observeIncludeSnapshots() = includeSnapshots

        override suspend fun setIncludeSnapshots(enabled: Boolean) {
            includeSnapshots.value = enabled
        }
    }

    private class FakeSource : UpdateReleaseSource {
        val outcomes = mutableMapOf<UpdateChannel, UpdateFetchOutcome>()
        val fetchCalls = mutableListOf<UpdateChannel>()
        val compareCalls = mutableListOf<Pair<String, String>>()
        var compareResult = CommitRelation.Unknown
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun fetchRelease(channel: UpdateChannel): UpdateFetchOutcome {
            fetchCalls += channel
            gate?.await()
            return outcomes[channel]
                ?: UpdateFetchOutcome.Failure(UpdateIssue(channel, UpdateIssueReason.Network))
        }

        override suspend fun compareCommits(baseSha: String, headSha: String): CommitRelation {
            compareCalls += baseSha to headSha
            return compareResult
        }
    }

    private fun release(
        channel: UpdateChannel,
        versionCode: Long,
        sha: String = SHA_B,
    ) = UpdateRelease(
        releaseId = versionCode,
        metadataAssetId = 1,
        apkAssetId = 2,
        tag = "t",
        channel = channel,
        versionName = "v$versionCode",
        versionCode = versionCode,
        commitSha = sha,
        apkName = "app.apk",
        apkSize = 1,
        apkSha256 = "d".repeat(64),
        apkDownloadUrl = "https://github.com/lightStarrr/starSchedule/releases/download/t/app.apk",
        htmlUrl = "https://github.com/lightStarrr/starSchedule/releases/tag/t",
        changelog = UpdateChangelog(),
    )

    private fun repository(
        source: FakeSource,
        preferences: FakePreferences,
        scope: kotlinx.coroutines.CoroutineScope,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        installed: com.star.schedule.feature.update.domain.CurrentInstallation = INSTALLED,
    ) = DefaultUpdateRepository(
        source = source,
        preferences = preferences,
        installed = installed,
        scope = scope,
        ioDispatcher = dispatcher,
        clock = { 42L },
    )

    @Test
    fun `concurrent checks merge into one in-flight request`() = runTest {
        val source = FakeSource()
        val preferences = FakePreferences()
        source.outcomes[UpdateChannel.Stable] =
            UpdateFetchOutcome.Success(release(UpdateChannel.Stable, 130))
        source.gate = CompletableDeferred()
        val repository = repository(source, preferences, testScope(), StandardTestDispatcher(testScheduler))

        val first = async { repository.check(UpdateCheckTrigger.Manual) }
        val second = async { repository.check(UpdateCheckTrigger.ColdStart) }
        advanceUntilIdle()
        assertEquals(1, source.fetchCalls.size)
        source.gate!!.complete(Unit)
        assertEquals(130L, first.await().target?.versionCode)
        assertEquals(130L, second.await().target?.versionCode)
    }

    @Test
    fun `snapshot is only fetched when the switch is on`() = runTest {
        val source = FakeSource()
        val preferences = FakePreferences()
        val repository = repository(source, preferences, testScope(), StandardTestDispatcher(testScheduler))

        repository.check(UpdateCheckTrigger.Manual)
        assertEquals(listOf(UpdateChannel.Stable), source.fetchCalls)

        preferences.includeSnapshots.value = true
        source.outcomes[UpdateChannel.Snapshot] =
            UpdateFetchOutcome.Success(release(UpdateChannel.Snapshot, 130))
        val result = repository.check(UpdateCheckTrigger.Manual)
        assertEquals(130L, result.target?.versionCode)
        assertEquals(UpdateChannel.Snapshot, result.target?.channel)
        assertEquals(
            listOf(UpdateChannel.Stable, UpdateChannel.Stable, UpdateChannel.Snapshot),
            source.fetchCalls,
        )
    }

    @Test
    fun `changing the switch invalidates an in-flight result`() = runTest {
        val source = FakeSource()
        val preferences = FakePreferences()
        source.gate = CompletableDeferred()
        val repository = repository(source, preferences, testScope(), StandardTestDispatcher(testScheduler))

        val pending = async { repository.check(UpdateCheckTrigger.Manual) }
        advanceUntilIdle()
        assertEquals(listOf(UpdateChannel.Stable), source.fetchCalls)

        repository.setIncludeSnapshots(true)
        val fresh = async { repository.check(UpdateCheckTrigger.Manual) }
        advanceUntilIdle()
        assertEquals(
            listOf(UpdateChannel.Stable, UpdateChannel.Stable, UpdateChannel.Snapshot),
            source.fetchCalls,
        )
        source.gate!!.complete(Unit)
        pending.await()
        assertTrue(fresh.await().includeSnapshots)
    }

    @Test
    fun `partial failure still recommends the working channel`() = runTest {
        val source = FakeSource()
        val preferences = FakePreferences()
        preferences.includeSnapshots.value = true
        source.outcomes[UpdateChannel.Stable] =
            UpdateFetchOutcome.Success(release(UpdateChannel.Stable, 130))
        val repository = repository(source, preferences, testScope(), StandardTestDispatcher(testScheduler))

        val result = repository.check(UpdateCheckTrigger.Manual)
        assertEquals(130L, result.target?.versionCode)
        assertEquals(
            UpdateIssueReason.Network,
            result.issues.single { it.channel == UpdateChannel.Snapshot }.reason,
        )
    }

    @Test
    fun `same code snapshot asks for the commit relation before updating`() = runTest {
        val source = FakeSource()
        val preferences = FakePreferences()
        preferences.includeSnapshots.value = true
        val installed = INSTALLED.copy(
            channel = UpdateChannel.Snapshot,
            commitSha = SHA_A,
        )
        source.outcomes[UpdateChannel.Snapshot] =
            UpdateFetchOutcome.Success(release(UpdateChannel.Snapshot, 120, sha = SHA_B))
        source.compareResult = CommitRelation.Ahead
        val repository = repository(source, preferences, testScope(), StandardTestDispatcher(testScheduler), installed)

        val result = repository.check(UpdateCheckTrigger.Manual)
        assertEquals(listOf(SHA_A to SHA_B), source.compareCalls)
        assertEquals(120L, result.target?.versionCode)
        assertEquals(UpdateChannel.Snapshot, result.target?.channel)

        source.compareResult = CommitRelation.Behind
        val behind = repository.check(UpdateCheckTrigger.Manual)
        assertNull(behind.target)
    }

    @Test
    fun `check result never pretends success when every channel fails`() = runTest {
        val repository = repository(FakeSource(), FakePreferences(), testScope(), StandardTestDispatcher(testScheduler))
        val result = repository.check(UpdateCheckTrigger.Manual)
        assertNull(result.target)
        assertEquals(UpdateIssueReason.Network, result.issues.single().reason)
    }

    private companion object {
        const val SHA_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val SHA_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        val INSTALLED = com.star.schedule.feature.update.domain.CurrentInstallation(
            versionName = "1.2.0",
            versionCode = 120,
            commitSha = SHA_A,
            channel = com.star.schedule.feature.update.domain.UpdateChannel.Stable,
        )
    }
}
