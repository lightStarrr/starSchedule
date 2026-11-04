package com.star.schedule.feature.update.data

import com.star.schedule.core.common.Constants
import com.star.schedule.core.database.ScheduleDao
import com.star.schedule.feature.update.domain.UpdatePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomUpdatePreferences(
    private val dao: ScheduleDao,
) : UpdatePreferences {
    override fun observeIncludeSnapshots(): Flow<Boolean> =
        dao.getPreferenceFlow(Constants.PREF_UPDATE_INCLUDE_SNAPSHOTS).map { it == "true" }

    override suspend fun setIncludeSnapshots(enabled: Boolean) {
        dao.setPreference(
            Constants.PREF_UPDATE_INCLUDE_SNAPSHOTS,
            if (enabled) "true" else "false",
        )
    }
}
