package com.star.schedule.feature.timetable.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.core.database.entity.CourseEntity
import com.star.schedule.core.database.entity.CourseSessionEntity
import com.star.schedule.core.database.entity.CourseWithSessions
import com.star.schedule.core.database.entity.LessonTimeEntity
import com.star.schedule.feature.timetable.domain.CourseRules
import com.star.schedule.feature.timetable.domain.CourseRules.CourseNameError
import com.star.schedule.feature.timetable.domain.TimetableRepository
import kotlinx.coroutines.CancellationException
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

sealed interface CourseUndoState {
    val token: Long
    val courseName: String

    data class DeletedSession(
        override val token: Long,
        override val courseName: String,
    ) : CourseUndoState

    data class DeletedCourse(
        override val token: Long,
        override val courseName: String,
    ) : CourseUndoState
}

data class RenameCourseDialogState(
    val course: CourseEntity,
    val name: String,
    val error: CourseNameError? = null,
    val isSaving: Boolean = false,
)

data class DeleteCourseConfirmState(
    val course: CourseEntity,
    val sessions: List<CourseSessionEntity>,
)

data class CourseSettingsUiState(
    val isLoading: Boolean = true,
    val timetableId: Long? = null,
    val hasLessonTimes: Boolean = false,
    val totalCourseCount: Int = 0,
    val groups: List<CourseRules.CourseGroup> = emptyList(),
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val undo: CourseUndoState? = null,
    val renamingCourse: RenameCourseDialogState? = null,
    val deletingCourse: DeleteCourseConfirmState? = null,
)

class CourseSettingsViewModel(
    private val repository: TimetableRepository,
    timetableId: Long,
) : ViewModel() {

    private sealed interface PendingCourseUndo {
        val token: Long

        data class Session(
            override val token: Long,
            val timetableId: Long,
            val courseName: String,
            val session: CourseSessionEntity,
        ) : PendingCourseUndo

        data class WholeCourse(
            override val token: Long,
            val course: CourseEntity,
            val sessions: List<CourseSessionEntity>,
        ) : PendingCourseUndo
    }

    private val searchActive = MutableStateFlow(false)
    private val searchQuery = MutableStateFlow("")
    private val pendingUndo = MutableStateFlow<PendingCourseUndo?>(null)
    private val renamingCourse = MutableStateFlow<RenameCourseDialogState?>(null)
    private val deletingCourse = MutableStateFlow<DeleteCourseConfirmState?>(null)

    private val writeLock = Mutex()
    private var nextUndoToken = 0L
    private var latestCoursesWithSessions: List<CourseWithSessions> = emptyList()

    private val targetTimetableId: Flow<Long?> = flowOf(timetableId.takeIf { it > 0L })

    @OptIn(ExperimentalCoroutinesApi::class)
    private val lessonTimes: Flow<List<LessonTimeEntity>> = targetTimetableId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.observeLessonTimes(id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val coursesWithSessions: Flow<List<CourseWithSessions>> =
        targetTimetableId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.observeCoursesWithSessions(id)
        }

    val uiState: StateFlow<CourseSettingsUiState> = combine(
        targetTimetableId,
        lessonTimes,
        coursesWithSessions,
        combine(searchActive, searchQuery, pendingUndo) { active, query, undo ->
            Triple(active, query, undo)
        },
        combine(renamingCourse, deletingCourse) { rename, deleteConfirm ->
            rename to deleteConfirm
        },
    ) { id, times, courseList, searchAndUndo, dialogs ->
        latestCoursesWithSessions = courseList
        val (active, query, undo) = searchAndUndo
        val (renameDialog, deleteDialog) = dialogs
        val effectiveQuery = if (active) query else ""
        CourseSettingsUiState(
            isLoading = false,
            timetableId = id,
            hasLessonTimes = times.isNotEmpty(),
            totalCourseCount = courseList.size,
            groups = CourseRules.buildCourseGroups(
                coursesWithSessions = courseList,
                lessonTimes = times,
                query = effectiveQuery,
            ),
            searchActive = active,
            searchQuery = query,
            undo = when (undo) {
                is PendingCourseUndo.Session ->
                    CourseUndoState.DeletedSession(undo.token, undo.courseName)
                is PendingCourseUndo.WholeCourse ->
                    CourseUndoState.DeletedCourse(undo.token, undo.course.name)
                null -> null
            },
            renamingCourse = renameDialog,
            deletingCourse = deleteDialog,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CourseSettingsUiState(),
    )

    fun toggleSearch() {
        val next = !searchActive.value
        searchActive.value = next
        if (!next) {
            searchQuery.value = ""
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun clearSearchQuery() {
        searchQuery.value = ""
    }

    fun startRenameCourse(course: CourseEntity) {
        renamingCourse.value = RenameCourseDialogState(
            course = course,
            name = course.name,
        )
    }

    fun updateRenameCourseName(name: String) {
        renamingCourse.update { current ->
            current?.copy(name = name, error = null)
        }
    }

    fun dismissRenameCourse() {
        renamingCourse.update { current ->
            if (current?.isSaving == true) current else null
        }
    }

    fun confirmRenameCourse() {
        val dialog = renamingCourse.value ?: return
        if (dialog.isSaving) return
        val existingNames = latestCoursesWithSessions
            .filter { it.course.id != dialog.course.id }
            .map { it.course.name }
        val error = CourseRules.validateCourseName(
            name = dialog.name,
            existingNames = existingNames,
            currentName = null,
        )
        if (error != null) {
            renamingCourse.value = dialog.copy(error = error)
            return
        }

        val trimmedName = dialog.name.trim()
        if (trimmedName == dialog.course.name) {
            renamingCourse.value = null
            return
        }

        viewModelScope.launch {
            renamingCourse.update { it?.copy(isSaving = true) }
            writeLock.withLock {
                try {
                    repository.updateCourseWithReminders(dialog.course.copy(name = trimmedName))
                    renamingCourse.value = null
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    renamingCourse.update { it?.copy(isSaving = false) }
                }
            }
        }
    }

    fun requestDeleteCourse(group: CourseRules.CourseGroup) {
        val fullSessions = latestCoursesWithSessions
            .firstOrNull { it.course.id == group.course.id }
            ?.sessions
            ?: group.sessions.map { it.session }
        deletingCourse.value = DeleteCourseConfirmState(
            course = group.course,
            sessions = fullSessions,
        )
    }

    fun dismissDeleteCourse() {
        deletingCourse.value = null
    }

    fun confirmDeleteCourse() {
        val pending = deletingCourse.value ?: return
        deletingCourse.value = null
        viewModelScope.launch {
            writeLock.withLock {
                try {
                    repository.deleteCourseWithReminders(pending.course)
                    val token = ++nextUndoToken
                    pendingUndo.value = PendingCourseUndo.WholeCourse(
                        token = token,
                        course = pending.course,
                        sessions = pending.sessions,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 忽略删除异常，列表与数据库流保持同步
                }
            }
        }
    }

    /** 只删除单个上课时段，保留主表中的课程本体（即使时段清零也不自动删课）。 */
    fun deleteSession(course: CourseEntity, session: CourseSessionEntity) {
        val targetTimetable = uiState.value.timetableId ?: course.timetableId
        viewModelScope.launch {
            writeLock.withLock {
                try {
                    repository.deleteCourseSessionWithReminders(targetTimetable, session)
                    val token = ++nextUndoToken
                    pendingUndo.value = PendingCourseUndo.Session(
                        token = token,
                        timetableId = targetTimetable,
                        courseName = course.name,
                        session = session,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 忽略删除异常，界面保持与数据库流一致
                }
            }
        }
    }

    fun undoDelete(token: Long) {
        viewModelScope.launch {
            writeLock.withLock {
                val pending = pendingUndo.value ?: return@withLock
                if (pending.token != token) return@withLock
                pendingUndo.value = null
                try {
                    when (pending) {
                        is PendingCourseUndo.Session -> {
                            repository.insertCourseSessionWithReminders(
                                timetableId = pending.timetableId,
                                session = pending.session.copy(id = 0L),
                            )
                        }
                        is PendingCourseUndo.WholeCourse -> {
                            repository.restoreCourseWithSessionsWithReminders(
                                course = pending.course.copy(id = 0L),
                                sessions = pending.sessions.map { it.copy(id = 0L) },
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 忽略恢复失败，避免破坏列表订阅状态
                }
            }
        }
    }

    fun dismissUndo(token: Long) {
        pendingUndo.update { current ->
            if (current?.token == token) null else current
        }
    }
}

class CourseSettingsViewModelFactory(
    private val repository: TimetableRepository,
    private val timetableId: Long,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CourseSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CourseSettingsViewModel(repository, timetableId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
