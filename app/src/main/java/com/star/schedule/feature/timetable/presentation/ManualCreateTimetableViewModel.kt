package com.star.schedule.feature.timetable.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.core.database.TimetableEntity
import com.star.schedule.feature.holiday.domain.HolidayStore
import com.star.schedule.feature.timetable.domain.SemesterStartSuggestion
import com.star.schedule.feature.timetable.domain.TimetableRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ManualCreateUiState(
    val name: String = "",
    /** 用户手改过一次名称后，名称不再跟随日期联动。 */
    val nameEdited: Boolean = false,
    val startDate: LocalDate,
    /** 推荐开学日，界面上给它一个「推荐」标记。 */
    val suggestedDate: LocalDate,
    val creating: Boolean = false,
    /** 创建成功后给页面一个退出信号。 */
    val createdTimetableId: Long? = null,
)

class ManualCreateTimetableViewModel(
    private val timetableRepository: TimetableRepository,
    private val holidayStore: HolidayStore,
    private val selectTimetable: suspend (Long) -> Unit,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val semesterName: (LocalDate) -> String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ManualCreateUiState(
            name = "",
            startDate = today(),
            suggestedDate = today(),
        ),
    )
    val uiState: StateFlow<ManualCreateUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val year = today().year
            val holidays = (year..year + 1)
                .associateWith { holidayStore.holidaysOfYear(it) }
            val suggested = SemesterStartSuggestion.suggest(today()) { holidays[it].orEmpty() }
            _uiState.update {
                it.copy(
                    name = semesterName(suggested),
                    startDate = suggested,
                    suggestedDate = suggested,
                )
            }
        }
    }

    fun onNameChange(name: String) {
        _uiState.update {
            it.copy(name = name, nameEdited = it.nameEdited || name.isNotBlank())
        }
    }

    fun onStartDateChange(date: LocalDate) {
        _uiState.update {
            it.copy(
                startDate = date,
                // 名称没动过就一直跟着日期联动，比如切到秋季日期就改成秋季名。
                name = if (it.nameEdited) it.name else semesterName(date),
            )
        }
    }

    fun create() {
        val state = _uiState.value
        if (state.creating || state.name.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(creating = true) }
            // 和 WakeUp 导入同一个节奏：先让表单行收进按钮、按钮飞到屏幕中心，
            // 编排播完才真正落库，所以结果很快也不会把退出动画掐断。
            delay(SUBMIT_ANIMATION_MILLIS)
            val timetableId = timetableRepository.insertTimetableWithReminders(
                TimetableEntity(
                    name = state.name.trim(),
                    showWeekend = true,
                    startDate = state.startDate.toString(),
                ),
            )
            selectTimetable(timetableId)
            _uiState.update { it.copy(createdTimetableId = timetableId) }
        }
    }
}

/** 提交编排的总时长，与界面上的 EXIT_MILLIS 对齐：编排播完才真正落库。 */
private const val SUBMIT_ANIMATION_MILLIS = 900L

class ManualCreateTimetableViewModelFactory(
    private val timetableRepository: TimetableRepository,
    private val holidayStore: HolidayStore,
    private val selectTimetable: suspend (Long) -> Unit,
    private val semesterName: (LocalDate) -> String,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ManualCreateTimetableViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ManualCreateTimetableViewModel(
                timetableRepository = timetableRepository,
                holidayStore = holidayStore,
                selectTimetable = selectTimetable,
                semesterName = semesterName,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
