package com.star.schedule.feature.timetable.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.core.database.entity.CourseSessionEntity
import com.star.schedule.core.database.entity.CourseWithSessions
import com.star.schedule.core.database.entity.LessonTimeEntity
import com.star.schedule.feature.timetable.domain.CourseRules
import com.star.schedule.feature.timetable.domain.CourseRules.CourseConflict
import com.star.schedule.feature.timetable.domain.CourseRules.CourseValidationError
import com.star.schedule.feature.timetable.domain.TimetableRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CourseNameSuggestion(
    val courseId: Long,
    val name: String,
    val teacher: String,
    val location: String,
    val weeks: Set<Int>,
)

enum class CourseEditMessageType {
    SavedAddNext,
    SaveFailed,
    DeleteFailed,
}

data class CourseEditMessage(
    val token: Long,
    val type: CourseEditMessageType,
)

data class CourseEditUiState(
    val isLoading: Boolean = true,
    val isNewSession: Boolean = true,
    val showCourseSelectorMode: Boolean = false,
    val isMissing: Boolean = false,
    val missingIsCourse: Boolean = false,
    val hasLessonTimes: Boolean = true,
    val lessonTimes: List<LessonTimeEntity> = emptyList(),
    val availableCourses: List<CourseNameSuggestion> = emptyList(),
    val selectedCourseId: Long? = null,
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val dayOfWeek: Int = 1,
    val selectedPeriods: Set<Int> = emptySet(),
    val selectedWeeks: Set<Int> = emptySet(),
    val showExtendedWeeks: Boolean = false,
    val conflicts: List<CourseConflict> = emptyList(),
    val validationError: CourseValidationError? = null,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val showDiscardConfirm: Boolean = false,
    val hasChanges: Boolean = false,
    val finish: Boolean = false,
    val message: CourseEditMessage? = null,
) {
    /** 顶栏保存按钮是否可用：必填和格式校验通过，且在编辑已有时段时存在改动。 */
    val canSave: Boolean
        get() {
            if (isLoading || isMissing || !hasLessonTimes || isSaving || isDeleting) return false
            if (!isNewSession && !hasChanges) return false
            val availablePeriods = lessonTimes.map { it.period }.toSet()
            return CourseRules.validateCourseDraft(
                name = name,
                teacher = teacher,
                location = location,
                periods = selectedPeriods,
                weeks = selectedWeeks,
                availablePeriods = availablePeriods,
            ) == null
        }
}

class CourseEditViewModel(
    private val repository: TimetableRepository,
    private val timetableId: Long,
    private val courseId: Long = -1L,
    private val sessionId: Long = -1L,
) : ViewModel() {

    private data class DraftSnapshot(
        val selectedCourseId: Long?,
        val name: String,
        val teacher: String,
        val location: String,
        val dayOfWeek: Int,
        val selectedPeriods: Set<Int>,
        val selectedWeeks: Set<Int>,
    )

    private val isEditingExistingSession = sessionId > 0L
    private val isAddingToSpecificCourse = !isEditingExistingSession && courseId > 0L

    private val mutableState = MutableStateFlow(
        CourseEditUiState(
            isNewSession = !isEditingExistingSession,
            showCourseSelectorMode = isEditingExistingSession || isAddingToSpecificCourse,
        ),
    )
    val uiState: StateFlow<CourseEditUiState> = mutableState.asStateFlow()

    private var initialized = false
    private var originalSnapshot: DraftSnapshot? = null
    private var editingSessionEntity: CourseSessionEntity? = null
    private var latestCoursesWithSessions: List<CourseWithSessions> = emptyList()
    private var nextMessageToken = 0L

    init {
        viewModelScope.launch {
            combine(
                repository.observeLessonTimes(timetableId),
                repository.observeCoursesWithSessions(timetableId),
            ) { times, courses ->
                times.sortedBy { it.period } to courses
            }.collect { (sortedTimes, courses) ->
                onSourceUpdated(sortedTimes, courses)
            }
        }
    }

    private fun onSourceUpdated(
        sortedTimes: List<LessonTimeEntity>,
        coursesWithSessions: List<CourseWithSessions>,
    ) {
        latestCoursesWithSessions = coursesWithSessions
        val groups = CourseRules.buildCourseGroups(coursesWithSessions, sortedTimes)
        val suggestions = groups.map { group ->
            CourseNameSuggestion(
                courseId = group.course.id,
                name = group.name,
                teacher = group.defaultTeacher,
                location = group.defaultLocation,
                weeks = group.defaultWeeks.toSet(),
            )
        }

        if (!initialized) {
            initialized = true
            when {
                isEditingExistingSession -> {
                    val match = coursesWithSessions.firstNotNullOfOrNull { item ->
                        item.sessions.firstOrNull { it.id == sessionId }?.let { item.course to it }
                    }
                    if (match == null) {
                        mutableState.update {
                            it.copy(
                                isLoading = false,
                                isMissing = true,
                                missingIsCourse = false,
                                hasLessonTimes = sortedTimes.isNotEmpty(),
                                lessonTimes = sortedTimes,
                            )
                        }
                        return
                    }
                    val (parentCourse, targetSession) = match
                    editingSessionEntity = targetSession
                    val validPeriods = sortedTimes.map { it.period }.toSet()
                    val periods = targetSession.periods.filter { it in validPeriods }.toSet()
                    val weeks = targetSession.weeks
                        .filter { it in CourseRules.MIN_WEEK..CourseRules.MAX_WEEK }
                        .toSet()
                    val day = targetSession.dayOfWeek.coerceIn(CourseRules.DAY_OF_WEEK_RANGE)
                    val snapshot = DraftSnapshot(
                        selectedCourseId = parentCourse.id,
                        name = parentCourse.name,
                        teacher = targetSession.teacher,
                        location = targetSession.location,
                        dayOfWeek = day,
                        selectedPeriods = periods,
                        selectedWeeks = weeks,
                    )
                    originalSnapshot = snapshot
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            isNewSession = false,
                            showCourseSelectorMode = true,
                            isMissing = false,
                            hasLessonTimes = sortedTimes.isNotEmpty(),
                            lessonTimes = sortedTimes,
                            availableCourses = suggestions,
                            selectedCourseId = snapshot.selectedCourseId,
                            name = snapshot.name,
                            teacher = snapshot.teacher,
                            location = snapshot.location,
                            dayOfWeek = snapshot.dayOfWeek,
                            selectedPeriods = snapshot.selectedPeriods,
                            selectedWeeks = snapshot.selectedWeeks,
                            showExtendedWeeks = weeks.any { week -> week > CourseRules.DEFAULT_VISIBLE_WEEKS },
                            conflicts = computeConflicts(snapshot),
                            hasChanges = false,
                        )
                    }
                }

                isAddingToSpecificCourse -> {
                    val targetGroup = groups.firstOrNull { it.course.id == courseId }
                    if (targetGroup == null) {
                        mutableState.update {
                            it.copy(
                                isLoading = false,
                                isMissing = true,
                                missingIsCourse = true,
                                hasLessonTimes = sortedTimes.isNotEmpty(),
                                lessonTimes = sortedTimes,
                            )
                        }
                        return
                    }
                    val defaultWeeks = targetGroup.defaultWeeks
                        .filter { it in CourseRules.MIN_WEEK..CourseRules.MAX_WEEK }
                        .toSet()
                        .takeIf { it.isNotEmpty() }
                        ?: (1..DEFAULT_NEW_COURSE_END_WEEK).toSet()
                    val defaultPeriods = sortedTimes.take(2).map { it.period }.toSet()
                    val snapshot = DraftSnapshot(
                        selectedCourseId = targetGroup.course.id,
                        name = targetGroup.course.name,
                        teacher = targetGroup.defaultTeacher,
                        location = targetGroup.defaultLocation,
                        dayOfWeek = 1,
                        selectedPeriods = defaultPeriods,
                        selectedWeeks = defaultWeeks,
                    )
                    originalSnapshot = snapshot
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            isNewSession = true,
                            showCourseSelectorMode = true,
                            isMissing = false,
                            hasLessonTimes = sortedTimes.isNotEmpty(),
                            lessonTimes = sortedTimes,
                            availableCourses = suggestions,
                            selectedCourseId = snapshot.selectedCourseId,
                            name = snapshot.name,
                            teacher = snapshot.teacher,
                            location = snapshot.location,
                            dayOfWeek = snapshot.dayOfWeek,
                            selectedPeriods = snapshot.selectedPeriods,
                            selectedWeeks = snapshot.selectedWeeks,
                            showExtendedWeeks = defaultWeeks.any { week ->
                                week > CourseRules.DEFAULT_VISIBLE_WEEKS
                            },
                            conflicts = computeConflicts(snapshot),
                            hasChanges = false,
                        )
                    }
                }

                else -> {
                    val defaultWeeks = (1..DEFAULT_NEW_COURSE_END_WEEK).toSet()
                    val defaultPeriods = sortedTimes.take(2).map { it.period }.toSet()
                    val snapshot = DraftSnapshot(
                        selectedCourseId = null,
                        name = "",
                        teacher = "",
                        location = "",
                        dayOfWeek = 1,
                        selectedPeriods = defaultPeriods,
                        selectedWeeks = defaultWeeks,
                    )
                    originalSnapshot = snapshot
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            isNewSession = true,
                            showCourseSelectorMode = false,
                            isMissing = false,
                            hasLessonTimes = sortedTimes.isNotEmpty(),
                            lessonTimes = sortedTimes,
                            availableCourses = suggestions,
                            selectedCourseId = null,
                            name = "",
                            teacher = "",
                            location = "",
                            dayOfWeek = 1,
                            selectedPeriods = defaultPeriods,
                            selectedWeeks = defaultWeeks,
                            showExtendedWeeks = false,
                            conflicts = computeConflicts(snapshot),
                            hasChanges = false,
                        )
                    }
                }
            }
            return
        }

        // 已初始化后同步外部变更：如果正在编辑的时段或目标课程在别处被删掉，切到缺失态
        if (isEditingExistingSession && !mutableState.value.isDeleting) {
            val targetSession = coursesWithSessions.firstNotNullOfOrNull { item ->
                item.sessions.firstOrNull { it.id == sessionId }
            }
            if (targetSession == null) {
                mutableState.update {
                    it.copy(
                        isMissing = true,
                        missingIsCourse = false,
                        hasLessonTimes = sortedTimes.isNotEmpty(),
                        lessonTimes = sortedTimes,
                        availableCourses = suggestions,
                    )
                }
                return
            }
            editingSessionEntity = targetSession
        }

        mutableState.update { current ->
            val validPeriodSet = sortedTimes.map { it.period }.toSet()
            val cleanedPeriods = current.selectedPeriods.filter { it in validPeriodSet }.toSet()
            val refreshedCourseName = current.selectedCourseId?.let { id ->
                coursesWithSessions.firstOrNull { it.course.id == id }?.course?.name
            } ?: current.name
            val updated = current.copy(
                hasLessonTimes = sortedTimes.isNotEmpty(),
                lessonTimes = sortedTimes,
                availableCourses = suggestions,
                name = refreshedCourseName,
                selectedPeriods = cleanedPeriods,
            )
            updated.copy(
                conflicts = computeConflicts(updated.toSnapshot()),
                hasChanges = updated.toSnapshot() != originalSnapshot,
            )
        }
    }

    fun updateName(value: String) {
        updateDraft { current ->
            current.copy(
                selectedCourseId = null,
                name = value,
                validationError = when {
                    value.trim().length > CourseRules.COURSE_NAME_MAX_LENGTH ->
                        CourseValidationError.NameTooLong
                    current.validationError == CourseValidationError.EmptyName ||
                        current.validationError == CourseValidationError.NameTooLong -> null
                    else -> current.validationError
                },
            )
        }
    }

    fun updateTeacher(value: String) {
        updateDraft { current ->
            current.copy(
                teacher = value,
                validationError = when {
                    value.trim().length > CourseRules.COURSE_TEACHER_MAX_LENGTH ->
                        CourseValidationError.TeacherTooLong
                    current.validationError == CourseValidationError.TeacherTooLong -> null
                    else -> current.validationError
                },
            )
        }
    }

    fun updateLocation(value: String) {
        updateDraft { current ->
            current.copy(
                location = value,
                validationError = when {
                    value.trim().length > CourseRules.COURSE_LOCATION_MAX_LENGTH ->
                        CourseValidationError.LocationTooLong
                    current.validationError == CourseValidationError.LocationTooLong -> null
                    else -> current.validationError
                },
            )
        }
    }

    fun selectCourse(suggestion: CourseNameSuggestion) {
        updateDraft { current ->
            val nextTeacher = if (current.showCourseSelectorMode) {
                current.teacher
            } else {
                current.teacher.ifBlank { suggestion.teacher }
            }
            val nextLocation = if (current.showCourseSelectorMode) {
                current.location
            } else {
                current.location.ifBlank { suggestion.location }
            }
            val nextWeeks = if (!current.showCourseSelectorMode && suggestion.weeks.isNotEmpty()) {
                suggestion.weeks
            } else {
                current.selectedWeeks
            }
            current.copy(
                selectedCourseId = suggestion.courseId,
                name = suggestion.name,
                teacher = nextTeacher,
                location = nextLocation,
                selectedWeeks = nextWeeks,
                showExtendedWeeks = current.showExtendedWeeks ||
                    nextWeeks.any { it > CourseRules.DEFAULT_VISIBLE_WEEKS },
                validationError = null,
            )
        }
    }

    fun selectDayOfWeek(day: Int) {
        if (day !in CourseRules.DAY_OF_WEEK_RANGE) return
        updateDraft { it.copy(dayOfWeek = day) }
    }

    fun updateSelectedPeriods(periods: Set<Int>) {
        updateDraft { current ->
            current.copy(
                selectedPeriods = periods,
                validationError = if (current.validationError == CourseValidationError.EmptyPeriods ||
                    current.validationError == CourseValidationError.InvalidPeriod
                ) {
                    null
                } else {
                    current.validationError
                },
            )
        }
    }

    fun updateSelectedWeeks(weeks: Set<Int>) {
        updateDraft { current ->
            current.copy(
                selectedWeeks = weeks,
                validationError = if (current.validationError == CourseValidationError.EmptyWeeks) {
                    null
                } else {
                    current.validationError
                },
            )
        }
    }

    fun selectAllWeeks() {
        val maxWeek = visibleMaxWeek(mutableState.value.showExtendedWeeks)
        updateSelectedWeeks((CourseRules.MIN_WEEK..maxWeek).toSet())
    }

    fun selectOddWeeks() {
        val maxWeek = visibleMaxWeek(mutableState.value.showExtendedWeeks)
        updateSelectedWeeks((CourseRules.MIN_WEEK..maxWeek).filter { it % 2 != 0 }.toSet())
    }

    fun selectEvenWeeks() {
        val maxWeek = visibleMaxWeek(mutableState.value.showExtendedWeeks)
        updateSelectedWeeks((CourseRules.MIN_WEEK..maxWeek).filter { it % 2 == 0 }.toSet())
    }

    fun clearWeeks() {
        updateSelectedWeeks(emptySet())
    }

    fun toggleExtendedWeeks() {
        mutableState.update { current ->
            current.copy(showExtendedWeeks = !current.showExtendedWeeks)
        }
    }

    fun save(addAnother: Boolean = false) {
        val state = mutableState.value
        if (state.isLoading || state.isMissing || !state.hasLessonTimes || state.isSaving || state.isDeleting) {
            return
        }
        val availablePeriods = state.lessonTimes.map { it.period }.toSet()
        val error = CourseRules.validateCourseDraft(
            name = state.name,
            teacher = state.teacher,
            location = state.location,
            periods = state.selectedPeriods,
            weeks = state.selectedWeeks,
            availablePeriods = availablePeriods,
        )
        if (error != null) {
            mutableState.update { it.copy(validationError = error) }
            return
        }
        if (!state.isNewSession && !state.hasChanges) {
            return
        }

        val sortedPeriods = state.selectedPeriods.sorted()
        val sortedWeeks = state.selectedWeeks
            .filter { it in CourseRules.MIN_WEEK..CourseRules.MAX_WEEK }
            .sorted()

        viewModelScope.launch {
            mutableState.update { it.copy(isSaving = true, validationError = null) }
            try {
                val resolvedCourseId: Long
                val targetCourseId = state.selectedCourseId
                if (state.isNewSession) {
                    val sessionDraft = CourseSessionEntity(
                        id = 0L,
                        courseId = targetCourseId ?: 0L,
                        teacher = state.teacher.trim(),
                        location = state.location.trim(),
                        dayOfWeek = state.dayOfWeek,
                        periods = sortedPeriods,
                        weeks = sortedWeeks,
                    )
                    resolvedCourseId = if (targetCourseId != null) {
                        repository.insertCourseSessionWithReminders(timetableId, sessionDraft)
                        targetCourseId
                    } else {
                        repository.upsertCourseAndInsertSessionWithReminders(
                            timetableId = timetableId,
                            courseName = state.name.trim(),
                            session = sessionDraft,
                        ).first
                    }
                } else {
                    val existingCourseId = targetCourseId ?: editingSessionEntity?.courseId ?: 0L
                    resolvedCourseId = existingCourseId
                    repository.updateCourseSessionWithReminders(
                        timetableId = timetableId,
                        session = CourseSessionEntity(
                            id = sessionId,
                            courseId = existingCourseId,
                            teacher = state.teacher.trim(),
                            location = state.location.trim(),
                            dayOfWeek = state.dayOfWeek,
                            periods = sortedPeriods,
                            weeks = sortedWeeks,
                        ),
                    )
                }

                if (addAnother && state.isNewSession) {
                    val nextSnapshot = DraftSnapshot(
                        selectedCourseId = resolvedCourseId,
                        name = state.name.trim(),
                        teacher = state.teacher.trim(),
                        location = state.location.trim(),
                        dayOfWeek = state.dayOfWeek,
                        selectedPeriods = emptySet(),
                        selectedWeeks = sortedWeeks.toSet(),
                    )
                    originalSnapshot = nextSnapshot
                    mutableState.update { current ->
                        val updated = current.copy(
                            isSaving = false,
                            showCourseSelectorMode = true,
                            selectedCourseId = nextSnapshot.selectedCourseId,
                            name = nextSnapshot.name,
                            teacher = nextSnapshot.teacher,
                            location = nextSnapshot.location,
                            selectedPeriods = emptySet(),
                            selectedWeeks = nextSnapshot.selectedWeeks,
                            hasChanges = false,
                            message = CourseEditMessage(
                                token = ++nextMessageToken,
                                type = CourseEditMessageType.SavedAddNext,
                            ),
                        )
                        updated.copy(conflicts = computeConflicts(updated.toSnapshot()))
                    }
                } else {
                    mutableState.update { it.copy(isSaving = false, finish = true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        isSaving = false,
                        message = CourseEditMessage(
                            token = ++nextMessageToken,
                            type = CourseEditMessageType.SaveFailed,
                        ),
                    )
                }
            }
        }
    }

    fun requestDelete() {
        val state = mutableState.value
        if (state.isNewSession || state.isSaving || state.isDeleting) return
        mutableState.update { it.copy(showDeleteConfirm = true) }
    }

    fun dismissDelete() {
        mutableState.update { it.copy(showDeleteConfirm = false) }
    }

    fun confirmDelete() {
        val state = mutableState.value
        val entity = editingSessionEntity ?: return
        if (state.isNewSession || state.isSaving || state.isDeleting) return
        viewModelScope.launch {
            mutableState.update { it.copy(isDeleting = true) }
            try {
                repository.deleteCourseSessionWithReminders(timetableId, entity)
                mutableState.update {
                    it.copy(
                        isDeleting = false,
                        showDeleteConfirm = false,
                        finish = true,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(
                        isDeleting = false,
                        showDeleteConfirm = false,
                        message = CourseEditMessage(
                            token = ++nextMessageToken,
                            type = CourseEditMessageType.DeleteFailed,
                        ),
                    )
                }
            }
        }
    }

    fun requestBack() {
        val state = mutableState.value
        if (state.isSaving || state.isDeleting) return
        if (state.hasChanges && !state.isMissing && state.hasLessonTimes) {
            mutableState.update { it.copy(showDiscardConfirm = true) }
        } else {
            mutableState.update { it.copy(finish = true) }
        }
    }

    fun dismissDiscard() {
        mutableState.update { it.copy(showDiscardConfirm = false) }
    }

    fun confirmDiscard() {
        mutableState.update { it.copy(showDiscardConfirm = false, finish = true) }
    }

    fun onMessageShown(token: Long) {
        mutableState.update { current ->
            if (current.message?.token == token) current.copy(message = null) else current
        }
    }

    private fun updateDraft(transform: (CourseEditUiState) -> CourseEditUiState) {
        mutableState.update { current ->
            val updated = transform(current)
            val snapshot = updated.toSnapshot()
            updated.copy(
                conflicts = computeConflicts(snapshot),
                hasChanges = snapshot != originalSnapshot,
            )
        }
    }

    private fun computeConflicts(snapshot: DraftSnapshot): List<CourseConflict> =
        CourseRules.findConflicts(
            editingSessionId = sessionId.takeIf { it > 0L },
            dayOfWeek = snapshot.dayOfWeek,
            periods = snapshot.selectedPeriods,
            weeks = snapshot.selectedWeeks,
            existingCourses = latestCoursesWithSessions,
        )

    private fun CourseEditUiState.toSnapshot(): DraftSnapshot = DraftSnapshot(
        selectedCourseId = selectedCourseId,
        name = name,
        teacher = teacher,
        location = location,
        dayOfWeek = dayOfWeek,
        selectedPeriods = selectedPeriods,
        selectedWeeks = selectedWeeks,
    )

    private fun visibleMaxWeek(showExtended: Boolean): Int =
        if (showExtended) CourseRules.MAX_WEEK else CourseRules.DEFAULT_VISIBLE_WEEKS

    companion object {
        private const val DEFAULT_NEW_COURSE_END_WEEK = 16
    }
}

class CourseEditViewModelFactory(
    private val repository: TimetableRepository,
    private val timetableId: Long,
    private val courseId: Long = -1L,
    private val sessionId: Long = -1L,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CourseEditViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CourseEditViewModel(
                repository = repository,
                timetableId = timetableId,
                courseId = courseId,
                sessionId = sessionId,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
