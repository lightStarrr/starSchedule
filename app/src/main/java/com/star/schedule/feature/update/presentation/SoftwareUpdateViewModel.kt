package com.star.schedule.feature.update.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.star.schedule.feature.update.domain.CurrentInstallation
import com.star.schedule.feature.update.domain.UpdateCheckResult
import com.star.schedule.feature.update.domain.UpdateCheckTrigger
import com.star.schedule.feature.update.domain.UpdateDownloadFailure
import com.star.schedule.feature.update.domain.UpdateDownloadRepository
import com.star.schedule.feature.update.domain.UpdateDownloadState
import com.star.schedule.feature.update.domain.UpdateInstallPreparation
import com.star.schedule.feature.update.domain.UpdateIssue
import com.star.schedule.feature.update.domain.UpdateIssueReason
import com.star.schedule.feature.update.domain.UpdateRelease
import com.star.schedule.feature.update.domain.UpdateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SoftwareUpdateUiState(
    val installed: CurrentInstallation,
    val includeSnapshots: Boolean = false,
    val isRestoringDownloads: Boolean = false,
    val isChecking: Boolean = false,
    val result: UpdateCheckResult? = null,
    val preferenceWriteFailed: Boolean = false,
    val canInstallPackages: Boolean = false,
    val showInstallPermissionPrompt: Boolean = false,
    val isPreparingInstall: Boolean = false,
    val problemMessage: UpdateProblemMessage? = null,
)

sealed interface SoftwareUpdateEffect {
    data object OpenInstallSettings : SoftwareUpdateEffect

    data class StartInstall(val identity: String, val localPath: String) : SoftwareUpdateEffect
}

class SoftwareUpdateViewModel(
    private val repository: UpdateRepository,
    private val downloadRepository: UpdateDownloadRepository,
    private val installed: CurrentInstallation,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SoftwareUpdateUiState(installed, isRestoringDownloads = true),
    )
    val uiState: StateFlow<SoftwareUpdateUiState> = _uiState.asStateFlow()

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private val _effects = Channel<SoftwareUpdateEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var observeJob: Job? = null
    private var prepareJob: Job? = null
    private var recheckPending = false
    private var installGeneration = 0L
    private var nextFailureToken = 0L
    private var pendingInstallIdentity: String? = null
    private var pendingInstallPath: String? = null
    private var consumedInstallIdentity: String? = null
    private var initialCheckTrigger: UpdateCheckTrigger? = null

    init {
        viewModelScope.launch {
            repository.observeIncludeSnapshots().collect { enabled ->
                _uiState.update { it.copy(includeSnapshots = enabled) }
            }
        }
        viewModelScope.launch {
            try {
                downloadRepository.restore()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                reportDownloadFailure(null, UpdateDownloadFailure.Storage)
            }
            _uiState.update { it.copy(isRestoringDownloads = false) }
            val trigger = initialCheckTrigger ?: UpdateCheckTrigger.PageEnter
            initialCheckTrigger = null
            performCheck(trigger)
        }
    }

    fun checkNow() = performCheck(UpdateCheckTrigger.Manual)

    fun setIncludeSnapshots(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(preferenceWriteFailed = false) }
            try {
                repository.setIncludeSnapshots(enabled)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(preferenceWriteFailed = true) }
                return@launch
            }
            performCheck(UpdateCheckTrigger.PageEnter)
        }
    }

    fun setPageActive(active: Boolean) {
        if (active) {
            if (observeJob?.isActive == true) return
            observeJob = viewModelScope.launch {
                try {
                    downloadRepository.observe().collect { incoming ->
                        val current = _downloadState.value
                        if (incoming is UpdateDownloadState.Failed && incoming != current) {
                            reportDownloadFailure(incoming.target, incoming.reason)
                        } else {
                            _downloadState.value = incoming
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    reportDownloadFailure(
                        downloadTarget(),
                        UpdateDownloadFailure.Storage,
                    )
                }
            }
        } else {
            observeJob?.cancel()
            observeJob = null
        }
    }

    fun refreshInstallPermission(granted: Boolean) {
        _uiState.update { it.copy(canInstallPackages = granted) }
    }

    fun download(target: UpdateRelease) {
        invalidateInstallation()
        _uiState.update { it.copy(problemMessage = null) }
        viewModelScope.launch {
            try {
                downloadRepository.start(target)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                reportDownloadFailure(target, UpdateDownloadFailure.Unknown)
            }
        }
    }

    fun cancelDownload() {
        invalidateInstallation()
        _uiState.update {
            it.copy(
                showInstallPermissionPrompt = false,
                problemMessage = null,
            )
        }
        viewModelScope.launch {
            try {
                downloadRepository.cancel()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                reportDownloadFailure(
                    downloadTarget(),
                    UpdateDownloadFailure.Storage,
                )
            }
        }
    }

    fun install() {
        val ready = _downloadState.value as? UpdateDownloadState.Ready ?: return
        if (!_uiState.value.canInstallPackages) {
            _uiState.update { it.copy(showInstallPermissionPrompt = true) }
            return
        }
        if (prepareJob?.isActive == true ||
            pendingInstallIdentity != null ||
            consumedInstallIdentity != null
        ) return
        launchInstall(ready.target)
    }

    fun confirmInstallPermission() {
        if (!_uiState.value.showInstallPermissionPrompt) return
        _uiState.update { it.copy(showInstallPermissionPrompt = false) }
        viewModelScope.launch { _effects.send(SoftwareUpdateEffect.OpenInstallSettings) }
    }

    fun dismissInstallPermission() {
        _uiState.update { it.copy(showInstallPermissionPrompt = false) }
    }

    fun notifyInstallLaunched(identity: String) {
        if (consumedInstallIdentity != identity) return
        consumedInstallIdentity = null
    }

    fun notifyInstallLaunchFailed(identity: String) {
        if (consumedInstallIdentity != identity) return
        consumedInstallIdentity = null
        postProblem(UpdateProblem.InstallerUnavailable)
    }

    fun notifyOpenReleaseFailed() = postProblem(UpdateProblem.ReleasePageUnavailable)

    fun notifyOpenSettingsFailed() = postProblem(UpdateProblem.SettingsUnavailable)

    fun consumeInstallRequest(identity: String, localPath: String): String? {
        if (pendingInstallIdentity != identity ||
            pendingInstallPath != localPath ||
            downloadTarget()?.identity != identity
        ) return null
        pendingInstallIdentity = null
        pendingInstallPath = null
        consumedInstallIdentity = identity
        return localPath
    }

    private fun invalidateInstallation() {
        installGeneration++
        pendingInstallIdentity = null
        pendingInstallPath = null
        consumedInstallIdentity = null
        prepareJob?.cancel()
        prepareJob = null
        _uiState.update { it.copy(isPreparingInstall = false) }
    }

    private fun launchInstall(target: UpdateRelease) {
        _uiState.update { it.copy(isPreparingInstall = true) }
        val generation = ++installGeneration
        prepareJob = viewModelScope.launch {
            val clickedIdentity = target.identity
            try {
                when (val preparation = downloadRepository.prepareInstallation()) {
                    is UpdateInstallPreparation.Ready -> {
                        val current = _downloadState.value
                        val currentIdentity = when (current) {
                            is UpdateDownloadState.Ready -> current.target.identity
                            is UpdateDownloadState.Verifying -> current.target.identity
                            else -> null
                        }
                        val stillCurrent = generation == installGeneration &&
                            currentIdentity == clickedIdentity &&
                            preparation.verifiedPackage.identity == clickedIdentity
                        if (stillCurrent) {
                            pendingInstallIdentity = clickedIdentity
                            pendingInstallPath = preparation.verifiedPackage.localPath
                            _effects.send(
                                SoftwareUpdateEffect.StartInstall(
                                    clickedIdentity,
                                    preparation.verifiedPackage.localPath,
                                ),
                            )
                        }
                    }

                    is UpdateInstallPreparation.Failure -> {
                        if (generation == installGeneration) {
                            reportDownloadFailure(target, preparation.reason)
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation == installGeneration) {
                    reportDownloadFailure(target, UpdateDownloadFailure.Unknown)
                }
            } finally {
                if (generation == installGeneration) {
                    _uiState.update { it.copy(isPreparingInstall = false) }
                }
            }
        }
    }

    private fun reportDownloadFailure(
        target: UpdateRelease?,
        reason: UpdateDownloadFailure,
    ) {
        _downloadState.value = UpdateDownloadState.Failed(target, reason)
        postProblem(UpdateProblem.Download(reason))
    }

    private fun postProblem(problem: UpdateProblem) {
        nextFailureToken += 1
        _uiState.update {
            it.copy(
                problemMessage = UpdateProblemMessage(
                    nextFailureToken,
                    problem,
                ),
            )
        }
    }

    fun onProblemMessageShown(token: Long) {
        _uiState.update {
            if (it.problemMessage?.token == token) {
                it.copy(problemMessage = null)
            } else {
                it
            }
        }
    }

    private fun downloadTarget(): UpdateRelease? = when (val state = _downloadState.value) {
        is UpdateDownloadState.Starting -> state.target
        is UpdateDownloadState.Downloading -> state.target
        is UpdateDownloadState.Verifying -> state.target
        is UpdateDownloadState.Ready -> state.target
        is UpdateDownloadState.Failed -> state.target
        UpdateDownloadState.Idle -> null
    }

    private fun performCheck(trigger: UpdateCheckTrigger) {
        if (_uiState.value.isRestoringDownloads) {
            initialCheckTrigger = trigger
            return
        }
        if (_uiState.value.isChecking) {
            recheckPending = true
            return
        }
        _uiState.update { it.copy(isChecking = true) }
        viewModelScope.launch {
            val result = try {
                repository.check(trigger)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                UpdateCheckResult(
                    target = null,
                    displayedRelease = null,
                    issues = listOf(UpdateIssue(null, UpdateIssueReason.Network)),
                    checkedAtEpochMillis = System.currentTimeMillis(),
                    includeSnapshots = _uiState.value.includeSnapshots,
                )
            }
            val stale = result.includeSnapshots != _uiState.value.includeSnapshots
            _uiState.update {
                it.copy(
                    isChecking = false,
                    result = if (stale) it.result else result,
                )
            }
            if (recheckPending || stale) {
                recheckPending = false
                performCheck(UpdateCheckTrigger.PageEnter)
            }
        }
    }
}

class SoftwareUpdateViewModelFactory(
    private val repository: UpdateRepository,
    private val downloadRepository: UpdateDownloadRepository,
    private val installed: CurrentInstallation,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(SoftwareUpdateViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return SoftwareUpdateViewModel(repository, downloadRepository, installed) as T
    }
}
