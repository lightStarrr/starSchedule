package com.star.schedule.feature.timetable.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.core.database.LessonTimeTemplateEntity
import com.star.schedule.core.database.LessonTimeTemplateItemEntity
import com.star.schedule.feature.schedule.domain.ScheduleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 模板列表里的一行。
 *
 * 模板本身只有名字和更新时间，列表要的是「共几节」，
 * 所以节次在 ViewModel 里按 templateId 分组算好，界面不碰实体。
 */
data class LessonTimeTemplateRow(
    val id: Long,
    val name: String,
    val periodCount: Int,
    /** 最后一次改动的时刻（epoch millis），列表里直接显示，不展示具体时间段。 */
    val updatedAt: Long,
)

/** 一次可撤销的删除；[token] 单调递增，用来分辨「同名的两次删除」。 */
data class LessonTimeTemplatesUndo(
    val token: Long,
    val name: String,
)

enum class LessonTimeTemplatesMessageType { RestoreFailed }

data class LessonTimeTemplatesMessage(
    val token: Long,
    val type: LessonTimeTemplatesMessageType,
)

data class LessonTimeTemplatesUiState(
    val isLoading: Boolean = true,
    val rows: List<LessonTimeTemplateRow> = emptyList(),
    /** 待撤销的删除；null 表示没有。 */
    val undo: LessonTimeTemplatesUndo? = null,
    val message: LessonTimeTemplatesMessage? = null,
)

/**
 * 节次时间模板：全局的时间方案，和某一门课表无关。
 *
 * 删除立刻落库，撤销靠把模板连同它的节次按原 id 插回去；
 * 写操作串行化，避免两次快速删除时撤销数据互相覆盖。
 */
class LessonTimeTemplatesViewModel(
    private val repository: ScheduleRepository,
) : ViewModel() {
    /** 完整实体只留在 ViewModel 里：撤销要原样恢复，界面只需要名字。 */
    private val pendingUndo = MutableStateFlow<PendingUndo?>(null)

    private val message = MutableStateFlow<LessonTimeTemplatesMessage?>(null)

    private val writeLock = Mutex()

    private var nextUndoToken = 0L

    private var nextMessageToken = 0L

    val uiState: StateFlow<LessonTimeTemplatesUiState> = combine(
        repository.observeLessonTimeTemplates(),
        repository.observeLessonTimeTemplateItems(),
    ) { templates, items ->
        val itemsByTemplate = items.groupBy { it.templateId }
        templates.map { template ->
            LessonTimeTemplateRow(
                id = template.id,
                name = template.name,
                periodCount = itemsByTemplate[template.id].orEmpty().size,
                updatedAt = template.updatedAt,
            )
        }
    }.combine(pendingUndo) { rows, pending ->
        LessonTimeTemplatesUiState(
            isLoading = false,
            rows = rows,
            undo = pending?.let { LessonTimeTemplatesUndo(it.token, it.template.name) },
        )
    }.combine(message) { state, message ->
        state.copy(message = message)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LessonTimeTemplatesUiState(),
    )

    fun delete(row: LessonTimeTemplateRow) {
        viewModelScope.launch {
            writeLock.withLock {
                val template = repository.getLessonTimeTemplateByIdOnce(row.id) ?: return@withLock
                val items = repository.getLessonTimeTemplateItemsOnce(row.id)
                repository.deleteLessonTimeTemplate(template)
                nextUndoToken += 1
                pendingUndo.value = PendingUndo(
                    token = nextUndoToken,
                    template = template,
                    items = items,
                )
            }
        }
    }

    /**
     * [token] 必须是产生当前提示那次删除的 token：倒计时期间又删了一个模板时，
     * 旧提示迟到的回调不能把新的撤销数据清掉。
     */
    fun undoDelete(token: Long) {
        viewModelScope.launch {
            writeLock.withLock {
                val pending = pendingUndo.value ?: return@withLock
                if (pending.token != token) return@withLock
                // 先清状态再写库：提示立刻收起，连点两次也只会恢复一次。
                pendingUndo.value = null
                try {
                    repository.restoreLessonTimeTemplate(pending.template, pending.items)
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    // 常见原因是这 5 秒里又建了同名模板，唯一索引插不进去。
                    emitMessage(LessonTimeTemplatesMessageType.RestoreFailed)
                }
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

    fun onMessageShown(token: Long) {
        if (message.value?.token != token) return
        message.value = null
    }

    private fun emitMessage(type: LessonTimeTemplatesMessageType) {
        nextMessageToken += 1
        message.value = LessonTimeTemplatesMessage(token = nextMessageToken, type = type)
    }

    private data class PendingUndo(
        val token: Long,
        val template: LessonTimeTemplateEntity,
        val items: List<LessonTimeTemplateItemEntity>,
    )
}

class LessonTimeTemplatesViewModelFactory(
    private val repository: ScheduleRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LessonTimeTemplatesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LessonTimeTemplatesViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
