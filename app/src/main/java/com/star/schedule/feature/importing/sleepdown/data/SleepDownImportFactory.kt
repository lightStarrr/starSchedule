package com.star.schedule.feature.importing.sleepdown.data

import com.star.schedule.feature.importing.sharecode.data.RoomImportedTimetableWriter
import com.star.schedule.feature.importing.sleepdown.domain.ImportSleepDownScheduleUseCase
import com.star.schedule.feature.timetable.domain.TimetableRepository

fun createSleepDownImportUseCase(
    repository: TimetableRepository,
    defaultTimetableName: String,
): ImportSleepDownScheduleUseCase = ImportSleepDownScheduleUseCase(
    source = OkHttpSleepDownShareSource(),
    parser = SleepDownShareParser(),
    writer = RoomImportedTimetableWriter(
        repository = repository,
        defaultTimetableName = defaultTimetableName,
    ),
)
