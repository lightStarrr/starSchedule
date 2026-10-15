package com.star.schedule.feature.settings.presentation

import androidx.lifecycle.SavedStateHandle
import com.star.schedule.feature.settings.domain.StartupHintRepository
import kotlinx.coroutines.CompletableDeferred
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

@OptIn(ExperimentalCoroutinesApi::class)
class StartupHintViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeStartupHintRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `waits for preference before showing and honors existing suppression`() = runTest(dispatcher) {
        repository.suppressed = true
        val viewModel = StartupHintViewModel(repository, SavedStateHandle())
        assertEquals(StartupHintUiState.Loading, viewModel.uiState.value)
        advanceUntilIdle()
        assertEquals(StartupHintUiState.Hidden, viewModel.uiState.value)
    }

    @Test
    fun `unchecked confirmation closes this launch but shows on next launch`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val viewModel = StartupHintViewModel(repository, savedState)
        advanceUntilIdle()
        assertEquals(StartupHintUiState.Visible(), viewModel.uiState.value)
        viewModel.confirm()
        assertEquals(StartupHintUiState.Hidden, viewModel.uiState.value)
        assertEquals(0, repository.writes)

        val restored = StartupHintViewModel(repository, savedState)
        val nextLaunch = StartupHintViewModel(repository, SavedStateHandle())
        advanceUntilIdle()
        assertEquals(StartupHintUiState.Hidden, restored.uiState.value)
        assertEquals(StartupHintUiState.Visible(), nextLaunch.uiState.value)
    }

    @Test
    fun `checked confirmation persists only once and suppresses future launches`() = runTest(dispatcher) {
        val viewModel = StartupHintViewModel(repository, SavedStateHandle())
        advanceUntilIdle()
        viewModel.setDoNotShowAgain(true)
        assertEquals(0, repository.writes)
        viewModel.confirm()
        viewModel.confirm()
        viewModel.dismiss()
        assertTrue((viewModel.uiState.value as StartupHintUiState.Visible).isSaving)
        advanceUntilIdle()
        assertEquals(StartupHintUiState.Hidden, viewModel.uiState.value)
        assertEquals(1, repository.writes)

        val nextLaunch = StartupHintViewModel(repository, SavedStateHandle())
        advanceUntilIdle()
        assertEquals(StartupHintUiState.Hidden, nextLaunch.uiState.value)
    }

    @Test
    fun `back or outside dismissal never saves a checked option`() = runTest(dispatcher) {
        val viewModel = StartupHintViewModel(repository, SavedStateHandle())
        advanceUntilIdle()
        viewModel.setDoNotShowAgain(true)
        viewModel.dismiss()
        advanceUntilIdle()
        assertEquals(StartupHintUiState.Hidden, viewModel.uiState.value)
        assertFalse(repository.suppressed)
    }

    @Test
    fun `restores checkbox before confirmation across recreation`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val viewModel = StartupHintViewModel(repository, savedState)
        advanceUntilIdle()
        viewModel.setDoNotShowAgain(true)
        val restored = StartupHintViewModel(repository, savedState)
        advanceUntilIdle()
        assertEquals(StartupHintUiState.Visible(doNotShowAgain = true), restored.uiState.value)
        assertEquals(0, repository.writes)
    }

    @Test
    fun `write failure keeps dialog open and allows retry`() = runTest(dispatcher) {
        val viewModel = StartupHintViewModel(repository, SavedStateHandle())
        advanceUntilIdle()
        viewModel.setDoNotShowAgain(true)
        repository.failWrite = true
        viewModel.confirm()
        advanceUntilIdle()
        assertEquals(
            StartupHintUiState.Visible(doNotShowAgain = true, saveFailed = true),
            viewModel.uiState.value,
        )
        repository.failWrite = false
        viewModel.confirm()
        advanceUntilIdle()
        assertEquals(StartupHintUiState.Hidden, viewModel.uiState.value)
        assertTrue(repository.suppressed)
    }

    @Test
    fun `pending read does not show dialog early`() = runTest(dispatcher) {
        val read = CompletableDeferred<Boolean>()
        repository.pendingRead = read
        val viewModel = StartupHintViewModel(repository, SavedStateHandle())
        advanceUntilIdle()
        assertEquals(StartupHintUiState.Loading, viewModel.uiState.value)
        read.complete(false)
        advanceUntilIdle()
        assertEquals(StartupHintUiState.Visible(), viewModel.uiState.value)
    }

    @Test
    fun `read failure shows reminder without modifying preference`() = runTest(dispatcher) {
        repository.failRead = true
        val viewModel = StartupHintViewModel(repository, SavedStateHandle())
        advanceUntilIdle()
        assertEquals(StartupHintUiState.Visible(), viewModel.uiState.value)
        assertEquals(0, repository.writes)
    }

    private class FakeStartupHintRepository : StartupHintRepository {
        var suppressed = false
        var writes = 0
        var failRead = false
        var failWrite = false
        var pendingRead: CompletableDeferred<Boolean>? = null

        override suspend fun isSuppressed(): Boolean {
            if (failRead) error("Read failed")
            return pendingRead?.await() ?: suppressed
        }

        override suspend fun suppress() {
            writes++
            if (failWrite) error("Write failed")
            suppressed = true
        }
    }
}
