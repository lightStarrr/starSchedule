package com.star.schedule.feature.timetable.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.core.common.Constants
import com.star.schedule.feature.settings.domain.SettingsRepository
import com.star.schedule.feature.settings.domain.TimetablePreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TimetableSettingsUiState(
    val isLoading: Boolean = true,
    val previews: List<TimetablePreview> = emptyList(),
    val currentId: Long? = null
) {
}

class TimetableSettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<TimetableSettingsUiState> = combine(
        repository.observeTimetablePreviews(),
        repository.observePreference(Constants.PREF_CURRENT_TIMETABLE),
    ) { previews, currentValue ->
        val currentId = currentValue?.toLongOrNull()
            ?.takeIf { id -> previews.any { it.id == id } }
            ?: previews.firstOrNull()?.id
        TimetableSettingsUiState(
            isLoading = false,
            previews = previews,
            currentId = currentId
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TimetableSettingsUiState()
    )

    fun apply(timetableId: Long) {
        if (timetableId == uiState.value.currentId) return
        viewModelScope.launch { repository.selectTimetable(timetableId) }
    }

    fun setRowHeight(timetableId: Long, rowHeight: Int) {
        val clamped = rowHeight.coerceIn(ROW_HEIGHT_MIN, ROW_HEIGHT_MAX)
        viewModelScope.launch { repository.setRowHeight(timetableId, clamped) }
    }

    companion object {
        const val ROW_HEIGHT_MIN = 40
        const val ROW_HEIGHT_MAX = 240
        const val ROW_HEIGHT_STEP = 10
    }
}

class TimetableSettingsViewModelFactory(
    private val repository: SettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimetableSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TimetableSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
