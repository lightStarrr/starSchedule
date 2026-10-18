package com.star.schedule.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.core.common.Constants
import com.star.schedule.feature.settings.domain.SettingsRepository
import com.star.schedule.feature.settings.domain.TimetableSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val timetables: List<TimetableSummary> = emptyList(),
    val currentTimetableId: Long? = null,
    val reminderEnabled: Boolean = false,
    val hideFromRecents: Boolean = false,
    val wakeUpSimulationAvailable: Boolean = false,
    val wakeUpProxyInstalled: Boolean = false,
    val wakeUpSimulationEnabled: Boolean = false,
)

private data class SettingsPreferences(
    val hideFromRecents: Boolean,
    val wakeUpSimulationEnabled: Boolean,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {
    private val preferences = combine(
        repository.observePreference(Constants.PREF_HIDE_FROM_RECENTS),
        repository.observePreference(Constants.PREF_WAKEUP_SIMULATION_ENABLED),
    ) { hideFromRecents, wakeUpSimulation ->
        SettingsPreferences(
            hideFromRecents = hideFromRecents == "true",
            wakeUpSimulationEnabled = wakeUpSimulation == "true",
        )
    }

    private val wakeUpSimulationAvailable = repository.isWakeUpSimulationAvailable()
    private val wakeUpProxyInstalled = repository.isWakeUpProxyInstalled()

    val uiState: StateFlow<SettingsUiState> = combine(
        repository.observeTimetables(),
        repository.observePreference(Constants.PREF_CURRENT_TIMETABLE),
        repository.observePreference(Constants.PREF_REMINDER_ENABLED_TIMETABLE),
        preferences,
    ) { timetables, currentIdValue, reminderIdValue, prefs ->
        val currentId = currentIdValue?.toLongOrNull()
        SettingsUiState(
            isLoading = false,
            timetables = timetables,
            currentTimetableId = currentId,
            reminderEnabled = currentId != null && currentId == reminderIdValue?.toLongOrNull(),
            hideFromRecents = prefs.hideFromRecents,
            wakeUpSimulationAvailable = wakeUpSimulationAvailable,
            wakeUpProxyInstalled = wakeUpProxyInstalled,
            wakeUpSimulationEnabled = prefs.wakeUpSimulationEnabled,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(
            wakeUpSimulationAvailable = wakeUpSimulationAvailable,
            wakeUpProxyInstalled = wakeUpProxyInstalled,
        ),
    )

    fun setReminderEnabled(enabled: Boolean) {
        val timetableId = uiState.value.currentTimetableId ?: return
        viewModelScope.launch {
            if (enabled) {
                repository.setPreference(Constants.PREF_WAKEUP_SIMULATION_ENABLED, "false")
                repository.enableRemindersForTimetable(timetableId)
            } else {
                repository.disableReminders()
            }
        }
    }

    fun setHideFromRecents(enabled: Boolean) {
        viewModelScope.launch {
            repository.setPreference(Constants.PREF_HIDE_FROM_RECENTS, enabled.toString())
        }
    }

    fun setWakeUpSimulation(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                repository.disableReminders()
            }
            repository.setPreference(
                Constants.PREF_WAKEUP_SIMULATION_ENABLED,
                enabled.toString(),
            )
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
