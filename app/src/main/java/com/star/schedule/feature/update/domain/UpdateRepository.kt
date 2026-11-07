package com.star.schedule.feature.update.domain

import kotlinx.coroutines.flow.Flow

interface UpdateRepository {
    suspend fun check(trigger: UpdateCheckTrigger): UpdateCheckResult

    fun observeIncludeSnapshots(): Flow<Boolean>

    suspend fun setIncludeSnapshots(enabled: Boolean)
}

interface UpdatePreferences {
    fun observeIncludeSnapshots(): Flow<Boolean>

    suspend fun setIncludeSnapshots(enabled: Boolean)
}

sealed interface UpdateFetchOutcome {
    val issues: List<UpdateIssue>

    data class Success(
        val release: UpdateRelease,
        override val issues: List<UpdateIssue> = emptyList(),
    ) : UpdateFetchOutcome

    data class Failure(
        val issue: UpdateIssue,
        override val issues: List<UpdateIssue> = emptyList(),
    ) : UpdateFetchOutcome
}

interface UpdateReleaseSource {
    suspend fun fetchRelease(channel: UpdateChannel): UpdateFetchOutcome
}

interface UpdateCacheStore {
    suspend fun read(key: String): String?

    suspend fun write(key: String, value: String)
}
