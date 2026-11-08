package com.star.schedule.feature.datasource.domain

import kotlinx.coroutines.flow.Flow

interface DataSourceRepository {

    /** 已注册的数据源，按注册顺序展示。 */
    val sources: List<DataSourceDescriptor>

    fun observeStatus(sourceId: String): Flow<DataSourceStatus>

    /** 手动刷新单个源；失败返回错误信息，同时写入状态。 */
    suspend fun refreshSource(sourceId: String): DataSourceRefreshResult

    /** 定时任务入口：刷新全部源，单个失败不影响其它源。 */
    suspend fun refreshAll()

    fun observeRefreshInterval(): Flow<RefreshInterval>

    suspend fun setRefreshInterval(interval: RefreshInterval)
}
