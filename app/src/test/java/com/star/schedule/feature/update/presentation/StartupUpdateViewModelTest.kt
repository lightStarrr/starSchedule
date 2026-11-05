package com.star.schedule.feature.update.presentation

import com.star.schedule.feature.update.domain.UpdateChannel
import com.star.schedule.feature.update.domain.UpdateChangelog
import com.star.schedule.feature.update.domain.UpdateCheckResult
import com.star.schedule.feature.update.domain.UpdateCheckTrigger
import com.star.schedule.feature.update.domain.UpdateIssue
import com.star.schedule.feature.update.domain.UpdateIssueReason
import com.star.schedule.feature.update.domain.UpdateRelease
import com.star.schedule.feature.update.domain.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StartupUpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class FakeRepository : UpdateRepository {
        val triggers = mutableListOf<UpdateCheckTrigger>()
        val includeSnapshots = MutableStateFlow(false)
        var nextResult = UpdateCheckResult(
            target = null,
            displayedRelease = null,
            issues = listOf(UpdateIssue(null, UpdateIssueReason.Network)),
            checkedAtEpochMillis = 1,
            includeSnapshots = false,
        )

        override suspend fun check(trigger: UpdateCheckTrigger): UpdateCheckResult {
            triggers += trigger
            return nextResult
        }

        override fun observeIncludeSnapshots() = includeSnapshots

        override suspend fun setIncludeSnapshots(enabled: Boolean) = Unit
    }

    private fun release(versionCode: Long) = UpdateRelease(
        releaseId = versionCode,
        metadataAssetId = 1,
        apkAssetId = 2,
        tag = "v1.3.0",
        channel = UpdateChannel.Stable,
        versionName = "1.3.0",
        versionCode = versionCode,
        commitSha = "b".repeat(40),
        apkName = "app.apk",
        apkSize = 1,
        apkSha256 = "d".repeat(64),
        apkDownloadUrl = "https://github.com/lightStarrr/starSchedule/releases/download/t/app.apk",
        htmlUrl = "https://github.com/lightStarrr/starSchedule/releases/tag/t",
        changelog = UpdateChangelog(),
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.gate(repository: FakeRepository) =
        StartupUpdateGate(
            repository,
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )

    @Test
    fun `cold start check fires on init without any interactive signal`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val gate = gate(repository)
        StartupUpdateViewModel(gate, repository)
        StartupUpdateViewModel(gate, repository)
        advanceUntilIdle()
        assertEquals(listOf(UpdateCheckTrigger.ColdStart), repository.triggers)
    }

    @Test
    fun `available update is held in state and prompts once per identity`() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.nextResult = repository.nextResult.copy(target = release(130))
        val gate = gate(repository)
        val viewModel = StartupUpdateViewModel(gate, repository)
        advanceUntilIdle()
        assertEquals(130L, viewModel.uiState.value.release?.versionCode)

        viewModel.onInteractive(
            viewModel.uiState.value.release!!.identity,
        )
        assertTrue(viewModel.uiState.value.canShowPrompt)
        viewModel.onInteractive(viewModel.uiState.value.release!!.identity)
        assertTrue(viewModel.uiState.value.canShowPrompt)

        val recreated = StartupUpdateViewModel(gate, repository)
        advanceUntilIdle()
        recreated.onInteractive(
            recreated.uiState.value.release!!.identity,
        )
        assertFalse(recreated.uiState.value.canShowPrompt)
        viewModel.dismiss()
        viewModel.onInteractive(viewModel.uiState.value.release!!.identity)
        assertFalse(viewModel.uiState.value.canShowPrompt)
    }

    @Test
    fun `no update or failed check stays quiet`() = runTest(dispatcher) {
        val repository = FakeRepository()
        StartupUpdateViewModel(gate(repository), repository)
        advanceUntilIdle()
        assertEquals(listOf(UpdateCheckTrigger.ColdStart), repository.triggers)
    }

    @Test
    fun `a stale result for a disabled channel is not recommended`() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.nextResult = repository.nextResult.copy(
            target = release(130).copy(channel = UpdateChannel.Snapshot),
            includeSnapshots = true,
        )
        val gate = gate(repository)
        val viewModel = StartupUpdateViewModel(gate, repository)
        advanceUntilIdle()
        assertEquals(listOf(UpdateCheckTrigger.ColdStart), repository.triggers)
        assertNull(viewModel.uiState.value.release)
    }

    @Test
    fun `disabling snapshots removes a fetched but not displayed prompt`() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.includeSnapshots.value = true
        repository.nextResult = repository.nextResult.copy(
            target = release(130).copy(channel = UpdateChannel.Snapshot),
            includeSnapshots = true,
        )
        val gate = gate(repository)
        val viewModel = StartupUpdateViewModel(gate, repository)
        advanceUntilIdle()
        val identity = viewModel.uiState.value.release!!.identity
        assertFalse(viewModel.uiState.value.canShowPrompt)
        repository.includeSnapshots.value = false
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.release)
        viewModel.onInteractive(identity)
        assertFalse(viewModel.uiState.value.canShowPrompt)
        assertTrue(gate.tryClaim(identity))
    }

    @Test
    fun `a background viewmodel does not consume the visible instance prompt`() = runTest(dispatcher) {
        val repository = FakeRepository()
        repository.nextResult = repository.nextResult.copy(target = release(130))
        val gate = gate(repository)
        val background = StartupUpdateViewModel(gate, repository)
        val foreground = StartupUpdateViewModel(gate, repository)
        advanceUntilIdle()
        val identity = foreground.uiState.value.release!!.identity
        foreground.onInteractive(identity)
        assertTrue(foreground.uiState.value.canShowPrompt)
        background.onInteractive(identity)
        assertFalse(background.uiState.value.canShowPrompt)
        assertEquals(listOf(UpdateCheckTrigger.ColdStart), repository.triggers)
    }
}
