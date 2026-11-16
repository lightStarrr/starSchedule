package com.star.schedule.feature.importing.sharecode.presentation

import com.star.schedule.feature.importing.sharecode.domain.ShareCodeImportResult
import com.star.schedule.feature.importing.sharecode.domain.ShareCodeImporter
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
class ShareCodeImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val fetchedCodes = mutableListOf<String>()
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
        importer: ShareCodeImporter = ShareCodeImporter { code ->
            fetchedCodes += code
            ShareCodeImportResult.Success(7L)
        },
    ) = ShareCodeImportViewModel(
        importer = importer,
        extractCode = { input -> input.substringAfter("「", "").substringBefore("」") },
        errorOf = { error ->
            when (error) {
                is IOException -> ShareCodeImportError.Network
                else -> ShareCodeImportError.Rejected
            }
        },
        selectTimetable = { selectedTimetables += it },
    )

    @Test
    fun `extracts share code while typing`() {
        val viewModel = viewModel()

        viewModel.onInputChange("分享口令为「a1b2c3d4」")

        assertEquals("a1b2c3d4", viewModel.uiState.value.shareCode)
        assertTrue(viewModel.uiState.value.canImport)
    }

    @Test
    fun `refuses to import without a recognizable code`() {
        val viewModel = viewModel()

        viewModel.onInputChange("随便一段话")
        viewModel.import()

        assertEquals(
            ShareCodeImportPhase.Failed(ShareCodeImportError.InvalidCode),
            viewModel.uiState.value.phase,
        )
        assertTrue(fetchedCodes.isEmpty())
    }

    @Test
    fun `selects the imported timetable on success`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.onInputChange("分享口令为「a1b2c3d4」")
        viewModel.import()
        advanceUntilIdle()

        assertEquals(listOf("a1b2c3d4"), fetchedCodes)
        assertEquals(listOf(7L), selectedTimetables)
        assertEquals(ShareCodeImportPhase.Finished(7L), viewModel.uiState.value.phase)
        assertFalse(viewModel.uiState.value.canImport)
    }

    @Test
    fun `marks network failures separately from rejected codes`() = runTest(dispatcher) {
        val viewModel = viewModel(
            importer = ShareCodeImporter { ShareCodeImportResult.Failure(IOException("offline")) },
        )

        viewModel.onInputChange("分享口令为「a1b2c3d4」")
        viewModel.import()
        advanceUntilIdle()

        assertEquals(
            ShareCodeImportPhase.Failed(ShareCodeImportError.Network),
            viewModel.uiState.value.phase,
        )
        assertTrue(selectedTimetables.isEmpty())
    }
}
