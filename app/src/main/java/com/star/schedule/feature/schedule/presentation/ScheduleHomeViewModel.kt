package com.star.schedule.feature.schedule.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.LessonTimeEntity
import com.star.schedule.core.database.TimetableEntity
import com.star.schedule.feature.schedule.domain.ScheduleRepository
import com.star.schedule.feature.wallpaper.domain.WallpaperRepository
import com.star.schedule.feature.wallpaper.domain.WallpaperState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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
import kotlinx.coroutines.flow.update

data class ScheduleHomeUiState(
    val isLoading: Boolean = true,
    val timetableId: Long? = null,
    val hasTimetable: Boolean = false,
    val semesterStartDate: LocalDate = LocalDate.now().with(DayOfWeek.MONDAY),
    val currentWeek: Int = 1,
    val weekStartDate: LocalDate = LocalDate.now().with(DayOfWeek.MONDAY),
    val lessonTimes: List<LessonTimeEntity> = emptyList(),
    val courses: List<CourseEntity> = emptyList(),
    val showWeekend: Boolean = false,
    val rowHeightDp: Int = 60,
    val wallpaperState: WallpaperState = WallpaperState.None,
)

private data class HomeTimetableBundle(
    val isLoading: Boolean,
    val timetableId: Long?,
    val timetable: TimetableEntity?,
    val courses: List<CourseEntity>,
    val lessonTimes: List<LessonTimeEntity>,
    val wallpaperState: WallpaperState,
)

private data class WeekSelectionOverride(
    val timetableId: Long?,
    val semesterStartDate: LocalDate,
    val week: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleHomeViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val todayProvider: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val weekOverride = MutableStateFlow<WeekSelectionOverride?>(null)

    private val timetableBundle: Flow<HomeTimetableBundle> =
        scheduleRepository.observeCurrentTimetableId().flatMapLatest { timetableId ->
            if (timetableId == null) {
                flowOf(
                    HomeTimetableBundle(
                        isLoading = false,
                        timetableId = null,
                        timetable = null,
                        courses = emptyList(),
                        lessonTimes = emptyList(),
                        wallpaperState = WallpaperState.None,
                    ),
                )
            } else {
                combine(
                    scheduleRepository.observeTimetable(timetableId),
                    scheduleRepository.observeCourses(timetableId),
                    scheduleRepository.observeLessonTimes(timetableId),
                    wallpaperRepository.observe(timetableId).onStart { emit(WallpaperState.None) },
                ) { timetable, courses, lessonTimes, wallpaperState ->
                    HomeTimetableBundle(
                        isLoading = false,
                        timetableId = timetableId,
                        timetable = timetable,
                        courses = courses,
                        lessonTimes = lessonTimes,
                        wallpaperState = wallpaperState,
                    )
                }.onStart {
                    emit(
                        HomeTimetableBundle(
                            isLoading = true,
                            timetableId = timetableId,
                            timetable = null,
                            courses = emptyList(),
                            lessonTimes = emptyList(),
                            wallpaperState = WallpaperState.None,
                        ),
                    )
                }
            }
        }

    val uiState: StateFlow<ScheduleHomeUiState> = combine(
        timetableBundle,
        weekOverride,
    ) { bundle, override ->
        val today = todayProvider()
        val semesterStart = resolveSemesterStart(bundle.timetable?.startDate, today)
        val defaultWeek = resolveCurrentWeek(semesterStart, today)
        val activeWeek = if (
            override != null &&
            override.timetableId == bundle.timetableId &&
            override.semesterStartDate == semesterStart
        ) {
            override.week
        } else {
            defaultWeek
        }
        val weekStartDate = semesterStart.plusWeeks((activeWeek - 1).toLong())
        ScheduleHomeUiState(
            isLoading = bundle.isLoading,
            timetableId = bundle.timetableId,
            hasTimetable = bundle.timetable != null,
            semesterStartDate = semesterStart,
            currentWeek = activeWeek,
            weekStartDate = weekStartDate,
            lessonTimes = bundle.lessonTimes,
            courses = bundle.courses,
            // 周末列不再由偏好决定：课表里没有周末的课就不占这两列。
            showWeekend = bundle.courses.any { it.dayOfWeek > 5 },
            rowHeightDp = bundle.timetable?.rowHeight ?: 60,
            wallpaperState = bundle.wallpaperState,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScheduleHomeUiState(
            isLoading = true,
            semesterStartDate = todayProvider().with(DayOfWeek.MONDAY),
            weekStartDate = todayProvider().with(DayOfWeek.MONDAY),
        ),
    )

    fun previousWeek() = shiftWeek(-1)

    fun nextWeek() = shiftWeek(1)

    private fun shiftWeek(delta: Int) {
        val current = uiState.value
        weekOverride.update { existing ->
            val baseWeek = if (
                existing != null &&
                existing.timetableId == current.timetableId &&
                existing.semesterStartDate == current.semesterStartDate
            ) {
                existing.week
            } else {
                current.currentWeek
            }
            WeekSelectionOverride(
                timetableId = current.timetableId,
                semesterStartDate = current.semesterStartDate,
                week = (baseWeek + delta).coerceAtLeast(1),
            )
        }
    }

    fun selectWeek(week: Int) {
        val current = uiState.value
        weekOverride.update {
            WeekSelectionOverride(
                timetableId = current.timetableId,
                semesterStartDate = current.semesterStartDate,
                week = week.coerceAtLeast(1),
            )
        }
    }

    companion object {
        internal fun resolveSemesterStart(rawStartDate: String?, today: LocalDate): LocalDate =
            runCatching { rawStartDate?.let(LocalDate::parse) }
                .getOrNull()
                ?: today.with(DayOfWeek.MONDAY)

        internal fun resolveCurrentWeek(semesterStart: LocalDate, today: LocalDate): Int =
            (ChronoUnit.DAYS.between(semesterStart, today).toInt() / 7 + 1).coerceAtLeast(1)
    }
}

class ScheduleHomeViewModelFactory(
    private val scheduleRepository: ScheduleRepository,
    private val wallpaperRepository: WallpaperRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScheduleHomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ScheduleHomeViewModel(scheduleRepository, wallpaperRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
