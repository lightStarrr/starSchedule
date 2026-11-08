package com.star.schedule.feature.datasource.data

import android.util.Log
import com.star.schedule.core.common.Constants
import com.star.schedule.core.database.ScheduleDao
import com.star.schedule.feature.datasource.domain.DataSourceDescriptor
import com.star.schedule.feature.datasource.domain.DataSourceRefreshResult
import com.star.schedule.feature.datasource.domain.DataSourceRefreshScheduler
import com.star.schedule.feature.datasource.domain.DataSourceRepository
import com.star.schedule.feature.datasource.domain.DataSourceStatus
import com.star.schedule.feature.datasource.domain.RefreshInterval
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomDataSourceRepository(
    override val sources: List<DataSourceDescriptor>,
    private val dao: ScheduleDao,
    private val scheduler: DataSourceRefreshScheduler,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : DataSourceRepository {

    override fun observeStatus(sourceId: String): Flow<DataSourceStatus> =
        combine(
            dao.getPreferenceFlow(Constants.datasourceLastSuccessKey(sourceId)),
            dao.getPreferenceFlow(Constants.datasourceLastErrorKey(sourceId)),
        ) { successAt, error ->
            DataSourceStatus(
                id = sourceId,
                lastSuccessAt = successAt?.toLongOrNull(),
                lastError = error?.takeIf { it.isNotBlank() },
            )
        }

    override suspend fun refreshSource(sourceId: String): DataSourceRefreshResult {
        val descriptor = sources.firstOrNull { it.source.id == sourceId }
            ?: return DataSourceRefreshResult.Failure("unknown source: $sourceId")
        Log.d(TAG, "开始刷新数据源: $sourceId")
        return try {
            descriptor.source.refresh()
            dao.setPreference(Constants.datasourceLastSuccessKey(sourceId), clock().toString())
            dao.setPreference(Constants.datasourceLastErrorKey(sourceId), "")
            Log.d(TAG, "数据源刷新成功: $sourceId")
            DataSourceRefreshResult.Success
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            val message = error.message ?: error.javaClass.simpleName
            dao.setPreference(Constants.datasourceLastErrorKey(sourceId), message)
            Log.e(TAG, "数据源刷新失败: $sourceId", error)
            DataSourceRefreshResult.Failure(message)
        }
    }

    override suspend fun refreshAll() {
        Log.d(TAG, "开始刷新全部数据源，共 ${sources.size} 个")
        sources.forEach { refreshSource(it.source.id) }
        Log.d(TAG, "全部数据源刷新结束")
    }

    override fun observeRefreshInterval(): Flow<RefreshInterval> =
        dao.getPreferenceFlow(Constants.PREF_DATASOURCE_REFRESH_INTERVAL_DAYS)
            .map { RefreshInterval(it?.toIntOrNull() ?: DEFAULT_INTERVAL_DAYS) }

    override suspend fun setRefreshInterval(interval: RefreshInterval) {
        dao.setPreference(
            Constants.PREF_DATASOURCE_REFRESH_INTERVAL_DAYS,
            interval.days.toString(),
        )
        scheduler.schedule(interval.days)
    }

    companion object {
        private const val TAG = "DataSourceRepository"

        /** 未设置过时的默认频率：每周一次。 */
        const val DEFAULT_INTERVAL_DAYS = 7
    }
}
