package com.star.schedule.feature.settings.domain

interface StartupHintRepository {
    suspend fun isSuppressed(): Boolean

    suspend fun suppress()
}
