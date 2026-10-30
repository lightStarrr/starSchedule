package com.star.schedule.feature.timetable.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.core.database.LessonTimeTemplateEntity
import com.star.schedule.core.database.LessonTimeTemplateItemEntity
import com.star.schedule.feature.schedule.domain.ScheduleRepository
import com.star.schedule.feature.timetable.domain.LessonTimeRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 重命名对话框里的校验错误。 */
enum class RenameTemplateError { EmptyName, NameTooLong, NameTaken }

data class RenameTemplateDialog(
    val name: String,
    val error: RenameTemplateError? = null,
)

enum class LessonTimeTemplateEditMessageType { SaveFailed }

data class LessonTimeTemplateEditMessage(
    val token: Long,
    val type: LessonTimeTemplateEditMessageType,
)

data class LessonTimeTemplateEditUiState(
    val isLoading: Boolean = true,
    /** 模板已经被别处删掉了：不再展示编辑器，只提示一句。 */
    val isMissing: Boolean = false,
    val name: String = "",
    val rows: List<LessonTimeRow> = emptyList(),
    val editor: LessonTimeEditor? = null,
    val rename: RenameTemplateDialog? = null,
    val undo: LessonTimeUndo? = null,
    val isSaving: Boolean = false,
    /** 和打开页面时的快照有差异：保存按钮的可用状态、返回时的放弃确认都看它。 */
    val isDirty: Boolean = false,
    /** 返回时弹「放弃修改？」 */
    val discardConfirm: Boolean = false,
    /** 页面可以关了（保存成功或确认放弃）。 */
    val finish: Boolean = false,
    val message: LessonTimeTemplateEditMessage? = null,
)

/**
 * 编辑单个节次时间模板。
 *
 * 所有改动（改名、加减节次）先落在内存草稿里，点保存才整份写库：
 * 模板是「一份完整方案」，中途半保存会让列表里的「共 N 节」长时间处于错误状态。
 * 节次号不手填，任何改动后都按开始时间重排 1..n，和课表里的节次时间保持一致的行为。
 */
class LessonTimeTemplateEditViewModel(
    private val repository: ScheduleRepository,
    private val templateId: Long,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LessonTimeTemplateEditUiState())

    val uiState: StateFlow<LessonTimeTemplateEditUiState> = mutableState.asStateFlow()

    /** 原始实体（不含草稿改动），保存时只替换名称和 updatedAt。 */
    private var template: LessonTimeTemplateEntity? = null

    private var originalName: String = ""

    private var originalRows: List<Pair<String, String>> = emptyList()

    /** 草稿行的 id 用负数，和数据库里的自增 id 不会撞。 */
    private var nextDraftId = -1L

    private var pendingUndoRow: LessonTimeRow? = null

    private var nextUndoToken = 0L

    private var nextMessageToken = 0L

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val template = repository.getLessonTimeTemplateByIdOnce(templateId)
        if (template == null) {
            mutableState.update { it.copy(isLoading = false, isMissing = true) }
            return
        }
        val items = repository.getLessonTimeTemplateItemsOnce(templateId)
        this.template = template
        originalName = template.name
        val rows = items
            .sortedBy { LessonTimeRules.parseMinutes(it.startTime) ?: Int.MAX_VALUE }
            .map { item ->
                LessonTimeRow(
                    id = newDraftId(),
                    period = item.period,
                    startTime = item.startTime,
                    endTime = item.endTime,
                )
            }
        originalRows = rows.map { it.startTime to it.endTime }
        mutableState.value = LessonTimeTemplateEditUiState(
            isLoading = false,
            name = template.name,
            rows = rows.mapIndexed { index, row -> row.copy(period = index + 1) },
        )
    }

    fun startAdd() {
        val rows = mutableState.value.rows
        val start = rows.mapNotNull { it.spanMinutes }.maxOfOrNull { it.last } ?: DEFAULT_START_MINUTES
        mutableState.update {
            it.copy(
                editor = LessonTimeEditor(
                    startTime = LessonTimeRules.formatMinutes(start),
                    endTime = LessonTimeRules.formatMinutes(start + DEFAULT_DURATION_MINUTES),
                ),
            )
        }
    }

    fun startEdit(row: LessonTimeRow) {
        mutableState.update {
            it.copy(
                editor = LessonTimeEditor(
                    editingId = row.id,
                    startTime = row.startTime,
                    endTime = row.endTime,
                ),
            )
        }
    }

    fun selectField(field: LessonTimeField) {
        mutableState.update { state ->
            state.copy(editor = state.editor?.copy(field = field, error = null))
        }
    }

    fun setTime(field: LessonTimeField, minutes: Int) {
        val text = LessonTimeRules.formatMinutes(minutes)
        mutableState.update { state ->
            val editor = state.editor ?: return@update state
            state.copy(
                editor = editor.copy(
                    startTime = if (field == LessonTimeField.Start) text else editor.startTime,
                    endTime = if (field == LessonTimeField.End) text else editor.endTime,
                    error = null,
                ),
            )
        }
    }

    fun dismissEditor() {
        if (mutableState.value.editor?.isSaving == true) return
        mutableState.update { it.copy(editor = null) }
    }

    /** 编辑浮层的「保存」：只是把这一节放进草稿，不落库。 */
    fun saveEditor() {
        val editor = mutableState.value.editor ?: return
        val others = mutableState.value.rows
            .filter { it.id != editor.editingId }
            .mapNotNull { it.spanMinutes }
        val error = validateLessonTimeRange(editor.startTime, editor.endTime, others)
        if (error != null) {
            mutableState.update { it.copy(editor = editor.copy(error = error)) }
            return
        }
        mutableState.update { state ->
            val rows = if (editor.editingId == null) {
                state.rows + LessonTimeRow(
                    id = newDraftId(),
                    period = state.rows.size + 1,
                    startTime = editor.startTime,
                    endTime = editor.endTime,
                )
            } else {
                state.rows.map { row ->
                    if (row.id == editor.editingId) {
                        row.copy(startTime = editor.startTime, endTime = editor.endTime)
                    } else {
                        row
                    }
                }
            }
            normalize(state.copy(editor = null, rows = rows))
        }
    }

    fun delete(row: LessonTimeRow) {
        mutableState.update { state ->
            normalize(
                state.copy(
                    rows = state.rows.filterNot { it.id == row.id },
                    undo = null,
                ),
            )
        }
        pendingUndoRow = row
        nextUndoToken += 1
        mutableState.update {
            it.copy(undo = LessonTimeUndo(token = nextUndoToken, period = row.period))
        }
    }

    fun undoDelete(token: Long) {
        val row = pendingUndoRow
        if (row == null || mutableState.value.undo?.token != token) return
        pendingUndoRow = null
        mutableState.update { state ->
            normalize(
                state.copy(
                    rows = state.rows + row.copy(id = newDraftId()),
                    undo = null,
                ),
            )
        }
    }

    fun dismissUndo(token: Long) {
        if (mutableState.value.undo?.token != token) return
        pendingUndoRow = null
        mutableState.update { it.copy(undo = null) }
    }

    fun startRename() {
        mutableState.update { it.copy(rename = RenameTemplateDialog(name = it.name)) }
    }

    fun updateRenameName(name: String) {
        mutableState.update { state ->
            state.copy(rename = state.rename?.copy(name = name, error = null))
        }
    }

    fun dismissRename() {
        mutableState.update { it.copy(rename = null) }
    }

    fun confirmRename() {
        val dialog = mutableState.value.rename ?: return
        val name = dialog.name.trim()
        val error = when {
            name.isBlank() -> RenameTemplateError.EmptyName
            name.length > LessonTimeSettingsViewModel.MAX_TEMPLATE_NAME_LENGTH ->
                RenameTemplateError.NameTooLong
            else -> null
        }
        if (error != null) {
            mutableState.update { it.copy(rename = dialog.copy(error = error)) }
            return
        }
        viewModelScope.launch {
            val existingId = try {
                repository.getLessonTimeTemplateIdByName(name)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                null
            }
            if (existingId != null && existingId != templateId) {
                mutableState.update {
                    it.copy(rename = dialog.copy(error = RenameTemplateError.NameTaken))
                }
                return@launch
            }
            mutableState.update { state ->
                normalize(state.copy(name = name, rename = null))
            }
        }
    }

    fun requestBack() {
        val state = mutableState.value
        if (state.isDirty && !state.isSaving) {
            mutableState.update { it.copy(discardConfirm = true) }
        } else {
            mutableState.update { it.copy(finish = true) }
        }
    }

    fun dismissDiscard() {
        mutableState.update { it.copy(discardConfirm = false) }
    }

    fun confirmDiscard() {
        mutableState.update { it.copy(discardConfirm = false, finish = true) }
    }

    fun save() {
        val state = mutableState.value
        val template = template ?: return
        if (state.isSaving || !state.isDirty || state.isLoading) return
        viewModelScope.launch {
            mutableState.update { it.copy(isSaving = true) }
            try {
                repository.replaceLessonTimeTemplate(
                    template = template.copy(
                        name = state.name,
                        updatedAt = System.currentTimeMillis(),
                    ),
                    items = state.rows.mapIndexed { index, row ->
                        LessonTimeTemplateItemEntity(
                            templateId = template.id,
                            period = index + 1,
                            startTime = row.startTime,
                            endTime = row.endTime,
                        )
                    },
                )
                mutableState.update { it.copy(isSaving = false, finish = true) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                nextMessageToken += 1
                mutableState.update { current ->
                    current.copy(
                        isSaving = false,
                        message = LessonTimeTemplateEditMessage(
                            token = nextMessageToken,
                            type = LessonTimeTemplateEditMessageType.SaveFailed,
                        ),
                    )
                }
            }
        }
    }

    fun onMessageShown(token: Long) {
        if (mutableState.value.message?.token != token) return
        mutableState.update { it.copy(message = null) }
    }

    private fun newDraftId(): Long {
        val id = nextDraftId
        nextDraftId -= 1
        return id
    }

    /** 任何改动后统一走这里：按开始时间排序、重排节次号、重算 isDirty。 */
    private fun normalize(state: LessonTimeTemplateEditUiState): LessonTimeTemplateEditUiState {
        val rows = state.rows
            .sortedBy { LessonTimeRules.parseMinutes(it.startTime) ?: Int.MAX_VALUE }
            .mapIndexed { index, row -> row.copy(period = index + 1) }
        val dirty = state.name != originalName || rows.map { it.startTime to it.endTime } != originalRows
        return state.copy(rows = rows, isDirty = dirty)
    }

    companion object {
        private const val DEFAULT_START_MINUTES = 8 * 60
        private const val DEFAULT_DURATION_MINUTES = 45
    }
}

class LessonTimeTemplateEditViewModelFactory(
    private val repository: ScheduleRepository,
    private val templateId: Long,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LessonTimeTemplateEditViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LessonTimeTemplateEditViewModel(repository, templateId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
