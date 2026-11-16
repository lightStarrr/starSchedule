package com.star.schedule.feature.importing.sharecode.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.feature.importing.sharecode.domain.ShareCodeImportResult
import com.star.schedule.feature.importing.sharecode.domain.ShareCodeImporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShareCodeImportUiState(
    val input: String = "",
    val shareCode: String = "",
    val phase: ShareCodeImportPhase = ShareCodeImportPhase.Idle,
) {
    val isImporting: Boolean get() = phase is ShareCodeImportPhase.Importing

    /** 只有识别出口令才允许提交，按钮的可用状态直接跟着这一条走。 */
    val canImport: Boolean
        get() = shareCode.isNotEmpty() &&
            phase !is ShareCodeImportPhase.Importing &&
            phase !is ShareCodeImportPhase.Finished
}

sealed interface ShareCodeImportPhase {
    data object Idle : ShareCodeImportPhase

    data object Importing : ShareCodeImportPhase

    data class Failed(val error: ShareCodeImportError) : ShareCodeImportPhase

    data class Finished(val timetableId: Long) : ShareCodeImportPhase
}

/**
 * 点击到真正发请求之间的等待时长：等于整段出场编排的时长。
 *
 * 编排（箭头飞走 → 文案上移 → 输入框收进按钮 → 按钮落到屏幕中心 → 变成加载指示器）一共
 * 900ms，界面那边用同一个值做时间轴总长。请求要等它走完再发，否则口令一错就立刻失败，
 * 时间轴还没走到屏幕中心就被倒放，看起来像点了个空——那段"飞过去"的动画等于没播。
 */
internal const val SHARE_CODE_SUBMIT_ANIMATION_MILLIS = 900L

/**
 * 导入失败的类别。文案在 UI 层映射，避免 ViewModel 持有 Context。
 */
enum class ShareCodeImportError {
    EmptyCode,
    InvalidCode,
    Network,
    Rejected,
    Unsupported,
    Busy,
}

class ShareCodeImportViewModel(
    private val importer: ShareCodeImporter,
    private val extractCode: (String) -> String,
    private val errorOf: (Throwable) -> ShareCodeImportError,
    private val selectTimetable: suspend (Long) -> Unit,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ShareCodeImportUiState())
    val uiState: StateFlow<ShareCodeImportUiState> = _uiState.asStateFlow()

    fun onInputChange(value: String) {
        _uiState.update {
            it.copy(
                input = value,
                shareCode = extractCode(value),
                phase = ShareCodeImportPhase.Idle,
            )
        }
    }

    fun import() {
        val state = _uiState.value
        if (state.isImporting) return

        if (state.input.isBlank()) {
            fail(ShareCodeImportError.EmptyCode)
            return
        }
        if (state.shareCode.isEmpty()) {
            fail(ShareCodeImportError.InvalidCode)
            return
        }

        _uiState.update { it.copy(phase = ShareCodeImportPhase.Importing) }
        viewModelScope.launch {
            // phase 一进 Importing 界面就开始播出场编排；编排播完、按钮已经变成加载指示器，
            // 这里才真正去取课表。见 SHARE_CODE_SUBMIT_ANIMATION_MILLIS 的说明。
            delay(SHARE_CODE_SUBMIT_ANIMATION_MILLIS)
            when (val result = importer.import(state.shareCode)) {
                is ShareCodeImportResult.Success -> {
                    // 导入出来的课表就是用户接下来要看的那张，切过去再结束页面。
                    selectTimetable(result.timetableId)
                    _uiState.update { it.copy(phase = ShareCodeImportPhase.Finished(result.timetableId)) }
                }
                is ShareCodeImportResult.Failure -> fail(errorOf(result.cause))
            }
        }
    }

    private fun fail(error: ShareCodeImportError) {
        _uiState.update { it.copy(phase = ShareCodeImportPhase.Failed(error)) }
    }
}

class ShareCodeImportViewModelFactory(
    private val importer: ShareCodeImporter,
    private val extractCode: (String) -> String,
    private val errorOf: (Throwable) -> ShareCodeImportError,
    private val selectTimetable: suspend (Long) -> Unit,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ShareCodeImportViewModel(importer, extractCode, errorOf, selectTimetable) as T
    }
}
