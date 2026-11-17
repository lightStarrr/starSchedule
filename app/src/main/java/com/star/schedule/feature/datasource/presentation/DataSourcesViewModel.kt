package com.star.schedule.feature.datasource.presentation

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.feature.datasource.domain.DataSourceRepository
import com.star.schedule.feature.datasource.domain.DataSourceStatus
import com.star.schedule.feature.datasource.domain.OptionalResourceRepository
import com.star.schedule.feature.datasource.domain.OptionalResourceState
import com.star.schedule.feature.datasource.domain.RefreshInterval
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DataSourceItemUiState(
    val id: String,
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
    val lastSuccessAt: Long?,
    val lastError: String?,
    val refreshing: Boolean,
)

data class OptionalResourceItemUiState(
    val id: String,
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
    val expectedSizeBytes: Long,
    val state: OptionalResourceState,
)

data class DataSourcesUiState(
    val sources: List<DataSourceItemUiState> = emptyList(),
    val interval: RefreshInterval = RefreshInterval.OFF,
    val resources: List<OptionalResourceItemUiState> = emptyList(),
)

class DataSourcesViewModel(
    private val repository: DataSourceRepository,
    private val optionalResources: OptionalResourceRepository,
) : ViewModel() {

    private val refreshingIds = MutableStateFlow<Set<String>>(emptySet())

    private val statuses: Flow<List<DataSourceStatus>> = combine(
        repository.sources.map { repository.observeStatus(it.source.id) },
    ) { it.toList() }

    private val resourceStates: Flow<List<OptionalResourceState>> = combine(
        optionalResources.resources.map { optionalResources.observeState(it.resource.id) },
    ) { it.toList() }

    val uiState: StateFlow<DataSourcesUiState> = combine(
        statuses,
        refreshingIds,
        repository.observeRefreshInterval(),
        resourceStates,
    ) { statusList, refreshing, interval, states ->
        val statusById = statusList.associateBy { it.id }
        DataSourcesUiState(
            sources = repository.sources.map { descriptor ->
                val status = statusById[descriptor.source.id]
                DataSourceItemUiState(
                    id = descriptor.source.id,
                    nameRes = descriptor.nameRes,
                    descriptionRes = descriptor.descriptionRes,
                    lastSuccessAt = status?.lastSuccessAt,
                    lastError = status?.lastError,
                    refreshing = descriptor.source.id in refreshing,
                )
            },
            interval = interval,
            resources = optionalResources.resources.mapIndexed { index, descriptor ->
                OptionalResourceItemUiState(
                    id = descriptor.resource.id,
                    nameRes = descriptor.nameRes,
                    descriptionRes = descriptor.descriptionRes,
                    expectedSizeBytes = descriptor.resource.expectedSizeBytes,
                    state = states.getOrElse(index) { OptionalResourceState.NotInstalled },
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DataSourcesUiState())

    fun refresh(sourceId: String) {
        if (sourceId in refreshingIds.value) return
        viewModelScope.launch {
            refreshingIds.update { it + sourceId }
            try {
                repository.refreshSource(sourceId)
            } finally {
                refreshingIds.update { it - sourceId }
            }
        }
    }

    fun setInterval(interval: RefreshInterval) {
        viewModelScope.launch {
            repository.setRefreshInterval(interval)
        }
    }

    /** 下载在仓库的 app 级 scope 里跑，离开页面不中断。 */
    fun downloadResource(id: String) = optionalResources.download(id)

    fun cancelResourceDownload(id: String) = optionalResources.cancelDownload(id)

    /** 删除由仓库的 app 级 scope 执行，页面销毁不打断。 */
    fun deleteResource(id: String) = optionalResources.delete(id)
}

class DataSourcesViewModelFactory(
    private val repository: DataSourceRepository,
    private val optionalResources: OptionalResourceRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DataSourcesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DataSourcesViewModel(repository, optionalResources) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
