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
    val notifyOnlyForFirstContinuousClass: Boolean = false,
    val hideFromRecents: Boolean = false,
    val wakeUpSimulationAvailable: Boolean = false,
    val wakeUpProxyInstalled: Boolean = false,
    val wakeUpSimulationEnabled: Boolean = false,
    val liveCapsuleCustomizationAvailable: Boolean = false,
    val liveCapsuleBgColor: String? = null,
    val liveCapsuleIconPath: String? = null,
    val liveNotificationTemplate: String? = null,
)

private data class SettingsPreferences(
    val notifyOnlyFirst: Boolean,
    val hideFromRecents: Boolean,
    val wakeUpSimulationEnabled: Boolean,
    val liveCapsuleBgColor: String?,
    val liveCapsuleIconPath: String?,
    val liveNotificationTemplate: String?,
)

class SettingsViewModel(
    private val repository: SettingsRepository,
) : ViewModel() {
    private val preferences = combine(
        repository.observePreference(Constants.PREF_NOTIFY_ONLY_FOR_FIRST_CONTINUOUS_CLASS),
        repository.observePreference(Constants.PREF_HIDE_FROM_RECENTS),
        repository.observePreference(Constants.PREF_WAKEUP_SIMULATION_ENABLED),
        repository.observePreference(Constants.PREF_LIVE_CAPSULE_BG_COLOR),
        repository.observePreference(Constants.PREF_LIVE_CAPSULE_ICON_PATH),
        repository.observePreference(Constants.PREF_FLYME_LIVE_TEMPLATE),
    ) { values ->
        SettingsPreferences(
            notifyOnlyFirst = values[0] == "true",
            hideFromRecents = values[1] == "true",
            wakeUpSimulationEnabled = values[2] == "true",
            liveCapsuleBgColor = values[3] as String?,
            liveCapsuleIconPath = (values[4] as String?)?.takeIf { it.isNotBlank() },
            liveNotificationTemplate = values[5] as String?,
        )
    }

    private val wakeUpSimulationAvailable = repository.isWakeUpSimulationAvailable()
    private val wakeUpProxyInstalled = repository.isWakeUpProxyInstalled()
    private val liveCapsuleCustomizationAvailable =
        repository.isLiveCapsuleCustomizationAvailable()

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
            notifyOnlyForFirstContinuousClass = prefs.notifyOnlyFirst,
            hideFromRecents = prefs.hideFromRecents,
            wakeUpSimulationAvailable = wakeUpSimulationAvailable,
            wakeUpProxyInstalled = wakeUpProxyInstalled,
            wakeUpSimulationEnabled = prefs.wakeUpSimulationEnabled,
            liveCapsuleCustomizationAvailable = liveCapsuleCustomizationAvailable,
            liveCapsuleBgColor = prefs.liveCapsuleBgColor,
            liveCapsuleIconPath = prefs.liveCapsuleIconPath,
            liveNotificationTemplate = prefs.liveNotificationTemplate,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(
            wakeUpSimulationAvailable = wakeUpSimulationAvailable,
            wakeUpProxyInstalled = wakeUpProxyInstalled,
            liveCapsuleCustomizationAvailable = liveCapsuleCustomizationAvailable,
        ),
    )

    fun selectTimetable(timetableId: Long) {
        viewModelScope.launch {
            repository.selectTimetable(timetableId)
        }
    }

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

    fun setNotifyOnlyForFirstContinuousClass(enabled: Boolean) {
        viewModelScope.launch {
            repository.setPreference(
                Constants.PREF_NOTIFY_ONLY_FOR_FIRST_CONTINUOUS_CLASS,
                enabled.toString(),
            )
            if (uiState.value.reminderEnabled) {
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

    suspend fun importLiveCapsuleIcon(uriString: String): Result<Unit> =
        repository.importLiveCapsuleIcon(uriString)

    fun clearLiveCapsuleIcon() {
        viewModelScope.launch {
            repository.clearLiveCapsuleIcon()
        }
    }

    fun setLiveCapsuleBgColor(colorHex: String) {
        viewModelScope.launch {
            repository.setPreference(Constants.PREF_LIVE_CAPSULE_BG_COLOR, colorHex)
        }
    }

    fun setLiveNotificationTemplate(template: String) {
        viewModelScope.launch {
            repository.setPreference(Constants.PREF_FLYME_LIVE_TEMPLATE, template)
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
