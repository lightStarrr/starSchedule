package com.star.schedule.feature.update.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.star.schedule.feature.update.domain.CurrentInstallation
import com.star.schedule.feature.update.domain.UpdateChannel
import com.star.schedule.feature.update.domain.UpdateChangelog
import com.star.schedule.feature.update.domain.UpdateCheckResult
import com.star.schedule.feature.update.domain.UpdateCheckTrigger
import com.star.schedule.feature.update.domain.UpdateDownloadRepository
import com.star.schedule.feature.update.domain.UpdateDownloadFailure
import com.star.schedule.feature.update.domain.UpdateDownloadState
import com.star.schedule.feature.update.domain.UpdateInstallPreparation
import com.star.schedule.feature.update.domain.UpdateIssue
import com.star.schedule.feature.update.domain.UpdateIssueReason
import com.star.schedule.feature.update.domain.UpdateRelease
import com.star.schedule.feature.update.domain.UpdateRepository
import com.star.schedule.feature.update.domain.VerifiedUpdatePackage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class SoftwareUpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeRepository()
    private val stores = mutableListOf<ViewModelStore>()
    private val installed = CurrentInstallation(
        versionName = "1.2.0",
        versionCode = 120,
        commitSha = SHA_A,
        channel = UpdateChannel.Stable,
    )

    private class FakeRepository : UpdateRepository {
        val triggers = mutableListOf<UpdateCheckTrigger>()
        val includeSnapshots = MutableStateFlow(false)
        var checkGate: CompletableDeferred<Unit>? = null
        var failWrite = false
        var nextResult = UpdateCheckResult(
            target = null,
            displayedRelease = null,
            issues = listOf(UpdateIssue(null, UpdateIssueReason.Network)),
            checkedAtEpochMillis = 1,
            includeSnapshots = false,
        )

        override suspend fun check(trigger: UpdateCheckTrigger): UpdateCheckResult {
            triggers += trigger
            val includeSnapshots = includeSnapshots.value
            checkGate?.await()
            return nextResult.copy(includeSnapshots = includeSnapshots)
        }

        override fun observeIncludeSnapshots() = includeSnapshots

        override suspend fun setIncludeSnapshots(enabled: Boolean) {
            if (failWrite) error("write failed")
            includeSnapshots.value = enabled
        }
    }

    private class FakeDownloadRepository : UpdateDownloadRepository {
        val state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
        var restoreGate: CompletableDeferred<Unit>? = null
        var prepareGate: CompletableDeferred<Unit>? = null
        var failure: String? = null
        var prepareCalls = 0
        var preparation: UpdateInstallPreparation = UpdateInstallPreparation.Failure(
            UpdateDownloadFailure.Unknown,
        )

        override fun observe() = flow {
            if (failure == "observe") error("observe")
            emitAll(state)
        }

        override suspend fun restore() {
            restoreGate?.await()
            if (failure == "restore") error("restore")
        }

        override suspend fun start(target: UpdateRelease) {
            if (failure == "start") error("start")
            state.value = UpdateDownloadState.Starting(target)
        }

        override suspend fun cancel() {
            if (failure == "cancel") error("cancel")
            state.value = UpdateDownloadState.Idle
        }

        override suspend fun prepareInstallation(): UpdateInstallPreparation {
            prepareCalls++
            prepareGate?.await()
            if (failure == "prepare") error("prepare")
            return preparation
        }
    }

    private val downloadRepository = FakeDownloadRepository()

    private fun createViewModel(): SoftwareUpdateViewModel {
        val store = ViewModelStore().also(stores::add)
        return ViewModelProvider(
            store,
            SoftwareUpdateViewModelFactory(repository, downloadRepository, installed),
        )[SoftwareUpdateViewModel::class.java]
    }

    private fun TestScope.effects(viewModel: SoftwareUpdateViewModel) =
        mutableListOf<SoftwareUpdateEffect>().also { effects ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.effects.collect { effects += it }
            }
        }

    private fun ready(): SoftwareUpdateViewModel {
        val target = release(130)
        downloadRepository.state.value = UpdateDownloadState.Ready(target)
        downloadRepository.preparation = UpdateInstallPreparation.Ready(
            VerifiedUpdatePackage(target.identity, "/updates/package.apk"),
        )
        return createViewModel().also {
            it.setPageActive(true)
            it.refreshInstallPermission(true)
        }
    }

    private fun release(versionCode: Long) = UpdateRelease(
        releaseId = versionCode,
        metadataAssetId = 1,
        apkAssetId = 2,
        tag = "v1.3.0",
        channel = UpdateChannel.Stable,
        versionName = "1.3.0",
        versionCode = versionCode,
        commitSha = SHA_B,
        apkName = "app.apk",
        apkSize = 1,
        apkSha256 = "d".repeat(64),
        apkDownloadUrl = "https://github.com/lightStarrr/starSchedule/releases/download/t/app.apk",
        htmlUrl = "https://github.com/lightStarrr/starSchedule/releases/tag/t",
        changelog = UpdateChangelog(added = listOf("新功能")),
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        stores.forEach(ViewModelStore::clear)
        Dispatchers.resetMain()
    }

    @Test
    fun `entering the page starts a check and publishes the result`() = runTest(dispatcher) {
        repository.nextResult = repository.nextResult.copy(target = release(130))
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(listOf(UpdateCheckTrigger.PageEnter), repository.triggers)
        assertEquals(130L, viewModel.uiState.value.result?.target?.versionCode)
        assertFalse(viewModel.uiState.value.isChecking)
    }

    @Test
    fun `manual check revalidates and queues at most one follow-up`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.checkNow()
        viewModel.checkNow()
        advanceUntilIdle()
        assertEquals(
            listOf(
                UpdateCheckTrigger.PageEnter,
                UpdateCheckTrigger.Manual,
                UpdateCheckTrigger.PageEnter,
            ),
            repository.triggers,
        )
    }

    @Test
    fun `toggling snapshots persists then rechecks with the new channel set`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setIncludeSnapshots(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.includeSnapshots)
        assertEquals(
            listOf(UpdateCheckTrigger.PageEnter, UpdateCheckTrigger.PageEnter),
            repository.triggers,
        )
        assertFalse(viewModel.uiState.value.preferenceWriteFailed)
    }

    @Test
    fun `preference write failure reports and keeps the old value`() = runTest(dispatcher) {
        repository.failWrite = true
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.setIncludeSnapshots(true)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.includeSnapshots)
        assertTrue(viewModel.uiState.value.preferenceWriteFailed)
    }

    @Test
    fun `a result from before the switch change triggers a fresh check`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        repository.checkGate = CompletableDeferred()
        repository.nextResult = repository.nextResult.copy(target = release(130))
        viewModel.checkNow()
        advanceUntilIdle()
        viewModel.setIncludeSnapshots(true)
        repository.checkGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(
            listOf(
                UpdateCheckTrigger.PageEnter,
                UpdateCheckTrigger.Manual,
                UpdateCheckTrigger.PageEnter,
            ),
            repository.triggers,
        )
        assertTrue(viewModel.uiState.value.result?.includeSnapshots == true)
        assertEquals(130L, viewModel.uiState.value.result?.target?.versionCode)
    }

    @Test
    fun `repository failure becomes a visible issue instead of silent state`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val result = viewModel.uiState.value.result
        assertEquals(UpdateIssueReason.Network, result?.issues?.single()?.reason)
    }

    @Test
    fun `checks wait for download recovery including a manual request`() = runTest(dispatcher) {
        downloadRepository.restoreGate = CompletableDeferred()
        val viewModel = createViewModel()
        viewModel.checkNow()
        runCurrent()
        assertTrue(repository.triggers.isEmpty())
        downloadRepository.restoreGate!!.complete(Unit)
        runCurrent()
        assertTrue(repository.triggers.isNotEmpty())
    }

    @Test
    fun `active pages subscribe once and inactive pages unsubscribe`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.setPageActive(true)
        viewModel.setPageActive(true)
        runCurrent()
        assertEquals(1, downloadRepository.state.subscriptionCount.value)
        viewModel.setPageActive(false)
        runCurrent()
        assertEquals(0, downloadRepository.state.subscriptionCount.value)
    }

    @Test
    fun `restore failure is visible and does not prevent checking`() = runTest(dispatcher) {
        downloadRepository.failure = "restore"
        val viewModel = createViewModel()
        runCurrent()
        assertEquals(
            UpdateDownloadFailure.Storage,
            (viewModel.downloadState.value as UpdateDownloadState.Failed).reason,
        )
        assertEquals(listOf(UpdateCheckTrigger.PageEnter), repository.triggers)
    }

    @Test
    fun `download observation and cancellation failures are visible`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        downloadRepository.failure = "start"
        viewModel.download(release(130))
        runCurrent()
        assertEquals(
            release(130).identity,
            (viewModel.downloadState.value as UpdateDownloadState.Failed).target?.identity,
        )
        downloadRepository.failure = "observe"
        viewModel.setPageActive(true)
        runCurrent()
        assertEquals(
            UpdateDownloadFailure.Storage,
            (viewModel.downloadState.value as UpdateDownloadState.Failed).reason,
        )
        downloadRepository.failure = "cancel"
        viewModel.cancelDownload()
        runCurrent()
        assertTrue(viewModel.downloadState.value is UpdateDownloadState.Failed)
        viewModel.setPageActive(false)
        downloadRepository.failure = null
        viewModel.setPageActive(true)
        runCurrent()
        assertEquals(1, downloadRepository.state.subscriptionCount.value)
    }

    @Test
    fun `failed observation prompts once and resubscription does not repeat`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            viewModel.setPageActive(true)
            runCurrent()
            downloadRepository.state.value = UpdateDownloadState.Failed(
                release(130),
                UpdateDownloadFailure.HashMismatch,
            )
            runCurrent()
            val message = viewModel.uiState.value.problemMessage
            assertEquals(
                UpdateProblem.Download(UpdateDownloadFailure.HashMismatch),
                message?.problem,
            )
            viewModel.onProblemMessageShown(message!!.token)
            runCurrent()
            assertNull(viewModel.uiState.value.problemMessage)
            assertEquals(
                release(130).identity,
                (viewModel.downloadState.value as UpdateDownloadState.Failed)
                    .target?.identity,
            )
            viewModel.setPageActive(false)
            viewModel.setPageActive(true)
            runCurrent()
            assertNull(viewModel.uiState.value.problemMessage)
        }

    @Test
    fun `retry clears prompt and a new failure gets a new token`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.setPageActive(true)
        runCurrent()
        downloadRepository.state.value = UpdateDownloadState.Failed(
            release(130),
            UpdateDownloadFailure.HashMismatch,
        )
        runCurrent()
        val first = viewModel.uiState.value.problemMessage
        viewModel.download(release(130))
        runCurrent()
        assertNull(viewModel.uiState.value.problemMessage)
        assertTrue(viewModel.downloadState.value is UpdateDownloadState.Starting)
        downloadRepository.state.value = UpdateDownloadState.Failed(
            release(130),
            UpdateDownloadFailure.HashMismatch,
        )
        runCurrent()
        val second = viewModel.uiState.value.problemMessage
        assertEquals(
            UpdateProblem.Download(UpdateDownloadFailure.HashMismatch),
            second?.problem,
        )
        assertTrue(second!!.token > first!!.token)
    }

    @Test
    fun `repeated immediate start failures get distinct tokens and stale dismiss is ignored`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            downloadRepository.failure = "start"
            viewModel.download(release(130))
            runCurrent()
            val first = viewModel.uiState.value.problemMessage
            assertEquals(
                UpdateProblem.Download(UpdateDownloadFailure.Unknown),
                first?.problem,
            )
            viewModel.download(release(130))
            runCurrent()
            val second = viewModel.uiState.value.problemMessage
            assertTrue(second!!.token > first!!.token)
            viewModel.onProblemMessageShown(first.token)
            runCurrent()
            assertEquals(second, viewModel.uiState.value.problemMessage)
            viewModel.onProblemMessageShown(second.token)
            runCurrent()
            assertNull(viewModel.uiState.value.problemMessage)
        }

    @Test
    fun `restore and prepare failures both produce messages`() = runTest(dispatcher) {
        downloadRepository.failure = "restore"
        val viewModel = createViewModel()
        runCurrent()
        assertEquals(
            UpdateProblem.Download(UpdateDownloadFailure.Storage),
            viewModel.uiState.value.problemMessage?.problem,
        )

        downloadRepository.failure = null
        val ready = createViewModel()
        ready.setPageActive(true)
        runCurrent()
        ready.refreshInstallPermission(true)
        downloadRepository.state.value = UpdateDownloadState.Ready(release(130))
        runCurrent()
        ready.install()
        runCurrent()
        assertEquals(
            UpdateProblem.Download(UpdateDownloadFailure.Unknown),
            ready.uiState.value.problemMessage?.problem,
        )
    }

    @Test
    fun `permission confirmation is single shot and returning never installs`() = runTest(dispatcher) {
        val viewModel = ready()
        val events = effects(viewModel)
        runCurrent()
        viewModel.refreshInstallPermission(false)
        viewModel.install()
        assertTrue(viewModel.uiState.value.showInstallPermissionPrompt)
        viewModel.confirmInstallPermission()
        viewModel.confirmInstallPermission()
        runCurrent()
        assertEquals(listOf(SoftwareUpdateEffect.OpenInstallSettings), events)
        viewModel.refreshInstallPermission(true)
        runCurrent()
        assertEquals(0, downloadRepository.prepareCalls)
        assertEquals(1, events.size)
    }

    @Test
    fun `install double clicks prepare once and consume an effect only once`() = runTest(dispatcher) {
        val viewModel = ready()
        val events = effects(viewModel)
        downloadRepository.prepareGate = CompletableDeferred()
        runCurrent()
        viewModel.install()
        viewModel.install()
        runCurrent()
        assertEquals(1, downloadRepository.prepareCalls)
        assertTrue(viewModel.uiState.value.isPreparingInstall)
        downloadRepository.prepareGate!!.complete(Unit)
        runCurrent()
        val event = events.single() as SoftwareUpdateEffect.StartInstall
        assertEquals(event.localPath, viewModel.consumeInstallRequest(event.identity, event.localPath))
        assertNull(viewModel.consumeInstallRequest(event.identity, event.localPath))
        viewModel.notifyInstallLaunched(event.identity)
        assertNull(viewModel.uiState.value.problemMessage)
    }

    @Test
    fun `prepare failures and exceptions are visible`() = runTest(dispatcher) {
        val viewModel = ready()
        val events = effects(viewModel)
        runCurrent()
        downloadRepository.preparation = UpdateInstallPreparation.Failure(
            UpdateDownloadFailure.HashMismatch,
        )
        viewModel.install()
        runCurrent()
        assertEquals(
            UpdateDownloadFailure.HashMismatch,
            (viewModel.downloadState.value as UpdateDownloadState.Failed).reason,
        )
        downloadRepository.state.value = UpdateDownloadState.Ready(release(130))
        runCurrent()
        downloadRepository.failure = "prepare"
        viewModel.install()
        runCurrent()
        assertTrue(viewModel.downloadState.value is UpdateDownloadState.Failed)
        assertTrue(events.isEmpty())
        assertFalse(viewModel.uiState.value.isPreparingInstall)
    }

    @Test
    fun `cancel invalidates a preparation and its late failure`() = runTest(dispatcher) {
        val viewModel = ready()
        val events = effects(viewModel)
        downloadRepository.prepareGate = CompletableDeferred()
        downloadRepository.preparation = UpdateInstallPreparation.Failure(
            UpdateDownloadFailure.HashMismatch,
        )
        runCurrent()
        viewModel.install()
        runCurrent()
        viewModel.cancelDownload()
        runCurrent()
        downloadRepository.prepareGate!!.complete(Unit)
        runCurrent()
        assertEquals(UpdateDownloadState.Idle, viewModel.downloadState.value)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `cancel invalidates a queued install effect and its callback`() = runTest(dispatcher) {
        val viewModel = ready()
        val events = effects(viewModel)
        runCurrent()
        viewModel.install()
        runCurrent()
        val event = events.single() as SoftwareUpdateEffect.StartInstall
        viewModel.cancelDownload()
        runCurrent()
        assertNull(viewModel.consumeInstallRequest(event.identity, event.localPath))
        viewModel.notifyInstallLaunchFailed(event.identity)
        assertNull(viewModel.uiState.value.problemMessage)
    }

    @Test
    fun `mismatched prepared identity never launches an installer`() = runTest(dispatcher) {
        val viewModel = ready()
        val events = effects(viewModel)
        runCurrent()
        downloadRepository.preparation = UpdateInstallPreparation.Ready(
            VerifiedUpdatePackage("another-release", "/updates/package.apk"),
        )
        viewModel.install()
        runCurrent()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `installer failure is not reported as a successful launch`() = runTest(dispatcher) {
        val viewModel = ready()
        val events = effects(viewModel)
        runCurrent()
        viewModel.install()
        runCurrent()
        val event = events.single() as SoftwareUpdateEffect.StartInstall
        viewModel.consumeInstallRequest(event.identity, event.localPath)
        viewModel.notifyInstallLaunchFailed(event.identity)
        assertEquals(
            UpdateProblem.InstallerUnavailable,
            viewModel.uiState.value.problemMessage?.problem,
        )
    }

    @Test
    fun `open release and settings failures produce distinct messages`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.notifyOpenReleaseFailed()
        val first = viewModel.uiState.value.problemMessage
        assertEquals(UpdateProblem.ReleasePageUnavailable, first?.problem)
        viewModel.notifyOpenSettingsFailed()
        val second = viewModel.uiState.value.problemMessage
        assertEquals(UpdateProblem.SettingsUnavailable, second?.problem)
        assertTrue(second!!.token > first!!.token)
    }

    private companion object {
        const val SHA_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val SHA_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
}
