package com.star.schedule.feature.importing.wakeup.presentation

import com.star.schedule.feature.importing.wakeup.domain.ImportWakeUpScheduleUseCase
import com.star.schedule.feature.importing.wakeup.domain.WakeUpImportParser
import com.star.schedule.feature.importing.wakeup.domain.WakeUpImportPlan
import com.star.schedule.feature.importing.wakeup.domain.WakeUpScheduleWriter
import com.star.schedule.feature.importing.wakeup.domain.WakeUpShareSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class WakeUpImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val plan = WakeUpImportPlan(
        timetableName = "测试课表",
        showWeekend = true,
        startDate = "2026-09-21",
        lessonTimes = emptyList(),
        courses = emptyList(),
    )
    private val fetchedKeys = mutableListOf<String>()
    private val selectedTimetables = mutableListOf<Long>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        source: WakeUpShareSource = WakeUpShareSource { key ->
            fetchedKeys += key
            "response"
        },
    ) = WakeUpImportViewModel(
        importSchedule = ImportWakeUpScheduleUseCase(
            source = source,
            parser = WakeUpImportParser { plan },
            writer = WakeUpScheduleWriter { 7L },
        ),
        selectTimetable = { selectedTimetables += it },
    )

    @Test
    fun `extracts share key while typing`() {
        val viewModel = viewModel()

        viewModel.onInputChange("分享口令为「a1b2c3」")

        assertEquals("a1b2c3", viewModel.uiState.value.shareKey)
        assertTrue(viewModel.uiState.value.canImport)
    }

    @Test
    fun `refuses to import without a recognizable key`() {
        val viewModel = viewModel()

        viewModel.onInputChange("随便一段话")
        viewModel.import()

        assertEquals(
            WakeUpImportPhase.Failed(WakeUpImportError.InvalidCode),
            viewModel.uiState.value.phase,
        )
        assertTrue(fetchedKeys.isEmpty())
    }

    @Test
    fun `selects the imported timetable on success`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onInputChange("分享口令为「a1b2c3」")
        viewModel.import()
        advanceUntilIdle()

        assertEquals(listOf("a1b2c3"), fetchedKeys)
        assertEquals(listOf(7L), selectedTimetables)
        assertEquals(WakeUpImportPhase.Finished(7L), viewModel.uiState.value.phase)
        assertFalse(viewModel.uiState.value.canImport)
    }

    @Test
    fun `marks network failures separately from rejected codes`() = runTest(dispatcher) {
        val viewModel = viewModel(source = WakeUpShareSource { throw IOException("offline") })

        viewModel.onInputChange("分享口令为「a1b2c3」")
        viewModel.import()
        advanceUntilIdle()

        assertEquals(
            WakeUpImportPhase.Failed(WakeUpImportError.Network),
            viewModel.uiState.value.phase,
        )
        assertTrue(selectedTimetables.isEmpty())
    }
}
