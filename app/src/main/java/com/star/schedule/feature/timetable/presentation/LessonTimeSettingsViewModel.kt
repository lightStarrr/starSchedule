package com.star.schedule.feature.timetable.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.core.database.LessonTimeEntity
import com.star.schedule.feature.schedule.domain.ScheduleRepository
import com.star.schedule.feature.timetable.domain.LessonTimeRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 时间列里的一行。节次号由数据库按开始时间排好，界面只负责显示。 */
data class LessonTimeRow(
    val id: Long,
    val period: Int,
    val startTime: String,
    val endTime: String,
) {
    /** 解析失败（历史数据被改坏）时为 null，这类行不参与重叠校验。 */
    val spanMinutes: IntRange?
        get() {
            val start = LessonTimeRules.parseMinutes(startTime) ?: return null
            val end = LessonTimeRules.parseMinutes(endTime) ?: return null
            return start..end
        }
}

/** 编辑浮层当前在改开始还是结束。 */
enum class LessonTimeField { Start, End }

enum class LessonTimeError { InvalidTime, EndNotAfterStart, Overlap }

/** 编辑浮层的草稿。[editingId] 为 null 表示新增。 */
data class LessonTimeEditor(
    val editingId: Long? = null,
    val startTime: String,
    val endTime: String,
    val field: LessonTimeField = LessonTimeField.Start,
    val error: LessonTimeError? = null,
    val isSaving: Boolean = false,
)

data class LessonTimeSettingsUiState(
    val isLoading: Boolean = true,
    val timetableId: Long? = null,
    val rows: List<LessonTimeRow> = emptyList(),
    val editor: LessonTimeEditor? = null,
    /** 刚删掉的节次号，用来出「已删除 + 撤销」提示；null 表示没有待撤销的删除。 */
    val deletedPeriod: Int? = null,
)

/**
 * 节次时间（首页左侧时间列）设置页。
 *
 * 只改当前课表的节次时间：首页时间列显示的、课程提醒算的，都是这份数据。
 * 所有写操作都经过 Repository 的 AutoSort 事务，节次号会跟着开始时间重新排。
 * 删除是立刻落库的，撤销靠把删掉的那条重新插回去。
 */
class LessonTimeSettingsViewModel(
    private val repository: ScheduleRepository,
) : ViewModel() {
    private val editor = MutableStateFlow<LessonTimeEditor?>(null)
    private val lastDeleted = MutableStateFlow<LessonTimeEntity?>(null)

    private val timetableId: Flow<Long?> = repository.observeCurrentTimetableId()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val lessonTimes: Flow<List<LessonTimeEntity>> = timetableId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeLessonTimes(id)
    }

    val uiState: StateFlow<LessonTimeSettingsUiState> = combine(
        timetableId,
        lessonTimes,
        editor,
        lastDeleted,
    ) { id, lessonTimes, draft, deleted ->
        LessonTimeSettingsUiState(
            isLoading = false,
            timetableId = id,
            rows = lessonTimes.sortedBy { it.period }.map { lessonTime ->
                LessonTimeRow(
                    id = lessonTime.id,
                    period = lessonTime.period,
                    startTime = lessonTime.startTime,
                    endTime = lessonTime.endTime,
                )
            },
            editor = draft,
            deletedPeriod = deleted?.period,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LessonTimeSettingsUiState(),
    )

    /** 新增：接在最后一节结束时间后面，没有节次时从 08:00 开始。 */
    fun startAdd() {
        val rows = uiState.value.rows.mapNotNull { it.spanMinutes }
        val start = rows.maxOfOrNull { it.last } ?: DEFAULT_START_MINUTES
        editor.value = LessonTimeEditor(
            startTime = LessonTimeRules.formatMinutes(start),
            endTime = LessonTimeRules.formatMinutes(start + DEFAULT_DURATION_MINUTES),
        )
    }

    fun startEdit(row: LessonTimeRow) {
        editor.value = LessonTimeEditor(
            editingId = row.id,
            startTime = row.startTime,
            endTime = row.endTime,
        )
    }

    fun selectField(field: LessonTimeField) {
        editor.update { current -> current?.copy(field = field, error = null) }
    }

    fun setTime(field: LessonTimeField, minutes: Int) {
        val text = LessonTimeRules.formatMinutes(minutes)
        editor.update { current ->
            current?.copy(
                startTime = if (field == LessonTimeField.Start) text else current.startTime,
                endTime = if (field == LessonTimeField.End) text else current.endTime,
                error = null,
            )
        }
    }

    fun dismissEditor() {
        if (editor.value?.isSaving == true) return
        editor.value = null
    }

    fun save() {
        val current = editor.value ?: return
        if (current.isSaving) return
        val state = uiState.value
        val timetableId = state.timetableId ?: return

        val start = LessonTimeRules.parseMinutes(current.startTime)
        val end = LessonTimeRules.parseMinutes(current.endTime)
        val error = when {
            start == null || end == null -> LessonTimeError.InvalidTime
            end <= start -> LessonTimeError.EndNotAfterStart
            else -> {
                val others = state.rows
                    .filter { it.id != current.editingId }
                    .mapNotNull { it.spanMinutes }
                if (LessonTimeRules.overlaps(start, end, others)) LessonTimeError.Overlap else null
            }
        }
        if (error != null) {
            editor.value = current.copy(error = error)
            return
        }

        val editing = current.editingId?.let { rowId -> state.rows.firstOrNull { it.id == rowId } }
        viewModelScope.launch {
            editor.value = current.copy(isSaving = true, error = null)
            if (editing == null) {
                repository.insertLessonTime(
                    LessonTimeEntity(
                        timetableId = timetableId,
                        // 节次号插入后会被 AutoSort 重排，这里给的值只影响重排前的瞬间。
                        period = state.rows.size + 1,
                        startTime = current.startTime,
                        endTime = current.endTime,
                    ),
                )
            } else {
                repository.updateLessonTime(
                    LessonTimeEntity(
                        id = editing.id,
                        timetableId = timetableId,
                        period = editing.period,
                        startTime = current.startTime,
                        endTime = current.endTime,
                    ),
                )
            }
            editor.value = null
        }
    }

    fun delete(row: LessonTimeRow) {
        val timetableId = uiState.value.timetableId ?: return
        val entity = LessonTimeEntity(
            id = row.id,
            timetableId = timetableId,
            period = row.period,
            startTime = row.startTime,
            endTime = row.endTime,
        )
        viewModelScope.launch {
            repository.deleteLessonTime(entity)
            lastDeleted.value = entity
        }
    }

    fun undoDelete() {
        val entity = lastDeleted.value ?: return
        lastDeleted.value = null
        viewModelScope.launch {
            // 原样插回去：节次号会被 AutoSort 重新排，不用关心删除期间空出的位置。
            repository.insertLessonTime(entity)
        }
    }

    fun dismissUndo() {
        lastDeleted.value = null
    }

    companion object {
        private const val DEFAULT_START_MINUTES = 8 * 60

        /** 新增一节时的默认时长，和常见 45 分钟一节课对齐。 */
        private const val DEFAULT_DURATION_MINUTES = 45
    }
}

class LessonTimeSettingsViewModelFactory(
    private val repository: ScheduleRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LessonTimeSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LessonTimeSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
