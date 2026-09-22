package com.star.schedule.feature.importing.wakeup.domain

data class WakeUpImportPlan(
    val timetableName: String?,
    val showWeekend: Boolean,
    val startDate: String?,
    val lessonTimes: List<WakeUpLessonTime>,
    val courses: List<WakeUpCourse>,
)

data class WakeUpLessonTime(
    val period: Int,
    val startTime: String,
    val endTime: String,
)

data class WakeUpCourse(
    val name: String,
    val location: String,
    val dayOfWeek: Int,
    val periods: List<Int>,
    val weeks: List<Int>,
    val teacher: String,
)

fun interface WakeUpImportParser {
    fun parse(responseBody: String): WakeUpImportPlan
}

fun interface WakeUpShareSource {
    suspend fun fetch(key: String): String
}

fun interface WakeUpScheduleWriter {
    suspend fun write(plan: WakeUpImportPlan)
}

sealed interface WakeUpImportResult {
    data object Success : WakeUpImportResult

    data class Failure(val cause: Throwable) : WakeUpImportResult
}

class ImportWakeUpScheduleUseCase(
    private val source: WakeUpShareSource,
    private val parser: WakeUpImportParser,
    private val writer: WakeUpScheduleWriter,
) {
    suspend operator fun invoke(key: String): WakeUpImportResult =
        try {
            val plan = parser.parse(source.fetch(key))
            writer.write(plan)
            WakeUpImportResult.Success
        } catch (exception: Exception) {
            WakeUpImportResult.Failure(exception)
        }
}
