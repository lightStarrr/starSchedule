package com.star.schedule.feature.update.domain

fun interface LatestReleaseSource {
    suspend fun fetchLatestReleaseTag(): String?
}
