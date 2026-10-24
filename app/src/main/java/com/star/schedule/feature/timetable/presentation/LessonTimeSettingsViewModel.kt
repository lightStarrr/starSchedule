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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

/**
 * 一次可撤销的删除。
 *
 * [token] 单调递增：删掉某一节后，剩下的节次会被 AutoSort 重新编号，
 * 于是「删第 3 节、再把变成第 3 节的下一节删掉」两次的节次号完全一样。
 * 界面只靠 token 才能区分这是两次不同的删除。
 */
data class LessonTimeUndo(
    val token: Long,
    val period: Int,
)

data class LessonTimeSettingsUiState(
    val isLoading: Boolean = true,
    val timetableId: Long? = null,
    val rows: List<LessonTimeRow> = emptyList(),
    val editor: LessonTimeEditor? = null,
    /** 待撤销的删除；null 表示没有。新的删除会整个替换掉它，token 一定变。 */
    val undo: LessonTimeUndo? = null,
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

    /** 完整实体只留在 ViewModel 里，UiState 只暴露 token 和节次号。 */
    private val pendingUndo = MutableStateFlow<PendingUndo?>(null)

    /**
     * 串行化写操作。
     *
     * 删除必须「先落库、再发布可撤销状态」作为一个整体按调用顺序发生：
     * 两次快速删除各起一个 launch 时，谁先写完库谁后写状态，单槽状态就可能指向
     * 先删的那一条。Mutex 是 FIFO 公平的，加锁顺序即调用顺序。
     */
    private val writeLock = Mutex()

    /** 只在 [writeLock] 内递增，保证单调且与落库顺序一致。 */
    private var nextUndoToken = 0L

    private val timetableId: Flow<Long?> = repository.observeCurrentTimetableId()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val lessonTimes: Flow<List<LessonTimeEntity>> = timetableId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeLessonTimes(id)
    }

    val uiState: StateFlow<LessonTimeSettingsUiState> = combine(
        timetableId,
        lessonTimes,
        editor,
        pendingUndo,
    ) { id, lessonTimes, draft, pending ->
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
            undo = pending?.let { LessonTimeUndo(token = it.token, period = it.entity.period) },
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
            writeLock.withLock {
                repository.deleteLessonTime(entity)
                // 提示里的节次号取 entity 里用户划掉那一刻看到的值：删除后剩下的会被
                // 重新编号，回读回来的是别的行的号，反而对不上用户的操作。
                nextUndoToken += 1
                pendingUndo.value = PendingUndo(token = nextUndoToken, entity = entity)
            }
        }
    }

    /**
     * [token] 必须是产生当前提示那次删除的 token。
     *
     * 倒计时期间又删了一节时，旧提示的回调可能迟到（旧提示被取消、用户点击与状态
     * 更新之间存在窗口）。带着 token 回来，只处理自己那一条，不会误撤销新删除。
     */
    fun undoDelete(token: Long) {
        viewModelScope.launch {
            writeLock.withLock {
                val pending = pendingUndo.value ?: return@withLock
                if (pending.token != token) return@withLock
                // 先清状态再插库：提示立刻收起，重复点击也会因 token 不匹配而变成空操作。
                pendingUndo.value = null
                // 原样插回去：节次号会被 AutoSort 重新排，不用关心删除期间空出的位置。
                repository.insertLessonTime(pending.entity)
            }
        }
    }

    /** 倒计时走完或用户划掉提示；同样只清掉自己那一条。 */
    fun dismissUndo(token: Long) {
        viewModelScope.launch {
            writeLock.withLock {
                if (pendingUndo.value?.token != token) return@withLock
                pendingUndo.value = null
            }
        }
    }

    /** 待撤销的删除：给界面的是 [LessonTimeUndo]，撤销需要的完整实体留在内部。 */
    private data class PendingUndo(
        val token: Long,
        val entity: LessonTimeEntity,
    )

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
