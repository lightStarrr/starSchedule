package com.star.schedule.feature.datasource.domain

import androidx.annotation.StringRes

/**
 * 一个可手动或定时刷新的远端数据源。
 *
 * 实现只负责「抓取并写入本地存储」，不写状态——状态（最后成功时间、错误信息）
 * 由仓库层统一记录，实现端失败时抛异常即可。
 */
interface RemoteDataSource {
    val id: String

    suspend fun refresh()
}

/** 数据源与它的展示文案绑定，注册进仓库后出现在设置页列表里。 */
data class DataSourceDescriptor(
    val source: RemoteDataSource,
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
)

/** 单个数据源的刷新状态，持久化在 preference 表里。 */
data class DataSourceStatus(
    val id: String,
    val lastSuccessAt: Long?,
    val lastError: String?,
)

sealed interface DataSourceRefreshResult {
    data object Success : DataSourceRefreshResult

    data class Failure(val error: String) : DataSourceRefreshResult
}

/**
 * 自动刷新间隔，统一按天存储；[OFF] 表示关闭。
 *
 * WorkManager 最小周期是 15 分钟，但这类公告数据按天粒度足够，
 * 自定义间隔也只开放到天，避免 UI 上出现没有意义的精细档位。
 */
data class RefreshInterval(val days: Int) {
    init {
        require(days >= 0) { "interval days must be >= 0" }
    }

    val enabled: Boolean get() = days > 0

    companion object {
        val OFF = RefreshInterval(0)
        val DAILY = RefreshInterval(1)
        val WEEKLY = RefreshInterval(7)
        val MONTHLY = RefreshInterval(30)

        val PRESETS = listOf(DAILY, WEEKLY, MONTHLY)
    }
}
