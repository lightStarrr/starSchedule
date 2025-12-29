package com.star.schedule.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.Constants
import com.star.schedule.db.ScheduleDao
import com.star.schedule.notification.FlymeLiveTemplate
import com.star.schedule.notification.UnifiedNotificationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val dao: ScheduleDao,
    private val notificationManager: UnifiedNotificationManager
) : ViewModel() {

    val timetables = dao.getAllTimetables()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val currentTimetableId = dao.getPreferenceFlow(Constants.PREF_CURRENT_TIMETABLE)
        .map { it?.toLongOrNull() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val notifyOnlyForFirstContinuousClass = dao
        .getPreferenceFlow(Constants.PREF_NOTIFY_ONLY_FOR_FIRST_CONTINUOUS_CLASS)
        .map { it == "true" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    val hideFromRecents = dao
        .getPreferenceFlow(Constants.PREF_HIDE_FROM_RECENTS)
        .map { it == "true" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    val startupHintClosed = dao.getPreferenceFlow("startup_hint_closed")
        .map { it == "true" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    val liveCapsuleBgColor = dao.getPreferenceFlow(Constants.PREF_LIVE_CAPSULE_BG_COLOR)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val liveCapsuleIconPath = dao.getPreferenceFlow(Constants.PREF_LIVE_CAPSULE_ICON_PATH)
        .map { it?.takeIf { path -> path.isNotBlank() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val liveNotificationTemplate = dao.getPreferenceFlow(Constants.PREF_FLYME_LIVE_TEMPLATE)
        .map { FlymeLiveTemplate.fromPref(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FlymeLiveTemplate.Classic
        )

    val isLiveCapsuleCustomizationAvailable =
        notificationManager.isLiveCapsuleCustomizationAvailable()

    private val _reminderEnabled = MutableStateFlow(false)
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled.asStateFlow()

    init {
        viewModelScope.launch {
            currentTimetableId.collectLatest { timetableId ->
                _reminderEnabled.value = timetableId?.let {
                    notificationManager.isReminderEnabledForTimetableSync(it)
                } ?: false
            }
        }
    }

    fun selectTimetable(timetableId: Long) {
        viewModelScope.launch {
            dao.setPreference(Constants.PREF_CURRENT_TIMETABLE, timetableId.toString())
        }
    }

    fun closeStartupHint() {
        viewModelScope.launch {
            dao.setPreference("startup_hint_closed", "true")
        }
    }

    fun setNotifyOnlyForFirstContinuousClass(enabled: Boolean) {
        viewModelScope.launch {
            dao.setPreference(
                Constants.PREF_NOTIFY_ONLY_FOR_FIRST_CONTINUOUS_CLASS,
                enabled.toString()
            )
            currentTimetableId.value?.let { timetableId ->
                notificationManager.enableRemindersForTimetable(timetableId)
            }
        }
    }

    fun setHideFromRecents(enabled: Boolean) {
        viewModelScope.launch {
            dao.setPreference(Constants.PREF_HIDE_FROM_RECENTS, enabled.toString())
        }
    }

    fun enableRemindersForCurrentTimetable() {
        val timetableId = currentTimetableId.value ?: return
        viewModelScope.launch {
            notificationManager.enableRemindersForTimetable(timetableId)
            _reminderEnabled.value = true
        }
    }

    fun disableReminders() {
        viewModelScope.launch {
            notificationManager.disableReminders()
            _reminderEnabled.value = false
        }
    }

    fun sendTestNotification() {
        viewModelScope.launch {
            notificationManager.sendTestNotification()
        }
    }

    fun scheduleTestReminder() {
        viewModelScope.launch {
            notificationManager.scheduleTestReminder()
        }
    }

    fun updateLiveCapsuleBgColor(colorHex: String) {
        viewModelScope.launch {
            dao.setPreference(Constants.PREF_LIVE_CAPSULE_BG_COLOR, colorHex)
        }
    }

    fun updateLiveCapsuleIconPath(path: String) {
        viewModelScope.launch {
            dao.setPreference(Constants.PREF_LIVE_CAPSULE_ICON_PATH, path)
        }
    }

    fun clearLiveCapsuleIcon() {
        viewModelScope.launch {
            dao.setPreference(Constants.PREF_LIVE_CAPSULE_ICON_PATH, "")
        }
    }

    fun updateFlymeLiveTemplate(template: FlymeLiveTemplate) {
        viewModelScope.launch {
            dao.setPreference(Constants.PREF_FLYME_LIVE_TEMPLATE, template.prefValue)
        }
    }
}

class SettingsViewModelFactory(
    private val dao: ScheduleDao,
    private val notificationManager: UnifiedNotificationManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(dao, notificationManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
