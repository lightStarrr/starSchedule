package com.star.schedule.feature.timetable.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.LessonTimeEntity
import com.star.schedule.core.database.TimetableEntity
import com.star.schedule.feature.schedule.domain.ScheduleRepository
import com.star.schedule.feature.schedule.domain.buildCourseBlocksByWeek
import com.star.schedule.feature.settings.domain.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

data class RowHeightUiState(
    val isLoading: Boolean = true,
    val timetableId: Long? = null,
    val rowHeight: Int = RowHeightViewModel.DEFAULT_ROW_HEIGHT,
    val savedRowHeight: Int = RowHeightViewModel.DEFAULT_ROW_HEIGHT,
    val week: Int = 1,
    val weekStartDate: LocalDate = LocalDate.now().with(DayOfWeek.MONDAY),
    val lessonTimes: List<LessonTimeEntity> = emptyList(),
    val courses: List<CourseEntity> = emptyList(),
    val showWeekend: Boolean = false,
) {
    val canApply: Boolean
        get() = timetableId != null && rowHeight != savedRowHeight
}

/**
 * 行高页面：整页预览 + 底部滑杆。
 *
 * 预览用「课最多的一周」，行高变化在课程密集的列上最直观。
 * 调整只改本地草稿，点「应用」才写回数据库，返回即丢弃。
 */
class RowHeightViewModel(
    scheduleRepository: ScheduleRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val draftRowHeight = MutableStateFlow<Int?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentTimetableId: Flow<Long?> = scheduleRepository.observeCurrentTimetableId()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val timetable: Flow<TimetableEntity?> = currentTimetableId.flatMapLatest { id ->
        if (id == null) flowOf(null) else scheduleRepository.observeTimetable(id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val courses: Flow<List<CourseEntity>> = currentTimetableId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else scheduleRepository.observeCourses(id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val lessonTimes: Flow<List<LessonTimeEntity>> = currentTimetableId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else scheduleRepository.observeLessonTimes(id)
    }

    val uiState: StateFlow<RowHeightUiState> = combine(
        timetable,
        courses,
        lessonTimes,
        draftRowHeight,
    ) { timetable, courses, lessonTimes, draft ->
        val saved = timetable?.rowHeight ?: DEFAULT_ROW_HEIGHT
        val busiestWeek = buildCourseBlocksByWeek(courses)
            .maxByOrNull { (_, blocks) -> blocks.size }
            ?.key
            ?: 1
        val semesterStart = timetable?.startDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now().with(DayOfWeek.MONDAY)
        RowHeightUiState(
            isLoading = timetable == null,
            timetableId = timetable?.id,
            rowHeight = draft ?: saved,
            savedRowHeight = saved,
            week = busiestWeek,
            weekStartDate = semesterStart.plusWeeks((busiestWeek - 1).toLong()),
            lessonTimes = lessonTimes.sortedBy { it.period },
            courses = courses,
            showWeekend = courses.any { it.dayOfWeek > 5 },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RowHeightUiState(),
    )

    fun setRowHeight(rowHeight: Int) {
        draftRowHeight.value = rowHeight
            .coerceIn(ROW_HEIGHT_MIN, ROW_HEIGHT_MAX)
            .let { snapped -> snapped / ROW_HEIGHT_STEP * ROW_HEIGHT_STEP }
            .coerceIn(ROW_HEIGHT_MIN, ROW_HEIGHT_MAX)
    }

    fun apply() {
        val state = uiState.value
        val timetableId = state.timetableId ?: return
        viewModelScope.launch {
            settingsRepository.setRowHeight(timetableId, state.rowHeight)
        }
    }

    companion object {
        const val ROW_HEIGHT_MIN = 40
        const val ROW_HEIGHT_MAX = 240
        const val ROW_HEIGHT_STEP = 10
        const val DEFAULT_ROW_HEIGHT = 60
    }
}

class RowHeightViewModelFactory(
    private val scheduleRepository: ScheduleRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RowHeightViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RowHeightViewModel(scheduleRepository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
