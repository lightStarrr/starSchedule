package com.star.schedule.feature.importing.wakeup.domain

import com.star.schedule.feature.importing.sharecode.domain.ImportedTimetablePlan
import com.star.schedule.feature.importing.sharecode.domain.ImportedTimetableWriter
import com.star.schedule.feature.importing.sharecode.domain.ShareCodeImportResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportWakeUpScheduleUseCaseTest {
    private val plan = ImportedTimetablePlan(
        timetableName = "测试课表",
        showWeekend = true,
        startDate = "2026-09-21",
        lessonTimes = emptyList(),
        courses = emptyList(),
    )

    @Test
    fun fetchesParsesAndWritesSchedule() = runBlocking {
        var fetchedKey: String? = null
        var parsedBody: String? = null
        var writtenPlan: ImportedTimetablePlan? = null
        val useCase = ImportWakeUpScheduleUseCase(
            source = WakeUpShareSource { key ->
                fetchedKey = key
                "response"
            },
            parser = WakeUpImportParser { body ->
                parsedBody = body
                plan
            },
            writer = ImportedTimetableWriter { value ->
                writtenPlan = value
                42L
            },
        )

        val result = useCase.import("share-key")

        assertEquals(ShareCodeImportResult.Success(42L), result)
        assertEquals("share-key", fetchedKey)
        assertEquals("response", parsedBody)
        assertEquals(plan, writtenPlan)
    }

    @Test
    fun returnsFailureWithoutWritingWhenSourceFails() = runBlocking {
        val expected = IllegalStateException("network failed")
        var writeCount = 0
        val useCase = ImportWakeUpScheduleUseCase(
            source = WakeUpShareSource { throw expected },
            parser = WakeUpImportParser { plan },
            writer = ImportedTimetableWriter { writeCount++; 0L },
        )

        val result = useCase.import("share-key")

        assertTrue(result is ShareCodeImportResult.Failure)
        assertSame(expected, (result as ShareCodeImportResult.Failure).cause)
        assertEquals(0, writeCount)
    }
}
