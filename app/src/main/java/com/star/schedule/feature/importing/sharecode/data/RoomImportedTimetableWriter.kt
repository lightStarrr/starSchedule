package com.star.schedule.feature.importing.sharecode.data

import com.star.schedule.core.database.entity.CourseEntity
import com.star.schedule.core.database.entity.CourseSessionEntity
import com.star.schedule.core.database.entity.LessonTimeEntity
import com.star.schedule.core.database.entity.TimetableEntity
import com.star.schedule.feature.importing.sharecode.domain.ImportedTimetablePlan
import com.star.schedule.feature.importing.sharecode.domain.ImportedTimetableWriter
import com.star.schedule.feature.timetable.domain.TimetableRepository
import java.time.LocalDate

class RoomImportedTimetableWriter(
    private val repository: TimetableRepository,
    private val defaultTimetableName: String,
    private val currentDate: () -> LocalDate = LocalDate::now,
) : ImportedTimetableWriter {
    override suspend fun write(plan: ImportedTimetablePlan): Long {
        val timetableId = repository.insertTimetableWithReminders(
            TimetableEntity(
                name = plan.timetableName ?: defaultTimetableName,
                showWeekend = plan.showWeekend,
                startDate = plan.startDate ?: currentDate().toString(),
            ),
        )

        plan.lessonTimes.forEach { lessonTime ->
            repository.insertOrUpdateLessonTimeAutoSort(
                LessonTimeEntity(
                    timetableId = timetableId,
                    period = lessonTime.period,
                    startTime = lessonTime.startTime,
                    endTime = lessonTime.endTime,
                ),
            )
        }

        val groupedCourses = plan.courses
            .groupBy { it.name.trim().ifEmpty { it.name } }
            .map { (name, items) ->
                CourseEntity(
                    timetableId = timetableId,
                    name = name,
                ) to items.map { item ->
                    CourseSessionEntity(
                        courseId = 0L,
                        teacher = item.teacher,
                        location = item.location,
                        dayOfWeek = item.dayOfWeek,
                        periods = item.periods,
                        weeks = item.weeks,
                    )
                }
            }
        if (groupedCourses.isNotEmpty()) {
            repository.replaceCoursesForTimetable(timetableId, groupedCourses)
        }

        return timetableId
    }
}
