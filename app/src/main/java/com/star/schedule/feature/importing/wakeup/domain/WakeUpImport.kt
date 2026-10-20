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
    /** 写入后返回新课表的 id，调用方要靠它把新课表切成当前课表。 */
    suspend fun write(plan: WakeUpImportPlan): Long
}

sealed interface WakeUpImportResult {
    data class Success(val timetableId: Long) : WakeUpImportResult

    data class Failure(val cause: Throwable) : WakeUpImportResult
}

/**
 * 协议本身对不上：常量过期、签名被拒、密文解不开。
 *
 * 这和「口令错了」是两件事，UI 上要给不同的提示，否则用户会以为是自己复制错了。
 */
class WakeUpProtocolException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/** 服务端明确拒了这个口令（无效或过期）。 */
class WakeUpShareRejectedException(
    message: String,
) : IllegalArgumentException(message)


class ImportWakeUpScheduleUseCase(
    private val source: WakeUpShareSource,
    private val parser: WakeUpImportParser,
    private val writer: WakeUpScheduleWriter,
) {
    suspend operator fun invoke(key: String): WakeUpImportResult =
        try {
            val plan = parser.parse(source.fetch(key))
            WakeUpImportResult.Success(writer.write(plan))
        } catch (exception: Exception) {
            WakeUpImportResult.Failure(exception)
        }
}
