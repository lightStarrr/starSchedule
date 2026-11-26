package com.star.schedule.feature.timetable.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.core.database.entity.LessonTimeEntity
import com.star.schedule.core.database.entity.TimetableEntity
import com.star.schedule.core.model.ScheduledCourse
import com.star.schedule.feature.schedule.domain.ScheduleRepository
import com.star.schedule.feature.settings.domain.SettingsRepository
import com.star.schedule.feature.timetable.domain.RowHeightPreviewSample
import com.star.schedule.feature.timetable.domain.timetablePreviewGrid
import com.star.schedule.feature.wallpaper.domain.WallpaperRepository
import com.star.schedule.feature.wallpaper.domain.WallpaperState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
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
    val courses: List<ScheduledCourse> = emptyList(),
    val showWeekend: Boolean = false,
    val wallpaperState: WallpaperState = WallpaperState.None,
) {
    val canApply: Boolean
        get() = timetableId != null && rowHeight != savedRowHeight
}

/**
 * 行高页面：整页预览 + 底部滑杆。
 *
 * 预览用「课最多的一周」，行高变化在课程密集的列上最直观。
 * 调整只改本地草稿，点「应用」才写回数据库，返回即丢弃。
 * 作用对象是打开页面时传入的 [timetableId]，不跟随「已应用课表」变化。
 */
class RowHeightViewModel(
    scheduleRepository: ScheduleRepository,
    private val settingsRepository: SettingsRepository,
    wallpaperRepository: WallpaperRepository,
    timetableId: Long,
) : ViewModel() {
    private val draftRowHeight = MutableStateFlow<Int?>(null)

    /** 没排课时的示例课表，整个页面生命周期里只随机一次。 */
    private val previewTimetable = RowHeightPreviewSample.create()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val currentTimetableId: Flow<Long?> = flowOf(timetableId)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val timetable: Flow<TimetableEntity?> = currentTimetableId.flatMapLatest { id ->
        if (id == null) flowOf(null) else scheduleRepository.observeTimetable(id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val courses: Flow<List<ScheduledCourse>> = currentTimetableId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else scheduleRepository.observeCourses(id)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val lessonTimes: Flow<List<LessonTimeEntity>> = currentTimetableId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else scheduleRepository.observeLessonTimes(id)
    }

    private val wallpaperState: Flow<WallpaperState> = wallpaperRepository.observe(timetableId)
        .onStart { emit(WallpaperState.None) }

    val uiState: StateFlow<RowHeightUiState> = combine(
        timetable,
        courses,
        lessonTimes,
        draftRowHeight,
        wallpaperState,
    ) { timetable, courses, lessonTimes, draft, wallpaper ->
        // 示例在这里生成一次、存在属性上：拖动滑杆会让 combine 重新跑，不能在函数里重新随机。
        val grid = timetablePreviewGrid(
            timetable = timetable,
            courses = courses,
            lessonTimes = lessonTimes,
            sample = previewTimetable,
            fallbackRowHeight = DEFAULT_ROW_HEIGHT,
        )
        RowHeightUiState(
            isLoading = timetable == null,
            timetableId = timetable?.id,
            rowHeight = draft ?: grid.rowHeight,
            savedRowHeight = grid.rowHeight,
            week = grid.week,
            weekStartDate = grid.weekStartDate,
            lessonTimes = grid.lessonTimes,
            courses = grid.courses,
            showWeekend = grid.showWeekend,
            wallpaperState = wallpaper,
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
    private val wallpaperRepository: WallpaperRepository,
    private val timetableId: Long,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RowHeightViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RowHeightViewModel(
                scheduleRepository = scheduleRepository,
                settingsRepository = settingsRepository,
                wallpaperRepository = wallpaperRepository,
                timetableId = timetableId,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
