package com.star.schedule.feature.settings.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.star.schedule.feature.settings.domain.StartupHintRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface StartupHintUiState {
    data object Loading : StartupHintUiState

    data object Hidden : StartupHintUiState

    data class Visible(
        val doNotShowAgain: Boolean = false,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
    ) : StartupHintUiState
}

class StartupHintViewModel(
    private val repository: StartupHintRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow<StartupHintUiState>(StartupHintUiState.Loading)
    val uiState: StateFlow<StartupHintUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            if (savedState.get<Boolean>(DISMISSED) == true) {
                _uiState.value = StartupHintUiState.Hidden
            } else {
                val suppressed = try {
                    repository.isSuppressed()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // A failed read must not permanently hide the reminder.
                    false
                }
                _uiState.value = if (suppressed) {
                    StartupHintUiState.Hidden
                } else {
                    StartupHintUiState.Visible(
                        doNotShowAgain = savedState[DO_NOT_SHOW_AGAIN] ?: false,
                    )
                }
            }
        }
    }

    fun setDoNotShowAgain(checked: Boolean) {
        val state = _uiState.value as? StartupHintUiState.Visible ?: return
        if (state.isSaving) return
        savedState[DO_NOT_SHOW_AGAIN] = checked
        _uiState.value = state.copy(doNotShowAgain = checked, saveFailed = false)
    }

    fun dismiss() {
        val state = _uiState.value as? StartupHintUiState.Visible ?: return
        if (!state.isSaving) hideForThisLaunch()
    }

    fun confirm() {
        val state = _uiState.value as? StartupHintUiState.Visible ?: return
        if (state.isSaving) return
        if (!state.doNotShowAgain) {
            hideForThisLaunch()
            return
        }
        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            try {
                repository.suppress()
                hideForThisLaunch()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.value = state.copy(saveFailed = true)
            }
        }
    }

    private fun hideForThisLaunch() {
        savedState[DISMISSED] = true
        _uiState.value = StartupHintUiState.Hidden
    }

    private companion object {
        const val DISMISSED = "startup_hint_dismissed"
        const val DO_NOT_SHOW_AGAIN = "startup_hint_do_not_show_again"
    }
}

class StartupHintViewModelFactory(
    private val repository: StartupHintRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(StartupHintViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return StartupHintViewModel(repository, extras.createSavedStateHandle()) as T
    }
}
