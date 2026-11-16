package com.star.schedule.feature.importing.sleepdown.domain

import com.star.schedule.feature.importing.sharecode.domain.ImportedTimetablePlan
import com.star.schedule.feature.importing.sharecode.domain.ImportedTimetableWriter
import com.star.schedule.feature.importing.sharecode.domain.ShareCodeImportResult
import com.star.schedule.feature.importing.sharecode.domain.ShareCodeImporter

fun interface SleepDownShareSource {
    suspend fun fetch(code: String): String
}

fun interface SleepDownImportParser {
    fun parse(payload: String): ImportedTimetablePlan
}

/** 服务端明确拒了这个口令（不存在或已过期，HTTP 404）。 */
class SleepDownShareRejectedException(
    message: String,
) : IllegalArgumentException(message)

/** 服务端限流或暂不可用（HTTP 429 / 503），稍后再试。 */
class SleepDownServiceBusyException(
    message: String,
) : IllegalStateException(message)

/** 回包不是预期的 JSON 信封、payload 缺失、状态码不在预期集合里。 */
class SleepDownProtocolException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/** payload 合法但映射不出任何可导入的课程（自定义时间课、空课表等暂不支持的形态）。 */
class SleepDownUnsupportedPayloadException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

class ImportSleepDownScheduleUseCase(
    private val source: SleepDownShareSource,
    private val parser: SleepDownImportParser,
    private val writer: ImportedTimetableWriter,
) : ShareCodeImporter {
    override suspend fun import(code: String): ShareCodeImportResult =
        try {
            val plan = parser.parse(source.fetch(code))
            ShareCodeImportResult.Success(writer.write(plan))
        } catch (exception: Exception) {
            ShareCodeImportResult.Failure(exception)
        }
}
