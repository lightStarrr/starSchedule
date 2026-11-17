package com.star.schedule.feature.datasource.domain

import androidx.annotation.StringRes
import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * 一个可选下载资源（字体、素材包等）：与定时刷新的数据源分开建模，
 * 不参与后台 refreshAll，只在用户主动点按时下载。
 */
interface OptionalResource {
    val id: String
    val expectedSizeBytes: Long

    /**
     * 已安装的文件；未安装返回 null。
     * 只做「存在 + 大小 == [expectedSizeBytes]」的廉价检查，
     * 完整 SHA-256 校验在下载落地时做。
     */
    fun installedFile(): File?

    /** 下载并校验后原子落地；[onProgress] 传已下载字节数。失败抛异常。 */
    suspend fun download(onProgress: (Long) -> Unit)

    /** 删除已安装文件与下载残留的临时文件。 */
    suspend fun delete()
}

/** 可选资源与它的展示文案绑定，注册进仓库后出现在设置页列表里。 */
data class OptionalResourceDescriptor(
    val resource: OptionalResource,
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
)

sealed interface OptionalResourceState {
    data object NotInstalled : OptionalResourceState

    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : OptionalResourceState

    data class Installed(
        val file: File,
        val sizeBytes: Long,
    ) : OptionalResourceState

    data class Failed(val error: String) : OptionalResourceState
}

interface OptionalResourceRepository {
    val resources: List<OptionalResourceDescriptor>
    fun observeState(id: String): Flow<OptionalResourceState>

    /** 开始下载；已在下载则忽略。 */
    fun download(id: String)

    fun cancelDownload(id: String)

    /** 删除在仓库自己的 scope 里异步执行，页面销毁不打断。 */
    fun delete(id: String)
}
