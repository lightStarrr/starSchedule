package com.star.schedule.feature.importing.wakeup.domain

import com.star.schedule.feature.importing.sharecode.domain.ImportedTimetablePlan
import com.star.schedule.feature.importing.sharecode.domain.ImportedTimetableWriter
import com.star.schedule.feature.importing.sharecode.domain.ShareCodeImportResult
import com.star.schedule.feature.importing.sharecode.domain.ShareCodeImporter

fun interface WakeUpImportParser {
    fun parse(responseBody: String): ImportedTimetablePlan
}

fun interface WakeUpShareSource {
    suspend fun fetch(key: String): String
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
