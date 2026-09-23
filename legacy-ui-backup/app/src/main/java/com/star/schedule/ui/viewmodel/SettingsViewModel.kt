package com.star.schedule.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.core.common.Constants
import com.star.schedule.feature.settings.domain.SettingsRepository
import com.star.schedule.notification.FlymeLiveTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    val timetables = repository.observeTimetables()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val currentTimetableId = repository.observePreference(Constants.PREF_CURRENT_TIMETABLE)
        .map { it?.toLongOrNull() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val notifyOnlyForFirstContinuousClass = repository
        .observePreference(Constants.PREF_NOTIFY_ONLY_FOR_FIRST_CONTINUOUS_CLASS)
        .map { it == "true" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    val wakeUpSimulationEnabled = repository
        .observePreference(Constants.PREF_WAKEUP_SIMULATION_ENABLED)
        .map { it == "true" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    val hideFromRecents = repository
        .observePreference(Constants.PREF_HIDE_FROM_RECENTS)
        .map { it == "true" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    val startupHintClosed = repository.observePreference("startup_hint_closed")
        .map { it == "true" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    val liveCapsuleBgColor = repository.observePreference(Constants.PREF_LIVE_CAPSULE_BG_COLOR)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val liveCapsuleIconPath = repository.observePreference(Constants.PREF_LIVE_CAPSULE_ICON_PATH)
        .map { it?.takeIf { path -> path.isNotBlank() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val liveNotificationTemplate = repository.observePreference(Constants.PREF_FLYME_LIVE_TEMPLATE)
        .map { FlymeLiveTemplate.fromPref(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FlymeLiveTemplate.Classic
        )

    val isLiveCapsuleCustomizationAvailable =
        repository.isLiveCapsuleCustomizationAvailable()

    private val _reminderEnabled = MutableStateFlow(false)
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            currentTimetableId.collectLatest { timetableId ->
                _reminderEnabled.value = timetableId?.let {
                    repository.isReminderEnabledForTimetable(it)
                } ?: false
            }
        }
    }

    fun selectTimetable(timetableId: Long) {
        viewModelScope.launch {
            repository.setPreference(Constants.PREF_CURRENT_TIMETABLE, timetableId.toString())
        }
    }

    fun closeStartupHint() {
        viewModelScope.launch {
            repository.setPreference("startup_hint_closed", "true")
        }
    }

    fun setNotifyOnlyForFirstContinuousClass(enabled: Boolean) {
        viewModelScope.launch {
            repository.setPreference(
                Constants.PREF_NOTIFY_ONLY_FOR_FIRST_CONTINUOUS_CLASS,
                enabled.toString()
            )
            currentTimetableId.value?.let { timetableId ->
                repository.enableRemindersForTimetable(timetableId)
            }
        }
    }

    fun setHideFromRecents(enabled: Boolean) {
        viewModelScope.launch {
            repository.setPreference(Constants.PREF_HIDE_FROM_RECENTS, enabled.toString())
        }
    }

    fun enableRemindersForCurrentTimetable() {
        val timetableId = currentTimetableId.value ?: return
        viewModelScope.launch {
            repository.setPreference(Constants.PREF_WAKEUP_SIMULATION_ENABLED, "false")
            repository.enableRemindersForTimetable(timetableId)
            _reminderEnabled.value = true
        }
    }

    fun disableReminders() {
        viewModelScope.launch {
            repository.disableReminders()
            _reminderEnabled.value = false
        }
    }

    fun enableWakeUpSimulation() {
        viewModelScope.launch {
            repository.disableReminders()
            repository.setPreference(Constants.PREF_WAKEUP_SIMULATION_ENABLED, "true")
            _reminderEnabled.value = false
        }
    }

    fun disableWakeUpSimulation() {
        viewModelScope.launch {
            repository.setPreference(Constants.PREF_WAKEUP_SIMULATION_ENABLED, "false")
        }
    }

    fun sendTestNotification() {
        viewModelScope.launch {
            repository.sendTestNotification()
        }
    }

    fun scheduleTestReminder() {
        viewModelScope.launch {
            repository.scheduleTestReminder()
        }
    }

    fun updateLiveCapsuleBgColor(colorHex: String) {
        viewModelScope.launch {
            repository.setPreference(Constants.PREF_LIVE_CAPSULE_BG_COLOR, colorHex)
        }
    }

    fun updateLiveCapsuleIconPath(path: String) {
        viewModelScope.launch {
            repository.setPreference(Constants.PREF_LIVE_CAPSULE_ICON_PATH, path)
        }
    }

    fun clearLiveCapsuleIcon() {
        viewModelScope.launch {
            repository.setPreference(Constants.PREF_LIVE_CAPSULE_ICON_PATH, "")
        }
    }

    fun updateFlymeLiveTemplate(template: FlymeLiveTemplate) {
        viewModelScope.launch {
            repository.setPreference(Constants.PREF_FLYME_LIVE_TEMPLATE, template.prefValue)
        }
    }
}

class SettingsViewModelFactory(
    private val repository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
