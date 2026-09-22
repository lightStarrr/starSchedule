package com.star.schedule.feature.importing.wakeup.data

import com.star.schedule.core.database.ScheduleDao
import com.star.schedule.feature.importing.wakeup.domain.ImportWakeUpScheduleUseCase

fun createWakeUpImportUseCase(
    dao: ScheduleDao,
    defaultTimetableName: String,
): ImportWakeUpScheduleUseCase = ImportWakeUpScheduleUseCase(
    source = OkHttpWakeUpShareSource(),
    parser = WakeUpShareParser(),
    writer = RoomWakeUpScheduleWriter(
        dao = dao,
        defaultTimetableName = defaultTimetableName,
    ),
)
