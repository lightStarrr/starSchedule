package com.star.schedule.app

import android.content.Context
import android.util.Log
import com.star.schedule.R
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.feature.datasource.data.RoomDataSourceRepository
import com.star.schedule.feature.datasource.domain.DataSourceDescriptor
import com.star.schedule.feature.datasource.domain.DataSourceRepository
import com.star.schedule.feature.holiday.data.HolidayCnDataSource
import com.star.schedule.feature.holiday.data.HolidayCnParser
import com.star.schedule.feature.holiday.data.RoomHolidayStore
import com.star.schedule.feature.holiday.domain.HolidayStore
import com.star.schedule.platform.datasource.WorkManagerDataSourceRefreshScheduler
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class DataSourceDependencies(
    context: Context,
    scope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val appContext = context.applicationContext

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    val holidayStore: HolidayStore = RoomHolidayStore(DatabaseProvider.db.holidayDao())

    private val refreshScheduler = WorkManagerDataSourceRefreshScheduler(appContext)

    val repository: DataSourceRepository = RoomDataSourceRepository(
        sources = listOf(
            DataSourceDescriptor(
                source = HolidayCnDataSource(
                    httpClient = httpClient,
                    holidayStore = holidayStore,
                    ioDispatcher = ioDispatcher,
                ),
                nameRes = R.string.datasource_holiday_cn_name,
                descriptionRes = R.string.datasource_holiday_cn_description,
            ),
        ),
        dao = DatabaseProvider.dao(),
        scheduler = refreshScheduler,
    )

    init {
        scope.launch {
            seedBundledHolidaysIfEmpty()
            reconcileRefreshSchedule()
        }
    }

    /**
     * 首次安装没网也能有数据：assets 里带一份最近年份的快照，
     * 只在库为空时灌一次，之后以远端刷新为准。
     */
    private suspend fun seedBundledHolidaysIfEmpty() {
        if (!holidayStore.isEmpty()) return
        Log.d(TAG, "节假日缓存为空，灌入内置快照")
        appContext.assets.list(BUNDLED_HOLIDAY_DIR).orEmpty().forEach { file ->
            val year = file.removeSuffix(".json").toIntOrNull() ?: return@forEach
            runCatching {
                val body = appContext.assets
                    .open("$BUNDLED_HOLIDAY_DIR/$file")
                    .bufferedReader()
                    .use { it.readText() }
                holidayStore.replaceYear(year, HolidayCnParser.parseYear(body, year))
            }
        }
    }

    /** 启动时按偏好重新断言一次调度，覆盖升级、恢复备份后 WorkManager 任务丢失的情况。 */
    private suspend fun reconcileRefreshSchedule() {
        val days = repository.observeRefreshInterval().first().days
        Log.d(TAG, "断言数据源定时刷新任务，间隔 $days 天")
        refreshScheduler.schedule(days)
    }

    private companion object {
        const val TAG = "DataSourceDependencies"
        const val BUNDLED_HOLIDAY_DIR = "holiday-cn"
    }
}
