package com.star.schedule.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.core.common.Constants
import com.star.schedule.feature.settings.domain.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

class AiSettingsViewModel(
    private val repository: SettingsRepository,
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

    fun selectProvider(providerId: String) {
        viewModelScope.launch {
            repository.setPreference(Constants.PREF_AI_PROVIDER_ID, providerId)
        }
    }
}

class AiSettingsViewModelFactory(
    private val repository: SettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AiSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AiSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
