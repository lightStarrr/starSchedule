package com.star.schedule.feature.timetable.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.core.common.Constants
import com.star.schedule.feature.settings.domain.SettingsRepository
import com.star.schedule.feature.settings.domain.TimetablePreview
import com.star.schedule.feature.wallpaper.domain.RecentWallpaper
import com.star.schedule.feature.wallpaper.domain.WallpaperRepository
import com.star.schedule.feature.wallpaper.domain.WallpaperState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

data class TimetableSettingsUiState(
    val isLoading: Boolean = true,
    val previews: List<TimetablePreview> = emptyList(),
    val currentId: Long? = null,
    /** 每张预览课表各自的壁纸状态，键是课表 id。 */
    val wallpapers: Map<Long, WallpaperState> = emptyMap(),
    /** 全局共享的最近壁纸（最新在前），键缺失时只有「默认」。 */
    val recents: List<RecentWallpaper> = emptyList(),
    /** 点了还没写完的选用，键是课表 id；UI 按它乐观显示选中项。 */
    val pendingWallpapers: Map<Long, RecentWallpaper> = emptyMap(),
)

class TimetableSettingsViewModel(
    private val repository: SettingsRepository,
    private val wallpaperRepository: WallpaperRepository,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val wallpapers: Flow<Map<Long, WallpaperState>> =
        repository.observeTimetablePreviews().flatMapLatest { previews ->
            if (previews.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    previews.map { preview ->
                        wallpaperRepository.observe(preview.id).map { preview.id to it }
                    },
                ) { states -> states.toMap() }
            }
        }

    /** 选用最近壁纸失败的一次性事件（UI 弹提示）。 */
    private val _selectRecentFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val selectRecentFailed: SharedFlow<Unit> = _selectRecentFailed.asSharedFlow()

    private val pendingWallpapers = MutableStateFlow<Map<Long, RecentWallpaper>>(emptyMap())

    // 连点几个槽位时按点击顺序串行写入，最后一次点击生效。
    private val wallpaperApplyMutex = Mutex()

    val uiState: StateFlow<TimetableSettingsUiState> = combine(
        repository.observeTimetablePreviews(),
        repository.observePreference(Constants.PREF_CURRENT_TIMETABLE),
        // 壁纸流先给一份空表：解码慢一拍也不能让页面一直转圈。
        wallpapers.onStart { emit(emptyMap()) },
        wallpaperRepository.observeRecents().onStart { emit(emptyList()) },
        pendingWallpapers,
    ) { previews, currentValue, wallpapers, recents, pending ->
        val currentId = currentValue?.toLongOrNull()
            ?.takeIf { id -> previews.any { it.id == id } }
            ?: previews.firstOrNull()?.id
        TimetableSettingsUiState(
            isLoading = false,
            previews = previews,
            currentId = currentId,
            wallpapers = wallpapers,
            recents = recents,
            pendingWallpapers = pending,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TimetableSettingsUiState()
    )

    /**
     * 点最近壁纸格直接选用：「默认」= 清掉该课表壁纸，图片 = 导入该文件。
     * 先记 pending 让 UI 立刻切选中项，写入完成且仍是最后一次点击后再摘掉，
     * 避免「先回弹到旧项再跳到新项」的闪烁。
     */
    fun selectRecent(timetableId: Long, recent: RecentWallpaper) {
        viewModelScope.launch {
            pendingWallpapers.update { it + (timetableId to recent) }
            wallpaperApplyMutex.withLock {
                try {
                    wallpaperRepository.applyRecent(timetableId, recent)
                        .onFailure {
                            removePendingIfCurrent(timetableId, recent)
                            _selectRecentFailed.emit(Unit)
                            return@withLock
                        }
                    // 等壁纸流吐出新状态再摘 pending，让乐观选中与实际选中无缝交接。
                    withTimeoutOrNull(2_000) {
                        wallpaperRepository.observe(timetableId)
                            .first { it.matches(recent) }
                    }
                    removePendingIfCurrent(timetableId, recent)
                } catch (e: CancellationException) {
                    throw e
                }
            }
        }
    }

    private fun removePendingIfCurrent(timetableId: Long, recent: RecentWallpaper) {
        pendingWallpapers.update { pending ->
            if (pending[timetableId] == recent) pending - timetableId else pending
        }
    }

    fun apply(timetableId: Long) {
        if (timetableId == uiState.value.currentId) return
        viewModelScope.launch { repository.selectTimetable(timetableId) }
    }

    fun rename(timetableId: Long, name: String) {
        viewModelScope.launch { repository.renameTimetable(timetableId, name) }
    }

    fun delete(timetableId: Long) {
        viewModelScope.launch { repository.deleteTimetable(timetableId) }
    }
}

private fun WallpaperState.matches(recent: RecentWallpaper): Boolean = when (recent) {
    RecentWallpaper.Default -> this is WallpaperState.None
    is RecentWallpaper.Generated ->
        (this as? WallpaperState.Generated)?.design == recent.design
    is RecentWallpaper.Image ->
        (this as? WallpaperState.Ready)?.contentHash == recent.contentHash
}

class TimetableSettingsViewModelFactory(
    private val repository: SettingsRepository,
    private val wallpaperRepository: WallpaperRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TimetableSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TimetableSettingsViewModel(repository, wallpaperRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
