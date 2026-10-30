package com.star.schedule.feature.timetable.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.LessonTimeApplySnapshot
import com.star.schedule.core.database.LessonTimeTemplateEntity
import com.star.schedule.core.database.LessonTimeTemplateItemEntity
import com.star.schedule.feature.schedule.domain.ScheduleRepository
import com.star.schedule.feature.timetable.domain.LessonTimeApplyPreview
import com.star.schedule.feature.timetable.domain.LessonTimeTemplateApply
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

/**
 * 一次可撤销的操作。
 *
 * [token] 单调递增：同名模板被删两次、或连续应用两个模板时，界面靠它区分新旧提示，
 * 旧提示迟到的回调也不会把新的撤销数据清掉。
 */
sealed interface LessonTimeTemplatesUndo {
    val token: Long

    data class Deleted(override val token: Long, val name: String) : LessonTimeTemplatesUndo

    data class Applied(override val token: Long, val templateName: String) : LessonTimeTemplatesUndo
}

enum class LessonTimeTemplatesMessageType {
    RestoreFailed,
    ApplyFailed,
    ApplyUndoFailed,
    TemplateEmpty,
    NoTimetable,
}

data class LessonTimeTemplatesMessage(
    val token: Long,
    val type: LessonTimeTemplatesMessageType,
)

data class LessonTimeTemplatesUiState(
    val isLoading: Boolean = true,
    val rows: List<LessonTimeTemplateRow> = emptyList(),
    /** 待撤销的操作；null 表示没有。 */
    val undo: LessonTimeTemplatesUndo? = null,
    val message: LessonTimeTemplatesMessage? = null,
    /** 应用确认弹窗的内容；null 表示没弹。随课程流实时重算。 */
    val applyPreview: LessonTimeApplyPreview? = null,
    val isApplying: Boolean = false,
    /** 没有当前课表时「应用」置灰。 */
    val canApply: Boolean = false,
)

/**
 * 节次时间模板：全局的时间方案，和某一门课表无关。
 *
 * 删除与「应用到当前课表」都立刻落库，撤销靠快照；
 * 写操作串行化，避免两次快速操作时撤销数据互相覆盖。
 */
class LessonTimeTemplatesViewModel(
    private val repository: ScheduleRepository,
) : ViewModel() {
    /** 完整实体只留在 ViewModel 里：撤销要原样恢复，界面只需要名字。 */
    private val pendingUndo = MutableStateFlow<PendingUndo?>(null)

    private val message = MutableStateFlow<LessonTimeTemplatesMessage?>(null)

    /** 用户点了哪一行的「应用」，非 null 时弹确认框。 */
    private val applyTarget = MutableStateFlow<Long?>(null)

    private val isApplying = MutableStateFlow(false)

    private val writeLock = Mutex()

    private var nextUndoToken = 0L

    private var nextMessageToken = 0L

    private val currentTimetableId: Flow<Long?> = repository.observeCurrentTimetableId()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentTimetable = currentTimetableId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.observeTimetable(id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentCourses = currentTimetableId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeCourses(id)
    }

    private val browseData: Flow<BrowseData> = combine(
        repository.observeLessonTimeTemplates(),
        repository.observeLessonTimeTemplateItems(),
        currentTimetableId,
        currentTimetable,
        currentCourses,
    ) { templates, items, timetableId, timetable, courses ->
        val itemsByTemplate = items.groupBy { it.templateId }
        BrowseData(
            rows = templates.map { template ->
                LessonTimeTemplateRow(
                    id = template.id,
                    name = template.name,
                    periodCount = itemsByTemplate[template.id].orEmpty().size,
                    updatedAt = template.updatedAt,
                )
            },
            templates = templates,
            itemsByTemplate = itemsByTemplate,
            timetableId = timetableId,
            timetableName = timetable?.name.orEmpty(),
            courses = courses,
        )
    }

    val uiState: StateFlow<LessonTimeTemplatesUiState> = combine(
        browseData,
        pendingUndo,
        message,
        applyTarget,
        isApplying,
    ) { data, pending, message, target, applying ->
        LessonTimeTemplatesUiState(
            isLoading = false,
            rows = data.rows,
            undo = pending?.toUi(),
            message = message,
            canApply = data.timetableId != null,
            isApplying = applying,
            applyPreview = target?.let { templateId ->
                val template = data.templates.firstOrNull { it.id == templateId }
                if (template == null) {
                    null
                } else {
                    LessonTimeTemplateApply.preview(
                        templateName = template.name,
                        timetableName = data.timetableName,
                        items = data.itemsByTemplate[templateId].orEmpty(),
                        courses = data.courses,
                    )
                }
            },
        )
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
                pendingUndo.value = PendingUndo.Deleted(
                    token = nextUndoToken,
                    template = template,
                    items = items,
                )
            }
        }
    }

    /** 点行内的「应用」：先摆出确认弹窗，真正写库在 [confirmApply]。 */
    fun requestApply(row: LessonTimeTemplateRow) {
        if (row.periodCount == 0) {
            emitMessage(LessonTimeTemplatesMessageType.TemplateEmpty)
            return
        }
        if (!uiState.value.canApply) {
            emitMessage(LessonTimeTemplatesMessageType.NoTimetable)
            return
        }
        applyTarget.value = row.id
    }

    fun cancelApply() {
        if (isApplying.value) return
        applyTarget.value = null
    }

    fun confirmApply() {
        val templateId = applyTarget.value ?: return
        if (isApplying.value) return
        val templateName = uiState.value.rows.firstOrNull { it.id == templateId }?.name.orEmpty()
        viewModelScope.launch {
            writeLock.withLock {
                isApplying.value = true
                try {
                    // 重新读一次当前课表：弹窗期间用户可能切走过。
                    val timetableId = repository.observeCurrentTimetableId().firstOrNull()
                    if (timetableId == null) {
                        applyTarget.value = null
                        emitMessage(LessonTimeTemplatesMessageType.NoTimetable)
                        return@withLock
                    }
                    val snapshot = repository.applyLessonTimeTemplateToTimetable(timetableId, templateId)
                    if (snapshot == null) {
                        applyTarget.value = null
                        emitMessage(LessonTimeTemplatesMessageType.TemplateEmpty)
                        return@withLock
                    }
                    applyTarget.value = null
                    nextUndoToken += 1
                    pendingUndo.value = PendingUndo.Applied(
                        token = nextUndoToken,
                        templateName = templateName,
                        snapshot = snapshot,
                    )
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    emitMessage(LessonTimeTemplatesMessageType.ApplyFailed)
                } finally {
                    isApplying.value = false
                }
            }
        }
    }

    /**
     * [token] 必须是产生当前提示那次操作的 token：倒计时期间又删/又应用时，
     * 旧提示迟到的回调不能把新的撤销数据清掉。
     */
    fun undo(token: Long) {
        viewModelScope.launch {
            writeLock.withLock {
                val pending = pendingUndo.value ?: return@withLock
                if (pending.token != token) return@withLock
                // 先清状态再写库：提示立刻收起，连点两次也只会恢复一次。
                pendingUndo.value = null
                try {
                    when (pending) {
                        is PendingUndo.Deleted ->
                            repository.restoreLessonTimeTemplate(pending.template, pending.items)

                        is PendingUndo.Applied ->
                            repository.restoreAppliedLessonTimeTemplate(pending.snapshot)
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    emitMessage(
                        when (pending) {
                            // 删除的撤销失败常见原因是这 5 秒里又建了同名模板。
                            is PendingUndo.Deleted -> LessonTimeTemplatesMessageType.RestoreFailed
                            is PendingUndo.Applied -> LessonTimeTemplatesMessageType.ApplyUndoFailed
                        },
                    )
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

    private fun PendingUndo.toUi(): LessonTimeTemplatesUndo = when (this) {
        is PendingUndo.Deleted -> LessonTimeTemplatesUndo.Deleted(token, template.name)
        is PendingUndo.Applied -> LessonTimeTemplatesUndo.Applied(token, templateName)
    }

    /** 列表与「应用预览」都要用的原始数据，只在 ViewModel 内部流转。 */
    private data class BrowseData(
        val rows: List<LessonTimeTemplateRow>,
        val templates: List<LessonTimeTemplateEntity>,
        val itemsByTemplate: Map<Long, List<LessonTimeTemplateItemEntity>>,
        val timetableId: Long?,
        val timetableName: String,
        val courses: List<CourseEntity>,
    )

    private sealed interface PendingUndo {
        val token: Long

        data class Deleted(
            override val token: Long,
            val template: LessonTimeTemplateEntity,
            val items: List<LessonTimeTemplateItemEntity>,
        ) : PendingUndo

        data class Applied(
            override val token: Long,
            val templateName: String,
            val snapshot: LessonTimeApplySnapshot,
        ) : PendingUndo
    }
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
