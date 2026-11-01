package com.star.schedule.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 云端模型列表的展示状态。 */
sealed interface AiModelUiState {
    /** 没选云端 AI，整行不组合。 */
    data object Hidden : AiModelUiState

    data object Loading : AiModelUiState

    data class Ready(val catalog: AiModelCatalog) : AiModelUiState

    data object Unavailable : AiModelUiState
}

data class AiSettingsUiState(
    val cloudEnabled: Boolean = true,
    val selectedProviderId: String? = null,
    val selectedModelAlias: String? = null,
    /** null = 跟随模型默认（请求里不带 reasoning_effort）。 */
    val selectedReasoningEffort: AiReasoningEffort? = null,
    val models: AiModelUiState = AiModelUiState.Hidden,
) {
    /**
     * 生效的服务商。
     *
     * 关闭匿名统计时 [com.star.schedule.ScheduleApplication] 会把云端选择真正写成「无」，
     * 这里再兜一道：万一偏好里残留 cloud（比如降级安装），也不会被当成可用。
     */
    val effectiveProviderId: String?
        get() = selectedProviderId?.takeIf { cloudEnabled || it != Constants.AI_PROVIDER_CLOUD }

    /** 没有服务商、或显式选了「无」时，AI 功能一律关闭——所有 AI 入口都该看这个值。 */
    val isAiEnabled: Boolean
        get() = effectiveProviderId != null

    /** 是否显示「模型」「今日额度」这些云端专属行。 */
    val hasCloudDetails: Boolean
        get() = effectiveProviderId == Constants.AI_PROVIDER_CLOUD

    /** 只有拿到列表且能查到才算选中；列表没到时不要退回默认，否则文案会闪。 */
    val selectedModel: AiModel?
        get() = (models as? AiModelUiState.Ready)?.catalog?.find(selectedModelAlias)

    /**
     * 真正生效的模型：用户选的那条；没选 → 服务端默认（含兜底第一条）。
     * 列表没到、或选的 alias 已经下架（自愈协程还没跑完）时为 null。
     */
    val effectiveModel: AiModel?
        get() {
            val catalog = (models as? AiModelUiState.Ready)?.catalog ?: return null
            return if (selectedModelAlias == null) catalog.defaultModel else catalog.find(selectedModelAlias)
        }

    val effectiveReasoning: AiReasoning?
        get() = effectiveModel?.reasoning

    /**
     * 用户选的档位，若当前模型不提供该档位则视为「跟随默认」。
     *
     * 只影响展示与将来的请求参数，**不改写偏好**：用户切回原模型时选择还在。
     */
    val effectiveReasoningEffort: AiReasoningEffort?
        get() = selectedReasoningEffort?.takeIf { effort ->
            effectiveReasoning?.efforts?.contains(effort) == true
        }

    /**
     * 思考强度行的可用性。
     *
     * 判断放这里而不是 Composable：将来 chat 请求要复用同一套「能不能发 reasoning_effort」的结论，
     * 纯逻辑才能两边共用，也才能单测。
     */
    val reasoningAvailability: AiReasoningAvailability
        get() {
            val reasoning = effectiveReasoning
            if (reasoning == null) {
                return when (models) {
                    AiModelUiState.Hidden, AiModelUiState.Loading -> AiReasoningAvailability.Loading
                    else -> AiReasoningAvailability.Unknown
                }
            }
            return when (reasoning.mode) {
                AiReasoningMode.Effort -> if (reasoning.efforts.isEmpty()) {
                    AiReasoningAvailability.Unsupported
                } else {
                    AiReasoningAvailability.Adjustable
                }
                AiReasoningMode.Fixed -> AiReasoningAvailability.Fixed
                AiReasoningMode.None -> AiReasoningAvailability.Unsupported
                AiReasoningMode.Unknown -> AiReasoningAvailability.Unknown
            }
        }
}

/** 云端额度的展示状态：没选云端时整块不显示，所以 Hidden 是独立分支而不是空数据。 */
sealed interface AiUsageUiState {
    data object Hidden : AiUsageUiState

    data object Loading : AiUsageUiState

    data class Ready(val usage: AiUsage) : AiUsageUiState

    data object Unavailable : AiUsageUiState
}

class AiSettingsViewModel(
    private val repository: SettingsRepository,
    private val usageRepository: AiUsageRepository,
    private val modelRepository: AiModelRepository,
) : ViewModel() {

    private val _models = MutableStateFlow<AiModelUiState>(AiModelUiState.Hidden)

    val uiState: StateFlow<AiSettingsUiState> = combine(
        repository.observePreference(Constants.PREF_PRIVACY_DATA_ENABLED),
        repository.observePreference(Constants.PREF_AI_PROVIDER_ID),
        repository.observePreference(Constants.PREF_AI_MODEL_ALIAS),
        repository.observePreference(Constants.PREF_AI_REASONING_EFFORT),
        _models,
    ) { dataCollection, providerId, modelAlias, effortRaw, models ->
        AiSettingsUiState(
            cloudEnabled = Constants.isDataCollectionEnabled(dataCollection),
            selectedProviderId = providerId?.takeIf { it != Constants.AI_PROVIDER_NONE },
            selectedModelAlias = modelAlias?.takeIf { it.isNotEmpty() },
            // 认不出的值（含空串）一律视为跟随默认，不崩也不误发请求
            selectedReasoningEffort = AiReasoningEffort.fromWire(effortRaw),
            models = models,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        // 偏好还没读出来时按默认值渲染，避免下拉菜单闪一下
        AiSettingsUiState(),
    )

    private val _usage = MutableStateFlow<AiUsageUiState>(AiUsageUiState.Hidden)
    val usage: StateFlow<AiUsageUiState> = _usage

    init {
        // 只有真的用云端时才去拉额度；切走或改成「无」就把这块收起来并取消在途请求
        viewModelScope.launch {
            uiState
                .map { it.effectiveProviderId }
                .distinctUntilChanged()
                .collectLatest { providerId ->
                    if (providerId != Constants.AI_PROVIDER_CLOUD) {
                        _usage.value = AiUsageUiState.Hidden
                        return@collectLatest
                    }
                    _usage.value = AiUsageUiState.Loading
                    _usage.value = usageRepository.fetchUsage().fold(
                        onSuccess = { AiUsageUiState.Ready(it) },
                        onFailure = { AiUsageUiState.Unavailable },
                    )
                }
        }

        // 模型列表与额度各自一条协程：合并成一条会让「切服务商」时两个请求共用一个取消域，语义变复杂
        viewModelScope.launch {
            uiState
                .map { it.effectiveProviderId }
                .distinctUntilChanged()
                .collectLatest { providerId ->
                    if (providerId != Constants.AI_PROVIDER_CLOUD) {
                        _models.value = AiModelUiState.Hidden
                        return@collectLatest
                    }
                    loadModels()
                }
        }

        // 已选的模型被后台关掉时自愈成「跟随默认」。只在拿到成功列表后判断：
        // 失败时按「不知道」处理，绝不能因为一次网络错误抹掉用户偏好。
        viewModelScope.launch {
            combine(
                repository.observePreference(Constants.PREF_AI_MODEL_ALIAS),
                _models,
            ) { alias, models -> alias to models }
                .collect { (alias, models) ->
                    if (models !is AiModelUiState.Ready || alias.isNullOrEmpty()) return@collect
                    if (models.catalog.find(alias) == null) {
                        repository.setPreference(Constants.PREF_AI_MODEL_ALIAS, "")
                    }
                }
        }
    }

    fun selectProvider(providerId: String) {
        viewModelScope.launch {
            repository.setPreference(Constants.PREF_AI_PROVIDER_ID, providerId)
        }
    }

    /** 传空串表示「跟随服务端默认」。不做乐观更新，偏好流会回灌。 */
    fun selectModel(alias: String) {
        viewModelScope.launch {
            repository.setPreference(Constants.PREF_AI_MODEL_ALIAS, alias)
        }
    }

    /**
     * 传 null 表示「跟随模型默认」。
     *
     * 切模型时**不清空**这个偏好（用户明确要保留）：只在当前模型不提供该档位时按跟随默认展示，
     * 偏好本身留给下次切回来时复用。
     */
    fun selectReasoningEffort(effort: AiReasoningEffort?) {
        viewModelScope.launch {
            repository.setPreference(Constants.PREF_AI_REASONING_EFFORT, effort?.wireValue ?: "")
        }
    }

    /** 只在拉取失败时由界面触发，避免连点重复请求。 */
    fun refreshModels() {
        // 只有失败态才值得重试：列表已就绪时重复点行不该再打一次网络
        if (_models.value != AiModelUiState.Unavailable) return
        if (uiState.value.effectiveProviderId != Constants.AI_PROVIDER_CLOUD) return
        viewModelScope.launch { loadModels() }
    }

    private suspend fun loadModels() {
        _models.value = AiModelUiState.Loading
        _models.value = modelRepository.fetchModels().fold(
            onSuccess = { AiModelUiState.Ready(it) },
            onFailure = { AiModelUiState.Unavailable },
        )
    }
}

class AiSettingsViewModelFactory(
    private val repository: SettingsRepository,
    private val usageRepository: AiUsageRepository,
    private val modelRepository: AiModelRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AiSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AiSettingsViewModel(repository, usageRepository, modelRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
