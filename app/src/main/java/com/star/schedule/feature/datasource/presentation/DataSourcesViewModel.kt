package com.star.schedule.feature.datasource.presentation

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.feature.datasource.domain.DataSourceRepository
import com.star.schedule.feature.datasource.domain.DataSourceStatus
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

data class DataSourcesUiState(
    val sources: List<DataSourceItemUiState> = emptyList(),
    val interval: RefreshInterval = RefreshInterval.OFF,
)

class DataSourcesViewModel(
    private val repository: DataSourceRepository,
) : ViewModel() {

    private val refreshingIds = MutableStateFlow<Set<String>>(emptySet())

    private val statuses: Flow<List<DataSourceStatus>> = combine(
        repository.sources.map { repository.observeStatus(it.source.id) },
    ) { it.toList() }

    val uiState: StateFlow<DataSourcesUiState> = combine(
        statuses,
        refreshingIds,
        repository.observeRefreshInterval(),
    ) { statusList, refreshing, interval ->
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
}

class DataSourcesViewModelFactory(
    private val repository: DataSourceRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DataSourcesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DataSourcesViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
