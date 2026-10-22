package com.star.schedule.feature.timetable.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.core.database.LessonTimeEntity
import com.star.schedule.feature.schedule.domain.ScheduleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class CourseSettingsUiState(
    val isLoading: Boolean = true,
    /**
     * 课程要排在节次上：没有节次时间时先引导用户去设置，而不是给一张空页面。
     */
    val hasLessonTimes: Boolean = false,
)

class CourseSettingsViewModel(
    repository: ScheduleRepository,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val lessonTimes: Flow<List<LessonTimeEntity>> =
        repository.observeCurrentTimetableId().flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.observeLessonTimes(id)
        }

    val uiState: StateFlow<CourseSettingsUiState> = lessonTimes
        .map { times ->
            CourseSettingsUiState(
                isLoading = false,
                hasLessonTimes = times.isNotEmpty(),
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CourseSettingsUiState(),
        )
}

class CourseSettingsViewModelFactory(
    private val repository: ScheduleRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CourseSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CourseSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
