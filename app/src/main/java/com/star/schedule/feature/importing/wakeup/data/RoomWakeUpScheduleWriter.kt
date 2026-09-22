package com.star.schedule.feature.importing.wakeup.data

import com.star.schedule.db.CourseEntity
import com.star.schedule.db.LessonTimeEntity
import com.star.schedule.db.ScheduleDao
import com.star.schedule.db.TimetableEntity
import com.star.schedule.feature.importing.wakeup.domain.WakeUpImportPlan
import com.star.schedule.feature.importing.wakeup.domain.WakeUpScheduleWriter
import java.time.LocalDate

class RoomWakeUpScheduleWriter(
    private val dao: ScheduleDao,
    private val defaultTimetableName: String,
    private val currentDate: () -> LocalDate = LocalDate::now,
) : WakeUpScheduleWriter {
    override suspend fun write(plan: WakeUpImportPlan) {
        val timetableId = dao.insertTimetableWithReminders(
            TimetableEntity(
                name = plan.timetableName ?: defaultTimetableName,
                showWeekend = plan.showWeekend,
                startDate = plan.startDate ?: currentDate().toString(),
            ),
        )

        plan.lessonTimes.forEach { lessonTime ->
            dao.insertOrUpdateLessonTimeAutoSort(
                LessonTimeEntity(
                    timetableId = timetableId,
                    period = lessonTime.period,
                    startTime = lessonTime.startTime,
                    endTime = lessonTime.endTime,
                ),
            )
        }

        plan.courses.forEach { course ->
            dao.insertCourseWithReminders(
                CourseEntity(
                    timetableId = timetableId,
                    name = course.name,
                    location = course.location,
                    dayOfWeek = course.dayOfWeek,
                    periods = course.periods,
                    weeks = course.weeks,
                    teacher = course.teacher,
                ),
            )
        }
    }
}
