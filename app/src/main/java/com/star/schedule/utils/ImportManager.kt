package com.star.schedule.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.star.schedule.db.CourseEntity
import com.star.schedule.db.LessonTimeEntity
import com.star.schedule.db.ScheduleDao
import com.star.schedule.db.TimetableEntity
import com.star.schedule.utils.parser.ParseResult
import com.star.schedule.utils.parser.TimetableParserManager
import com.star.schedule.utils.parser.algorithms.XuexitongParser
import com.star.schedule.utils.parser.algorithms.XuexitongParser2
import com.star.schedule.utils.parser.algorithms.YinghuaParser1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

object ImportManager {

    init {
        Log.d("ImportManager", "Registering XuexitongParser...")
        TimetableParserManager.register(XuexitongParser())
        TimetableParserManager.register(XuexitongParser2())
        TimetableParserManager.register(YinghuaParser1())
    }

    suspend fun importTimetable(fileUri: Uri, context: Context, dao: ScheduleDao): Boolean =
        withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(fileUri)
            if (inputStream == null) {
                Log.e("ImportManager","Failed to open input stream")
                return@withContext false
            }
            inputStream.mark(Int.MAX_VALUE)
            val result: ParseResult? = TimetableParserManager.tryParse(inputStream)
            inputStream.close()

            if (result == null) {
                Log.e("ImportManager","Failed to parse timetable")
                return@withContext false
            }

            val timetableId = dao.insertTimetableWithReminders(
                TimetableEntity(
                    name = "自动导入课表",
                    showWeekend = true,
                    startDate = LocalDate.now().toString()
                )
            )

            result.timeSlots.forEachIndexed { index, slot ->
                dao.insertOrUpdateLessonTimeAutoSort(
                    LessonTimeEntity(
                        timetableId = timetableId,
                        period = index + 1,
                        startTime = slot.start,
                        endTime = slot.end
                    )
                )
            }

            result.courses.forEach { course ->
                dao.insertCourseWithReminders(
                    CourseEntity(
                        timetableId = timetableId,
                        name = course.name,
                        teacher = course.teacher,
                        location = course.location,
                        dayOfWeek = course.weekDay,
                        periods = course.timeSlots,
                        weeks = course.weeks
                    )
                )
            }

            // 课表导入完成后立即刷新小组件
            com.star.schedule.service.WidgetRefreshManager.onTimetableSwitched(context)

            true
        }
}
