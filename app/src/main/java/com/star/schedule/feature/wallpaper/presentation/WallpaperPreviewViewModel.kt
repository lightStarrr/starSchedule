package com.star.schedule.feature.wallpaper.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.core.database.entity.LessonTimeEntity
import com.star.schedule.core.model.ScheduledCourse
import com.star.schedule.feature.schedule.domain.ScheduleRepository
import com.star.schedule.feature.timetable.domain.RowHeightPreviewSample
import com.star.schedule.feature.timetable.domain.timetablePreviewGrid
import com.star.schedule.feature.wallpaper.domain.StagedWallpaper
import com.star.schedule.feature.wallpaper.domain.WallpaperBlurSpec
import com.star.schedule.feature.wallpaper.domain.WallpaperRepository
import com.star.schedule.feature.wallpaper.domain.WallpaperDesign
import com.star.schedule.feature.wallpaper.domain.RecentWallpaper
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface WallpaperPreviewUiState {
    data object Loading : WallpaperPreviewUiState

    data object LoadFailed : WallpaperPreviewUiState

    data class Ready(
        val stagedPath: String?,
        val seedArgb: Int?,
        val design: WallpaperDesign?,
        val scrimEnabled: Boolean,
        val blurEnabled: Boolean,
        // 单位 dp；开关没打开时保留上次滑到的值，写库时按 0 处理。
        val blurRadius: Float,
        val isApplying: Boolean,
        val applyFailed: Boolean,
        val applied: Boolean,
        val week: Int,
        val weekStartDate: LocalDate,
        val lessonTimes: List<LessonTimeEntity>,
        val courses: List<ScheduledCourse>,
        val showWeekend: Boolean,
        val rowHeight: Int,
    ) : WallpaperPreviewUiState
}

/**
 * 壁纸预览页：把选中的照片暂存下来，叠上真实课表和模糊特效给用户看效果。
 *
 * 作用于构造时传入的 [timetableId]；点「应用」才把照片与模糊设置写进课表，
 * 返回/页面销毁时暂存文件会被 [WallpaperRepository.discardStaged] 清掉。
 */
class WallpaperPreviewViewModel(
    private val timetableId: Long,
    imageUriString: String?,
    scheduleRepository: ScheduleRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val initialDesign: WallpaperDesign? = null,
) : ViewModel() {
    private sealed interface StageResult {
        data object Loading : StageResult
        data object Failed : StageResult
        data class Ok(val staged: StagedWallpaper?) : StageResult
    }

    private data class EditState(
        val scrimEnabled: Boolean = true,
        val blurEnabled: Boolean = false,
        val blurRadius: Float = DEFAULT_BLUR_RADIUS,
        val isApplying: Boolean = false,
        val applyFailed: Boolean = false,
        val applied: Boolean = false,
        val solidArgb: Int? = null,
    )

    private val staged = MutableStateFlow<StageResult>(StageResult.Loading)
    private val edit = MutableStateFlow(
        EditState(scrimEnabled = initialDesign?.scrimEnabled ?: true),
    )

    // onCleared 里要同步删暂存文件，不能只靠状态流取路径（作用域已取消）。
    private var stagedPath: String? = null

    /** 没排课时的示例课表，整个页面生命周期里只随机一次。 */
    private val previewTimetable = RowHeightPreviewSample.create()

    private val timetable = scheduleRepository.observeTimetable(timetableId)
    private val courses = scheduleRepository.observeCourses(timetableId)
    private val lessonTimes = scheduleRepository.observeLessonTimes(timetableId)

    init {
        viewModelScope.launch {
            if (imageUriString == null) {
                staged.value = if (initialDesign == null || initialDesign.isValid()) {
                    StageResult.Ok(null)
                } else {
                    StageResult.Failed
                }
                return@launch
            }
            val result = wallpaperRepository.stagePreview(imageUriString)
            result.onSuccess { stagedPath = it.path }
            staged.value = result.fold(
                onSuccess = { StageResult.Ok(it) },
                onFailure = { StageResult.Failed },
            )
        }
    }

    val uiState: StateFlow<WallpaperPreviewUiState> = combine(
        staged,
        edit,
        timetable,
        courses,
        lessonTimes,
    ) { staged, edit, timetable, courses, lessonTimes ->
        when (staged) {
            StageResult.Loading -> WallpaperPreviewUiState.Loading
            StageResult.Failed -> WallpaperPreviewUiState.LoadFailed
            is StageResult.Ok -> {
                val grid = timetablePreviewGrid(
                    timetable = timetable,
                    courses = courses,
                    lessonTimes = lessonTimes,
                    sample = previewTimetable,
                    fallbackRowHeight = DEFAULT_ROW_HEIGHT,
                )
                WallpaperPreviewUiState.Ready(
                    stagedPath = staged.staged?.path,
                    seedArgb = edit.solidArgb ?: initialDesign?.seedArgb ?: staged.staged?.seedArgb,
                    design = if (initialDesign?.solidArgb != null && edit.solidArgb != null) {
                        initialDesign.copy(solidArgb = edit.solidArgb)
                    } else {
                        initialDesign
                    },
                    scrimEnabled = edit.scrimEnabled,
                    blurEnabled = edit.blurEnabled,
                    blurRadius = edit.blurRadius,
                    isApplying = edit.isApplying,
                    applyFailed = edit.applyFailed,
                    applied = edit.applied,
                    week = grid.week,
                    weekStartDate = grid.weekStartDate,
                    lessonTimes = grid.lessonTimes,
                    courses = grid.courses,
                    showWeekend = grid.showWeekend,
                    rowHeight = grid.rowHeight,
                )
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        WallpaperPreviewUiState.Loading,
    )

    fun setScrimEnabled(enabled: Boolean) {
        edit.update { it.copy(scrimEnabled = enabled) }
    }

    fun setBlurEnabled(enabled: Boolean) {
        edit.update { it.copy(blurEnabled = enabled) }
    }

    fun selectSolidColor(colorArgb: Int) {
        if (initialDesign?.solidArgb != null && colorArgb ushr 24 == 255) {
            edit.update { it.copy(solidArgb = colorArgb) }
        }
    }

    fun setBlurRadius(radius: Float) {
        edit.update { it.copy(blurRadius = radius.coerceIn(BLUR_RADIUS_MIN, BLUR_RADIUS_MAX)) }
    }

    /**
     * 应用暂存照片为课表壁纸；[blur] 非空时先按窗口像素烘焙模糊再落库，
     * 由 UI 层用窗口尺寸与 density 组装（见 WallpaperPreviewScreen）。
     */
    fun apply(blur: WallpaperBlurSpec?) {
        val state = uiState.value as? WallpaperPreviewUiState.Ready ?: return
        if (state.isApplying) return
        edit.update { it.copy(isApplying = true, applyFailed = false) }
        viewModelScope.launch {
            val result = when {
                state.stagedPath != null -> wallpaperRepository.applyStaged(
                    timetableId = timetableId,
                    stagedPath = state.stagedPath,
                    blur = blur,
                    scrimEnabled = state.scrimEnabled,
                )
                state.design != null -> wallpaperRepository.applyDesign(
                    timetableId,
                    state.design.copy(
                        blurRadius = if (state.blurEnabled) state.blurRadius else 0f,
                        scrimEnabled = state.scrimEnabled,
                    ),
                )
                else -> wallpaperRepository.applyRecent(timetableId, RecentWallpaper.Default)
            }
            result
                .onSuccess {
                    state.stagedPath?.let(wallpaperRepository::discardStaged)
                    stagedPath = null
                    edit.update { it.copy(isApplying = false, applied = true) }
                }
                .onFailure {
                    edit.update { it.copy(isApplying = false, applyFailed = true) }
                }
        }
    }

    override fun onCleared() {
        stagedPath?.let(wallpaperRepository::discardStaged)
    }

    companion object {
        const val BLUR_RADIUS_MIN = 1f
        const val BLUR_RADIUS_MAX = 25f
        const val DEFAULT_BLUR_RADIUS = 10f
        private const val DEFAULT_ROW_HEIGHT = 60
    }
}

class WallpaperPreviewViewModelFactory(
    private val timetableId: Long,
    private val imageUriString: String?,
    private val scheduleRepository: ScheduleRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val initialDesign: WallpaperDesign? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WallpaperPreviewViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WallpaperPreviewViewModel(
                timetableId = timetableId,
                imageUriString = imageUriString,
                scheduleRepository = scheduleRepository,
                wallpaperRepository = wallpaperRepository,
                initialDesign = initialDesign,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
