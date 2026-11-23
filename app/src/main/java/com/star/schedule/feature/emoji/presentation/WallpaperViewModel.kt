package com.star.schedule.feature.emoji.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.feature.datasource.domain.OptionalResourceRepository
import com.star.schedule.feature.datasource.domain.OptionalResourceState
import com.star.schedule.feature.emoji.data.NotoEmojiFontResource
import com.star.schedule.feature.wallpaper.domain.CachedWallpaper
import com.star.schedule.feature.wallpaper.domain.RecentWallpaper
import com.star.schedule.feature.wallpaper.domain.WallpaperRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 壁纸页状态源：
 * - 表情字体是否就绪（「制作表情壁纸」前按需弹下载进度对话框）；
 * - 已缓存壁纸列表与清理操作。
 */
class WallpaperViewModel(
    private val optionalResources: OptionalResourceRepository,
    private val wallpaperRepository: WallpaperRepository,
) : ViewModel() {

    val emojiFontState: StateFlow<OptionalResourceState> = optionalResources
        .observeState(NotoEmojiFontResource.ID)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            OptionalResourceState.NotInstalled,
        )

    val cachedWallpapers: StateFlow<List<CachedWallpaper>> = wallpaperRepository
        .observeCachedWallpapers()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    private val _cacheActionFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val cacheActionFailed: SharedFlow<Unit> = _cacheActionFailed.asSharedFlow()

    /** 对话框「约 X MB」要用：未开始下载时状态里没有大小。 */
    val emojiFontExpectedBytes: Long = optionalResources.resources
        .firstOrNull { it.resource.id == NotoEmojiFontResource.ID }
        ?.resource?.expectedSizeBytes
        ?: 0L

    fun downloadEmojiFont() = optionalResources.download(NotoEmojiFontResource.ID)

    fun cancelEmojiFontDownload() =
        optionalResources.cancelDownload(NotoEmojiFontResource.ID)

    fun deleteCachedWallpaper(wallpaper: RecentWallpaper) {
        viewModelScope.launch {
            wallpaperRepository.deleteCachedWallpaper(wallpaper).onFailure {
                _cacheActionFailed.tryEmit(Unit)
            }
        }
    }
}

class WallpaperViewModelFactory(
    private val optionalResources: OptionalResourceRepository,
    private val wallpaperRepository: WallpaperRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(WallpaperViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return WallpaperViewModel(optionalResources, wallpaperRepository) as T
    }
}
