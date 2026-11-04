package com.star.schedule.feature.update.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.star.schedule.feature.update.domain.UpdateChannel
import com.star.schedule.feature.update.domain.UpdateCheckResult
import com.star.schedule.feature.update.domain.UpdateCheckTrigger
import com.star.schedule.feature.update.domain.UpdateRelease
import com.star.schedule.feature.update.domain.UpdateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class StartupUpdateGate(
    private val repository: UpdateRepository,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var coldStartCheck: Deferred<UpdateCheckResult>? = null
    private val promptedIdentities = mutableSetOf<String>()

    suspend fun checkColdStart(): UpdateCheckResult {
        val deferred = mutex.withLock {
            coldStartCheck ?: scope.async {
                repository.check(UpdateCheckTrigger.ColdStart)
            }.also { coldStartCheck = it }
        }
        return deferred.await()
    }

    fun tryClaim(identity: String): Boolean = promptedIdentities.add(identity)
}

data class StartupUpdateUiState(
    val release: UpdateRelease? = null,
    val canShowPrompt: Boolean = false,
    val dismissed: Boolean = false,
)

class StartupUpdateViewModel(
    private val gate: StartupUpdateGate,
    private val repository: UpdateRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StartupUpdateUiState())
    val uiState: StateFlow<StartupUpdateUiState> = _uiState.asStateFlow()

    private var claimedIdentity: String? = null

    init {
        viewModelScope.launch {
            val result = try {
                gate.checkColdStart()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@launch
            }
            val target = result.target ?: return@launch
            if (result.includeSnapshots != repositoryIncludeSnapshots()) {
                return@launch
            }
            _uiState.update { it.copy(release = target) }
        }
        viewModelScope.launch {
            repository.observeIncludeSnapshots().collect { enabled ->
                val release = _uiState.value.release
                if (!enabled && release != null &&
                    release.channel == UpdateChannel.Snapshot
                ) {
                    _uiState.update {
                        it.copy(release = null, canShowPrompt = false)
                    }
                }
            }
        }
    }

    fun onInteractive(identity: String) {
        if (_uiState.value.dismissed || _uiState.value.release?.identity != identity) return
        if (claimedIdentity == identity) {
            _uiState.update { it.copy(canShowPrompt = true) }
            return
        }
        if (!gate.tryClaim(identity)) return
        claimedIdentity = identity
        _uiState.update { it.copy(canShowPrompt = true) }
    }

    private suspend fun repositoryIncludeSnapshots(): Boolean = try {
        repository.observeIncludeSnapshots().first()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        true
    }

    fun dismiss() {
        _uiState.update { it.copy(dismissed = true, canShowPrompt = false) }
    }
}

class StartupUpdateViewModelFactory(
    private val gate: StartupUpdateGate,
    private val repository: UpdateRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(StartupUpdateViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return StartupUpdateViewModel(gate, repository) as T
    }
}
