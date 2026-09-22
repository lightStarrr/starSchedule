package com.star.schedule.feature.importing.wakeup.data

import com.star.schedule.feature.importing.wakeup.domain.ImportWakeUpScheduleUseCase
import com.star.schedule.feature.timetable.domain.TimetableRepository

fun createWakeUpImportUseCase(
    repository: TimetableRepository,
    defaultTimetableName: String,
): ImportWakeUpScheduleUseCase = ImportWakeUpScheduleUseCase(
    source = OkHttpWakeUpShareSource(),
    parser = WakeUpShareParser(),
    writer = RoomWakeUpScheduleWriter(
        repository = repository,
        defaultTimetableName = defaultTimetableName,
    ),
)
