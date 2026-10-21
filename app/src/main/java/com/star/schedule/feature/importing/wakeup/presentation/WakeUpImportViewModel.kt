package com.star.schedule.feature.importing.wakeup.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.feature.importing.wakeup.domain.ImportWakeUpScheduleUseCase
import com.star.schedule.feature.importing.wakeup.domain.WakeUpImportResult
import com.star.schedule.feature.importing.wakeup.domain.WakeUpProtocolException
import com.star.schedule.feature.importing.wakeup.domain.extractKeyFromShareText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import java.io.IOException

data class WakeUpImportUiState(
    val input: String = "",
    val shareKey: String = "",
    val phase: WakeUpImportPhase = WakeUpImportPhase.Idle,
) {
    val isImporting: Boolean get() = phase is WakeUpImportPhase.Importing

    /** 只有识别出口令才允许提交，按钮的可用状态直接跟着这一条走。 */
    val canImport: Boolean
        get() = shareKey.isNotEmpty() &&
            phase !is WakeUpImportPhase.Importing &&
            phase !is WakeUpImportPhase.Finished
}

sealed interface WakeUpImportPhase {
    data object Idle : WakeUpImportPhase

    data object Importing : WakeUpImportPhase

    data class Failed(val error: WakeUpImportError) : WakeUpImportPhase

    data class Finished(val timetableId: Long) : WakeUpImportPhase
}

/**
 * 点击到真正发请求之间的等待时长：等于整段出场编排的时长。
 *
 * 编排（箭头飞走 → 文案上移 → 输入框收进按钮 → 按钮落到屏幕中心 → 变成加载指示器）一共
 * 900ms，界面那边用同一个值做时间轴总长。请求要等它走完再发，否则口令一错就立刻失败，
 * 时间轴还没走到屏幕中心就被倒放，看起来像点了个空——那段"飞过去"的动画等于没播。
 */
internal const val WAKEUP_SUBMIT_ANIMATION_MILLIS = 900L

/**
 * 导入失败的类别。文案在 UI 层映射，避免 ViewModel 持有 Context。
 */
enum class WakeUpImportError {
    EmptyCode,
    InvalidCode,
    Network,
    Rejected,
    Unsupported,
}

class WakeUpImportViewModel(
    private val importSchedule: ImportWakeUpScheduleUseCase,
    private val selectTimetable: suspend (Long) -> Unit,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WakeUpImportUiState())
    val uiState: StateFlow<WakeUpImportUiState> = _uiState.asStateFlow()

    fun onInputChange(value: String) {
        _uiState.update {
            it.copy(
                input = value,
                shareKey = extractKeyFromShareText(value),
                phase = WakeUpImportPhase.Idle,
            )
        }
    }

    fun import() {
        val state = _uiState.value
        if (state.isImporting) return

        if (state.input.isBlank()) {
            fail(WakeUpImportError.EmptyCode)
            return
        }
        if (state.shareKey.isEmpty()) {
            fail(WakeUpImportError.InvalidCode)
            return
        }

        _uiState.update { it.copy(phase = WakeUpImportPhase.Importing) }
        viewModelScope.launch {
            // phase 一进 Importing 界面就开始播出场编排；编排播完、按钮已经变成加载指示器，
            // 这里才真正去取课表。见 WAKEUP_SUBMIT_ANIMATION_MILLIS 的说明。
            delay(WAKEUP_SUBMIT_ANIMATION_MILLIS)
            when (val result = importSchedule(state.shareKey)) {
                is WakeUpImportResult.Success -> {
                    // 导入出来的课表就是用户接下来要看的那张，切过去再结束页面。
                    selectTimetable(result.timetableId)
                    _uiState.update { it.copy(phase = WakeUpImportPhase.Finished(result.timetableId)) }
                }
                is WakeUpImportResult.Failure -> fail(result.cause.toError())
            }
        }
    }

    private fun fail(error: WakeUpImportError) {
        _uiState.update { it.copy(phase = WakeUpImportPhase.Failed(error)) }
    }

    private fun Throwable.toError(): WakeUpImportError = when (this) {
        // okhttp 连不上、超时都走 IOException。
        is IOException -> WakeUpImportError.Network
        // 协议常量过期、签名被拒、密文解不开：跟用户输错口令无关，别让他反复重试。
        is WakeUpProtocolException -> WakeUpImportError.Unsupported
        // 返回体不是预期的 JSON：WakeUp 改过分享格式，不是口令本身的问题。
        is SerializationException -> WakeUpImportError.Unsupported
        // 服务端明确拒绝，或 HTTP 失败：口令无效/过期。
        else -> WakeUpImportError.Rejected
    }
}

class WakeUpImportViewModelFactory(
    private val importSchedule: ImportWakeUpScheduleUseCase,
    private val selectTimetable: suspend (Long) -> Unit,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return WakeUpImportViewModel(importSchedule, selectTimetable) as T
    }
}
