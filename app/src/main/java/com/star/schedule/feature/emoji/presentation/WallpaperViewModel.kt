package com.star.schedule.feature.emoji.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.star.schedule.feature.datasource.domain.OptionalResourceRepository
import com.star.schedule.feature.datasource.domain.OptionalResourceState
import com.star.schedule.feature.emoji.data.NotoEmojiFontResource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * 壁纸页的「表情字体是否就绪」状态源：
 * 「制作表情壁纸」需要字体已下载，没下载就在页上弹进度对话框。
 */
class WallpaperViewModel(
    private val optionalResources: OptionalResourceRepository,
) : ViewModel() {

    val emojiFontState: StateFlow<OptionalResourceState> = optionalResources
        .observeState(NotoEmojiFontResource.ID)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            OptionalResourceState.NotInstalled,
        )

    /** 对话框「约 X MB」要用：未开始下载时状态里没有大小。 */
    val emojiFontExpectedBytes: Long = optionalResources.resources
        .firstOrNull { it.resource.id == NotoEmojiFontResource.ID }
        ?.resource?.expectedSizeBytes
        ?: 0L

    fun downloadEmojiFont() = optionalResources.download(NotoEmojiFontResource.ID)

    fun cancelEmojiFontDownload() =
        optionalResources.cancelDownload(NotoEmojiFontResource.ID)
}

class WallpaperViewModelFactory(
    private val optionalResources: OptionalResourceRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(WallpaperViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return WallpaperViewModel(optionalResources) as T
    }
}
