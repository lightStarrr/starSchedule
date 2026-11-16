package com.star.schedule.feature.importing.sharecode.domain

data class ImportedTimetablePlan(
    val timetableName: String?,
    val showWeekend: Boolean,
    val startDate: String?,
    val lessonTimes: List<ImportedLessonTime>,
    val courses: List<ImportedCourse>,
)

data class ImportedLessonTime(
    val period: Int,
    val startTime: String,
    val endTime: String,
)

data class ImportedCourse(
    val name: String,
    val location: String,
    val dayOfWeek: Int,
    val periods: List<Int>,
    val weeks: List<Int>,
    val teacher: String,
)

fun interface ImportedTimetableWriter {
    /** 写入后返回新课表的 id，调用方要靠它把新课表切成当前课表。 */
    suspend fun write(plan: ImportedTimetablePlan): Long
}

sealed interface ShareCodeImportResult {
    data class Success(val timetableId: Long) : ShareCodeImportResult

    data class Failure(val cause: Throwable) : ShareCodeImportResult
}

/**
 * 各家「粘贴口令 → 拿到一张课表」的统一入口：
 * 取数、解析、写库的差异全部藏在实现里，页面只面对这个结果。
 */
fun interface ShareCodeImporter {
    suspend fun import(code: String): ShareCodeImportResult
}
