package com.star.schedule.feature.wallpaper.domain

import kotlinx.coroutines.flow.Flow

interface WallpaperRepository {
    fun observe(timetableId: Long): Flow<WallpaperState>

    /**
     * 全局共享的最近壁纸列表（最新在前）；缺失时等价于只有「默认」。
     * 不包含当前正在用的那项的逻辑在 UI 侧（current 因课表而异）。
     */
    fun observeRecents(): Flow<List<RecentWallpaper>>

    suspend fun importFromUri(timetableId: Long, uriString: String): Result<Unit>

    /**
     * 预览页暂存一张候选照片：复制进缓存、校验可解码、取好取色 seed。
     * 还没写入任何课表；不用了要交给 [discardStaged] 清掉。
     */
    suspend fun stagePreview(uriString: String): Result<StagedWallpaper>

    /**
     * 删除 [stagePreview] 产出的暂存文件；只允许删预览缓存目录里的文件。
     *
     * 同步删除而非 suspend：ViewModel.onCleared 时 viewModelScope 已取消，
     * 删一个缓存文件足够快，直接同步做掉最稳妥。
     */
    fun discardStaged(path: String)

    /**
     * 把预览页暂存的照片设为课表壁纸。给出 [blur] 时先把模糊烘焙进文件
     * （放大到屏幕像素 1:1 再按像素半径模糊），此后显示端不需要任何后期处理。
     */
    suspend fun applyStaged(
        timetableId: Long,
        stagedPath: String,
        blur: WallpaperBlurSpec?,
        scrimEnabled: Boolean = true,
    ): Result<Unit>

    /**
     * 选用最近列表里的一项：[RecentWallpaper.Default] 等价于 [clear]，
     * 图片项把 recent 文件导入目标课表。成功后该项会被移到列表最前。
     */
    suspend fun applyRecent(timetableId: Long, recent: RecentWallpaper): Result<Unit>

    suspend fun applyDesign(timetableId: Long, design: WallpaperDesign): Result<Unit>

    suspend fun clear(timetableId: Long)
}
