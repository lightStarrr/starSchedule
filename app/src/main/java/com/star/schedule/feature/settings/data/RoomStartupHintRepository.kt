package com.star.schedule.feature.settings.data

import com.star.schedule.core.common.Constants
import com.star.schedule.core.database.ScheduleDao
import com.star.schedule.feature.settings.domain.StartupHintRepository
import kotlinx.coroutines.flow.first

class RoomStartupHintRepository(
    private val dao: ScheduleDao,
) : StartupHintRepository {
    override suspend fun isSuppressed(): Boolean =
        dao.getPreferenceFlow(Constants.PREF_STARTUP_HINT_CLOSED).first() == "true"

    override suspend fun suppress() {
        dao.setPreference(Constants.PREF_STARTUP_HINT_CLOSED, "true")
    }
}
