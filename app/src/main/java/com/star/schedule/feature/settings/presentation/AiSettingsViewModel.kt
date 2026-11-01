package com.star.schedule.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.core.common.Constants
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

data class AiSettingsUiState(
    val cloudEnabled: Boolean = true,
    val selectedProviderId: String? = null,
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
) : ViewModel() {

    val uiState: StateFlow<AiSettingsUiState> = combine(
        repository.observePreference(Constants.PREF_PRIVACY_DATA_ENABLED),
        repository.observePreference(Constants.PREF_AI_PROVIDER_ID),
    ) { dataCollection, providerId ->
        AiSettingsUiState(
            cloudEnabled = Constants.isDataCollectionEnabled(dataCollection),
            selectedProviderId = providerId?.takeIf { it != Constants.AI_PROVIDER_NONE },
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
    }

    fun selectProvider(providerId: String) {
        viewModelScope.launch {
            repository.setPreference(Constants.PREF_AI_PROVIDER_ID, providerId)
        }
    }
}

class AiSettingsViewModelFactory(
    private val repository: SettingsRepository,
    private val usageRepository: AiUsageRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AiSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AiSettingsViewModel(repository, usageRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
