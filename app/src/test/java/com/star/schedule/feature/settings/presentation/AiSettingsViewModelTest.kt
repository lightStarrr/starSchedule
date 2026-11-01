package com.star.schedule.feature.settings.presentation

import com.star.schedule.core.common.Constants
import com.star.schedule.feature.settings.domain.AiModel
import com.star.schedule.feature.settings.domain.AiModelCatalog
import com.star.schedule.feature.settings.domain.AiModelRepository
import com.star.schedule.feature.settings.domain.AiReasoning
import com.star.schedule.feature.settings.domain.AiReasoningEffort
import com.star.schedule.feature.settings.domain.AiReasoningMode
import com.star.schedule.feature.settings.domain.AiUsage
import com.star.schedule.feature.settings.domain.AiUsageRepository
import com.star.schedule.feature.settings.domain.SettingsRepository
import com.star.schedule.feature.settings.domain.TimetablePreview
import com.star.schedule.feature.settings.domain.TimetableSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeSettingsRepository()
    private val usageRepository = FakeUsageRepository()
    private val modelRepository = FakeModelRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads models once when cloud provider is selected`() = runTest(dispatcher) {
        settings.set(Constants.PREF_PRIVACY_DATA_ENABLED, "true")
        settings.set(Constants.PREF_AI_PROVIDER_ID, Constants.AI_PROVIDER_CLOUD)

        val viewModel = createViewModel()
        val state = collectState(viewModel)

        advanceUntilIdle()

        assertEquals(1, modelRepository.fetchCount)
        assertTrue(state.value.models is AiModelUiState.Ready)
        assertEquals(3, (state.value.models as AiModelUiState.Ready).catalog.models.size)
    }

    @Test
    fun `does not touch network when provider is none`() = runTest(dispatcher) {
        settings.set(Constants.PREF_PRIVACY_DATA_ENABLED, "true")
        settings.set(Constants.PREF_AI_PROVIDER_ID, Constants.AI_PROVIDER_NONE)

        val viewModel = createViewModel()
        val state = collectState(viewModel)

        advanceUntilIdle()

        assertEquals(0, modelRepository.fetchCount)
        assertEquals(AiModelUiState.Hidden, state.value.models)
        assertEquals(AiUsageUiState.Hidden, viewModel.usage.value)
    }

    @Test
    fun `selecting a model stores its alias and empty alias follows default`() = runTest(dispatcher) {
        settings.set(Constants.PREF_PRIVACY_DATA_ENABLED, "true")
        settings.set(Constants.PREF_AI_PROVIDER_ID, Constants.AI_PROVIDER_CLOUD)

        val viewModel = createViewModel()
        collectState(viewModel)
        advanceUntilIdle()

        viewModel.selectModel("deepseek-reasoner")
        advanceUntilIdle()
        assertEquals("deepseek-reasoner", settings.value(Constants.PREF_AI_MODEL_ALIAS))

        viewModel.selectModel("")
        advanceUntilIdle()
        assertEquals("", settings.value(Constants.PREF_AI_MODEL_ALIAS))
    }

    @Test
    fun `stale alias is cleared once after a successful fetch`() = runTest(dispatcher) {
        settings.set(Constants.PREF_PRIVACY_DATA_ENABLED, "true")
        settings.set(Constants.PREF_AI_PROVIDER_ID, Constants.AI_PROVIDER_CLOUD)
        settings.set(Constants.PREF_AI_MODEL_ALIAS, "retired-model")

        val viewModel = createViewModel()
        val state = collectState(viewModel)

        advanceUntilIdle()

        assertEquals("", settings.value(Constants.PREF_AI_MODEL_ALIAS))
        assertEquals(1, settings.writes(Constants.PREF_AI_MODEL_ALIAS))
        assertNull(state.value.selectedModel)
    }

    @Test
    fun `failed fetch keeps the stored alias`() = runTest(dispatcher) {
        settings.set(Constants.PREF_PRIVACY_DATA_ENABLED, "true")
        settings.set(Constants.PREF_AI_PROVIDER_ID, Constants.AI_PROVIDER_CLOUD)
        settings.set(Constants.PREF_AI_MODEL_ALIAS, "retired-model")
        modelRepository.result = Result.failure(IllegalStateException("offline"))

        val viewModel = createViewModel()
        val state = collectState(viewModel)

        advanceUntilIdle()

        assertEquals(AiModelUiState.Unavailable, state.value.models)
        assertEquals("retired-model", settings.value(Constants.PREF_AI_MODEL_ALIAS))
        assertEquals(0, settings.writes(Constants.PREF_AI_MODEL_ALIAS))
    }

    @Test
    fun `switching away hides models and switching back refetches`() = runTest(dispatcher) {
        settings.set(Constants.PREF_PRIVACY_DATA_ENABLED, "true")
        settings.set(Constants.PREF_AI_PROVIDER_ID, Constants.AI_PROVIDER_CLOUD)

        val viewModel = createViewModel()
        val state = collectState(viewModel)
        advanceUntilIdle()
        assertEquals(1, modelRepository.fetchCount)

        settings.set(Constants.PREF_AI_PROVIDER_ID, Constants.AI_PROVIDER_NONE)
        advanceUntilIdle()
        assertEquals(AiModelUiState.Hidden, state.value.models)

        settings.set(Constants.PREF_AI_PROVIDER_ID, Constants.AI_PROVIDER_CLOUD)
        advanceUntilIdle()
        assertEquals(2, modelRepository.fetchCount)
    }

    @Test
    fun `refresh only retries from unavailable state`() = runTest(dispatcher) {
        settings.set(Constants.PREF_PRIVACY_DATA_ENABLED, "true")
        settings.set(Constants.PREF_AI_PROVIDER_ID, Constants.AI_PROVIDER_CLOUD)
        modelRepository.result = Result.failure(IllegalStateException("offline"))

        val viewModel = createViewModel()
        val state = collectState(viewModel)
        advanceUntilIdle()
        assertEquals(AiModelUiState.Unavailable, state.value.models)

        modelRepository.result = Result.success(sampleCatalog())
        viewModel.refreshModels()
        advanceUntilIdle()
        assertEquals(2, modelRepository.fetchCount)
        assertTrue(state.value.models is AiModelUiState.Ready)

        // 已就绪时再点不该再打网络
        viewModel.refreshModels()
        advanceUntilIdle()
        assertEquals(2, modelRepository.fetchCount)
    }

    @Test
    fun `follows server default model when nothing is selected`() = runTest(dispatcher) {
        settings.set(Constants.PREF_PRIVACY_DATA_ENABLED, "true")
        settings.set(Constants.PREF_AI_PROVIDER_ID, Constants.AI_PROVIDER_CLOUD)

        val viewModel = createViewModel()
        val state = collectState(viewModel)
        advanceUntilIdle()

        assertEquals("deepseek-chat", state.value.effectiveModel?.alias)
        assertEquals(AiReasoningAvailability.Adjustable, state.value.reasoningAvailability)
    }

    @Test
    fun `reasoning availability follows the selected model capability`() = runTest(dispatcher) {
        settings.set(Constants.PREF_PRIVACY_DATA_ENABLED, "true")
        settings.set(Constants.PREF_AI_PROVIDER_ID, Constants.AI_PROVIDER_CLOUD)
        settings.set(Constants.PREF_AI_MODEL_ALIAS, "deepseek-reasoner")

        val viewModel = createViewModel()
        val state = collectState(viewModel)
        advanceUntilIdle()
        assertEquals(AiReasoningAvailability.Fixed, state.value.reasoningAvailability)

        viewModel.selectModel("deepseek-lite")
        advanceUntilIdle()
        assertEquals(AiReasoningAvailability.Unsupported, state.value.reasoningAvailability)
    }

    @Test
    fun `unknown mode and failed fetch both read as unknown`() = runTest(dispatcher) {
        settings.set(Constants.PREF_PRIVACY_DATA_ENABLED, "true")
        settings.set(Constants.PREF_AI_PROVIDER_ID, Constants.AI_PROVIDER_CLOUD)
        modelRepository.result = Result.success(
            AiModelCatalog(
                models = listOf(
                    AiModel(
                        alias = "mystery",
                        displayName = "Mystery",
                        reasoning = AiReasoning(mode = AiReasoningMode.Unknown),
                    ),
                ),
                defaultAlias = "mystery",
            ),
        )

        val viewModel = createViewModel()
        val state = collectState(viewModel)
        advanceUntilIdle()
        assertEquals(AiReasoningAvailability.Unknown, state.value.reasoningAvailability)

        modelRepository.result = Result.failure(IllegalStateException("offline"))
        viewModel.refreshModels()
        advanceUntilIdle()
        assertEquals(AiReasoningAvailability.Unknown, state.value.reasoningAvailability)
    }

    @Test
    fun `selecting effort stores the wire value and empty follows model default`() = runTest(dispatcher) {
        settings.set(Constants.PREF_PRIVACY_DATA_ENABLED, "true")
        settings.set(Constants.PREF_AI_PROVIDER_ID, Constants.AI_PROVIDER_CLOUD)

        val viewModel = createViewModel()
        collectState(viewModel)
        advanceUntilIdle()

        viewModel.selectReasoningEffort(AiReasoningEffort.Low)
        advanceUntilIdle()
        assertEquals("low", settings.value(Constants.PREF_AI_REASONING_EFFORT))

        viewModel.selectReasoningEffort(null)
        advanceUntilIdle()
        assertEquals("", settings.value(Constants.PREF_AI_REASONING_EFFORT))
    }

    @Test
    fun `switching model keeps the stored effort untouched`() = runTest(dispatcher) {
        settings.set(Constants.PREF_PRIVACY_DATA_ENABLED, "true")
        settings.set(Constants.PREF_AI_PROVIDER_ID, Constants.AI_PROVIDER_CLOUD)

        val viewModel = createViewModel()
        collectState(viewModel)
        advanceUntilIdle()

        viewModel.selectReasoningEffort(AiReasoningEffort.Low)
        advanceUntilIdle()
        val writesAfterSelect = settings.writes(Constants.PREF_AI_REASONING_EFFORT)

        viewModel.selectModel("deepseek-lite")
        advanceUntilIdle()

        assertEquals("low", settings.value(Constants.PREF_AI_REASONING_EFFORT))
        assertEquals(writesAfterSelect, settings.writes(Constants.PREF_AI_REASONING_EFFORT))
    }

    @Test
    fun `stored effort outside current model levels is ignored but preserved`() = runTest(dispatcher) {
        settings.set(Constants.PREF_PRIVACY_DATA_ENABLED, "true")
        settings.set(Constants.PREF_AI_PROVIDER_ID, Constants.AI_PROVIDER_CLOUD)
        // 当前模型只给 low/medium，偏好却是 high
        settings.set(Constants.PREF_AI_REASONING_EFFORT, "high")

        val viewModel = createViewModel()
        val state = collectState(viewModel)
        advanceUntilIdle()

        assertNull(state.value.effectiveReasoningEffort)
        assertEquals("high", settings.value(Constants.PREF_AI_REASONING_EFFORT))
        assertEquals(0, settings.writes(Constants.PREF_AI_REASONING_EFFORT))
    }

    @Test
    fun `invalid stored effort degrades to follow model default`() = runTest(dispatcher) {
        settings.set(Constants.PREF_PRIVACY_DATA_ENABLED, "true")
        settings.set(Constants.PREF_AI_PROVIDER_ID, Constants.AI_PROVIDER_CLOUD)
        settings.set(Constants.PREF_AI_REASONING_EFFORT, "ultra")

        val viewModel = createViewModel()
        val state = collectState(viewModel)
        advanceUntilIdle()

        assertNull(state.value.selectedReasoningEffort)
        assertEquals(0, settings.writes(Constants.PREF_AI_REASONING_EFFORT))
    }

    private fun TestScope.createViewModel() = AiSettingsViewModel(
        repository = settings,
        usageRepository = usageRepository,
        modelRepository = modelRepository,
    )

    /** uiState 用 WhileSubscribed 预热，测试里需要真的有人订阅才会开始收上游。 */
    private fun TestScope.collectState(viewModel: AiSettingsViewModel): MutableStateFlow<AiSettingsUiState> {
        val holder = MutableStateFlow(AiSettingsUiState())
        backgroundScope.launch(dispatcher) {
            viewModel.uiState.collect { holder.value = it }
        }
        return holder
    }
}

private fun sampleCatalog() = AiModelCatalog(
    models = listOf(
        AiModel(
            alias = "deepseek-chat",
            displayName = "DeepSeek Chat",
            reasoning = AiReasoning(
                mode = AiReasoningMode.Effort,
                efforts = listOf(AiReasoningEffort.Low, AiReasoningEffort.Medium),
                defaultEffort = AiReasoningEffort.Medium,
            ),
        ),
        AiModel(
            alias = "deepseek-reasoner",
            displayName = "DeepSeek Reasoner",
            reasoning = AiReasoning(mode = AiReasoningMode.Fixed),
        ),
        AiModel(
            alias = "deepseek-lite",
            displayName = "DeepSeek Lite",
            reasoning = AiReasoning(mode = AiReasoningMode.None),
        ),
    ),
    defaultAlias = "deepseek-chat",
)

private class FakeModelRepository : AiModelRepository {
    var result: Result<AiModelCatalog> = Result.success(sampleCatalog())
    var fetchCount = 0

    override suspend fun fetchModels(): Result<AiModelCatalog> {
        fetchCount++
        return result
    }
}

private class FakeUsageRepository : AiUsageRepository {
    override suspend fun fetchUsage(): Result<AiUsage> = Result.success(
        AiUsage(
            dailyQuota = 200_000,
            usedTokens = 0,
            remainingTokens = 200_000,
            requestCount = 0,
            resetAt = null,
        ),
    )
}

private class FakeSettingsRepository : SettingsRepository {
    private val preferences = mutableMapOf<String, MutableStateFlow<String?>>()
    private val writeCounts = mutableMapOf<String, Int>()

    fun set(key: String, value: String?) {
        preferences.getOrPut(key) { MutableStateFlow(null) }.value = value
    }

    fun value(key: String): String? = preferences[key]?.value

    fun writes(key: String): Int = writeCounts[key] ?: 0

    override fun observePreference(key: String): Flow<String?> =
        preferences.getOrPut(key) { MutableStateFlow(null) }

    override suspend fun setPreference(key: String, value: String) {
        preferences.getOrPut(key) { MutableStateFlow(null) }.value = value
        writeCounts[key] = (writeCounts[key] ?: 0) + 1
    }

    override fun observeTimetables(): Flow<List<TimetableSummary>> = emptyFlow()

    override fun observeTimetablePreviews(): Flow<List<TimetablePreview>> = emptyFlow()

    override suspend fun setRowHeight(timetableId: Long, rowHeight: Int) = Unit

    override suspend fun selectTimetable(timetableId: Long) = Unit

    override suspend fun renameTimetable(timetableId: Long, name: String) = Unit

    override suspend fun deleteTimetable(timetableId: Long) = Unit

    override fun isLiveCapsuleCustomizationAvailable(): Boolean = false

    override fun isWakeUpSimulationAvailable(): Boolean = false

    override fun isWakeUpProxyInstalled(): Boolean = false

    override suspend fun importLiveCapsuleIcon(uriString: String): Result<Unit> = Result.success(Unit)

    override suspend fun clearLiveCapsuleIcon() = Unit

    override fun isReminderEnabledForTimetable(timetableId: Long): Boolean = false

    override suspend fun enableRemindersForTimetable(timetableId: Long) = Unit

    override suspend fun disableReminders() = Unit

    override fun sendTestNotification() = Unit

    override fun scheduleTestReminder() = Unit
}
