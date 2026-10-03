package com.star.schedule.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.core.common.Constants
import com.star.schedule.feature.settings.domain.SettingsRepository
import com.star.schedule.feature.settings.domain.TimetableSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val timetables: List<TimetableSummary> = emptyList(),
    val currentTimetableId: Long? = null,
    val reminderEnabled: Boolean = false,
    val notifyOnlyForFirstContinuousClass: Boolean = false,
    val hideFromRecents: Boolean = false,
    val liveCapsuleCustomizationAvailable: Boolean = false,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {
    private val currentTimetableId = repository
        .observePreference(Constants.PREF_CURRENT_TIMETABLE)
        .map { it?.toLongOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val reminderEnabled = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.observeTimetables(),
        currentTimetableId,
        repository.observePreference(Constants.PREF_NOTIFY_ONLY_FOR_FIRST_CONTINUOUS_CLASS)
            .map { it == "true" },
        repository.observePreference(Constants.PREF_HIDE_FROM_RECENTS)
            .map { it == "true" },
        reminderEnabled,
    ) { timetables, currentId, notifyOnlyFirst, hideFromRecents, reminders ->
        SettingsUiState(
            isLoading = false,
            timetables = timetables,
            currentTimetableId = currentId,
            reminderEnabled = reminders,
            notifyOnlyForFirstContinuousClass = notifyOnlyFirst,
            hideFromRecents = hideFromRecents,
            liveCapsuleCustomizationAvailable = repository.isLiveCapsuleCustomizationAvailable(),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(liveCapsuleCustomizationAvailable = repository.isLiveCapsuleCustomizationAvailable()),
    )

    init {
        viewModelScope.launch {
            currentTimetableId.collect { timetableId ->
                reminderEnabled.value = timetableId?.let {
                    repository.isReminderEnabledForTimetable(it)
                } ?: false
            }
        }
    }

    fun selectTimetable(timetableId: Long) {
        viewModelScope.launch {
            repository.selectTimetable(timetableId)
            reminderEnabled.value = false
        }
    }

    fun setReminderEnabled(enabled: Boolean) {
        val timetableId = uiState.value.currentTimetableId ?: return
        viewModelScope.launch {
            if (enabled) {
                repository.enableRemindersForTimetable(timetableId)
            } else {
                repository.disableReminders()
            }
            reminderEnabled.value = enabled
        }
    }

    fun setNotifyOnlyForFirstContinuousClass(enabled: Boolean) {
        viewModelScope.launch {
            repository.setPreference(
                Constants.PREF_NOTIFY_ONLY_FOR_FIRST_CONTINUOUS_CLASS,
                enabled.toString(),
            )
            if (reminderEnabled.value) {
                uiState.value.currentTimetableId?.let { timetableId ->
                    repository.enableRemindersForTimetable(timetableId)
                }
            }
        }
    }

    fun setHideFromRecents(enabled: Boolean) {
        viewModelScope.launch {
            repository.setPreference(Constants.PREF_HIDE_FROM_RECENTS, enabled.toString())
        }
    }

    fun sendTestNotification() = repository.sendTestNotification()

    fun scheduleTestReminder() = repository.scheduleTestReminder()
}

class SettingsViewModelFactory(
    private val repository: SettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
