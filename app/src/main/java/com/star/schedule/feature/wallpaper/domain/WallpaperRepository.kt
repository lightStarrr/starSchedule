package com.star.schedule.feature.wallpaper.domain

import kotlinx.coroutines.flow.Flow

interface WallpaperRepository {
    fun observe(timetableId: Long): Flow<WallpaperState>

    suspend fun importFromUri(timetableId: Long, uriString: String): Result<Unit>

    suspend fun clear(timetableId: Long)
}
