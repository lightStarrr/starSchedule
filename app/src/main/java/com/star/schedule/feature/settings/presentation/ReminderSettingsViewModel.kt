package com.star.schedule.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.core.common.Constants
import com.star.schedule.feature.settings.domain.SettingsRepository
import com.star.schedule.feature.settings.domain.TimetableSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReminderSettingsUiState(
    val isLoading: Boolean = true,
    val currentTimetable: TimetableSummary? = null,
    val reminderEnabled: Boolean = false,
    val notifyOnlyForFirstContinuousClass: Boolean = false,
    val liveCapsuleCustomizationAvailable: Boolean = false,
    val liveCapsuleBgColor: String? = null,
    val liveCapsuleIconPath: String? = null,
    val liveNotificationTemplate: String? = null,
)

private data class ReminderPreferences(
    val notifyOnlyFirst: Boolean,
    val liveCapsuleBgColor: String?,
    val liveCapsuleIconPath: String?,
    val liveNotificationTemplate: String?,
)

class ReminderSettingsViewModel(
    private val repository: SettingsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val preferences = combine(
        repository.observePreference(Constants.PREF_NOTIFY_ONLY_FOR_FIRST_CONTINUOUS_CLASS),
        repository.observePreference(Constants.PREF_LIVE_CAPSULE_BG_COLOR),
        repository.observePreference(Constants.PREF_LIVE_CAPSULE_ICON_PATH),
        repository.observePreference(Constants.PREF_FLYME_LIVE_TEMPLATE),
    ) { notifyOnly, bgColor, iconPath, template ->
        ReminderPreferences(
            notifyOnlyFirst = notifyOnly == "true",
            liveCapsuleBgColor = bgColor,
            liveCapsuleIconPath = iconPath?.takeIf { it.isNotBlank() },
            liveNotificationTemplate = template,
        )
    }

    private val liveCapsuleCustomizationAvailable = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            liveCapsuleCustomizationAvailable.value = withContext(ioDispatcher) {
                repository.isLiveCapsuleCustomizationAvailable()
            }
        }
    }

    val uiState: StateFlow<ReminderSettingsUiState> = combine(
        repository.observeTimetables(),
        repository.observePreference(Constants.PREF_CURRENT_TIMETABLE),
        repository.observePreference(Constants.PREF_REMINDER_ENABLED_TIMETABLE),
        preferences,
        liveCapsuleCustomizationAvailable,
    ) { timetables, currentIdValue, reminderIdValue, prefs, liveCapsuleAvailable ->
        val currentId = currentIdValue?.toLongOrNull()
        ReminderSettingsUiState(
            isLoading = false,
            currentTimetable = timetables.firstOrNull { it.id == currentId },
            reminderEnabled = currentId != null && currentId == reminderIdValue?.toLongOrNull(),
            notifyOnlyForFirstContinuousClass = prefs.notifyOnlyFirst,
            liveCapsuleCustomizationAvailable = liveCapsuleAvailable,
            liveCapsuleBgColor = prefs.liveCapsuleBgColor,
            liveCapsuleIconPath = prefs.liveCapsuleIconPath,
            liveNotificationTemplate = prefs.liveNotificationTemplate,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ReminderSettingsUiState(),
    )

    fun setReminderEnabled(enabled: Boolean) {
        val timetableId = uiState.value.currentTimetable?.id ?: return
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
                uiState.value.currentTimetable?.id?.let { timetableId ->
                    repository.enableRemindersForTimetable(timetableId)
                }
            }
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

    fun sendTestNotification() {
        viewModelScope.launch {
            repository.sendTestNotification()
        }
    }

    fun scheduleTestReminder() = repository.scheduleTestReminder()
}

class ReminderSettingsViewModelFactory(
    private val repository: SettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReminderSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReminderSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
