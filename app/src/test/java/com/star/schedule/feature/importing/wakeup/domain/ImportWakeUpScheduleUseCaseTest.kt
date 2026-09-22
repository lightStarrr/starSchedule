package com.star.schedule.feature.importing.wakeup.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportWakeUpScheduleUseCaseTest {
    private val plan = WakeUpImportPlan(
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
        var writtenPlan: WakeUpImportPlan? = null
        val useCase = ImportWakeUpScheduleUseCase(
            source = WakeUpShareSource { key ->
                fetchedKey = key
                "response"
            },
            parser = WakeUpImportParser { body ->
                parsedBody = body
                plan
            },
            writer = WakeUpScheduleWriter { value -> writtenPlan = value },
        )

        val result = useCase("share-key")

        assertSame(WakeUpImportResult.Success, result)
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
            writer = WakeUpScheduleWriter { writeCount++ },
        )

        val result = useCase("share-key")

        assertTrue(result is WakeUpImportResult.Failure)
        assertSame(expected, (result as WakeUpImportResult.Failure).cause)
        assertEquals(0, writeCount)
    }
}
