package com.star.schedule.feature.update.data

import com.star.schedule.feature.update.domain.CommitRelation
import com.star.schedule.feature.update.domain.CurrentInstallation
import com.star.schedule.feature.update.domain.UpdateChannel
import com.star.schedule.feature.update.domain.UpdateCheckResult
import com.star.schedule.feature.update.domain.UpdateCheckTrigger
import com.star.schedule.feature.update.domain.UpdateFetchOutcome
import com.star.schedule.feature.update.domain.UpdateIssue
import com.star.schedule.feature.update.domain.UpdatePreferences
import com.star.schedule.feature.update.domain.UpdateRelease
import com.star.schedule.feature.update.domain.UpdateReleaseSource
import com.star.schedule.feature.update.domain.UpdateRepository
import com.star.schedule.feature.update.domain.UpdateSelectionPolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Deferred

class DefaultUpdateRepository(
    private val source: UpdateReleaseSource,
    private val preferences: UpdatePreferences,
    private val installed: CurrentInstallation,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : UpdateRepository {
    private val mutex = Mutex()
    private var generation = 0L
    private var inFlight: Pair<Long, Deferred<UpdateCheckResult>>? = null

    override fun observeIncludeSnapshots(): Flow<Boolean> =
        preferences.observeIncludeSnapshots()

    override suspend fun setIncludeSnapshots(enabled: Boolean) {
        preferences.setIncludeSnapshots(enabled)
        mutex.withLock { generation++ }
    }

    override suspend fun check(trigger: UpdateCheckTrigger): UpdateCheckResult {
        val deferred = mutex.withLock {
            val current = inFlight
            if (current != null && current.first == generation && current.second.isActive) {
                current.second
            } else {
                val includeSnapshots = observeIncludeSnapshots().first()
                scope.async(ioDispatcher) { doCheck(includeSnapshots) }.also {
                    inFlight = generation to it
                }
            }
        }
        return deferred.await()
    }

    private suspend fun doCheck(includeSnapshots: Boolean): UpdateCheckResult = coroutineScope {
        val stableDeferred = async { source.fetchRelease(UpdateChannel.Stable) }
        val snapshotDeferred = if (includeSnapshots) {
            async { source.fetchRelease(UpdateChannel.Snapshot) }
        } else {
            null
        }

        val issues = mutableListOf<UpdateIssue>()
        var stale = false
        fun releaseOf(outcome: UpdateFetchOutcome): UpdateRelease? {
            issues += outcome.issues
            return when (outcome) {
                is UpdateFetchOutcome.Success -> outcome.release
                is UpdateFetchOutcome.Failure -> {
                    issues += outcome.issue
                    outcome.staleRelease?.also { stale = true }
                }
            }
        }

        val stable = releaseOf(stableDeferred.await())
        val snapshot = snapshotDeferred?.await()?.let(::releaseOf)

        val snapshotVsInstalled = if (
            snapshot != null &&
            snapshot.versionCode == installed.versionCode &&
            snapshot.commitSha != installed.commitSha &&
            installed.commitSha.isNotBlank() &&
            snapshot.commitSha.isNotBlank()
        ) {
            source.compareCommits(installed.commitSha, snapshot.commitSha)
        } else {
            null
        }
        val snapshotVsStable = if (
            snapshot != null &&
            stable != null &&
            snapshot.versionCode == stable.versionCode &&
            snapshot.commitSha != stable.commitSha &&
            snapshot.versionCode > installed.versionCode
        ) {
            source.compareCommits(stable.commitSha, snapshot.commitSha)
        } else {
            null
        }

        val selection = UpdateSelectionPolicy.select(
            installed = installed,
            includeSnapshots = includeSnapshots,
            stable = stable,
            snapshot = snapshot,
            snapshotVsInstalled = snapshotVsInstalled,
            snapshotVsStable = snapshotVsStable,
        )
        UpdateCheckResult(
            target = selection.target,
            displayedRelease = selection.displayedRelease,
            issues = issues + selection.issues,
            checkedAtEpochMillis = clock(),
            includeSnapshots = includeSnapshots,
            isStale = stale,
        )
    }
}
