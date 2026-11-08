package com.star.schedule.feature.holiday.data

import android.util.Log
import com.star.schedule.feature.datasource.domain.RemoteDataSource
import com.star.schedule.feature.holiday.domain.HolidayStore
import java.io.IOException
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

/**
 * 中国法定节假日数据源：抓取 NateScarlet/holiday-cn 的年度 JSON，
 * 内容是国务院公告的放假安排，放假窗口和调休工作日都直接给出，不需要推算。
 *
 * 每次刷新拉「去年 + 今年 + 明年」三年：跨年的假期块拆在两个年份文件里，
 * 只拉当年会丢掉边界的几天（比如元旦连休里的 12 月 30、31 日）。
 * 明年的文件要等国务院公告后才发布，404 属正常，不算刷新失败；
 * 当年拉不到才算失败。
 */
class HolidayCnDataSource(
    private val httpClient: OkHttpClient,
    private val holidayStore: HolidayStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val currentYear: () -> Int = { LocalDate.now().year },
    private val urlTemplates: List<String> = DEFAULT_URL_TEMPLATES,
) : RemoteDataSource {

    override val id: String = SOURCE_ID

    override suspend fun refresh() = withContext(ioDispatcher) {
        val year = currentYear()
        for (targetYear in listOf(year - 1, year, year + 1)) {
            try {
                val body = fetchYear(targetYear)
                if (body == null) {
                    Log.d(TAG, "$targetYear 年数据尚未发布，跳过")
                    continue
                }
                val days = HolidayCnParser.parseYear(body, targetYear)
                holidayStore.replaceYear(targetYear, days)
                Log.d(TAG, "$targetYear 年节假日数据已写入，共 ${days.size} 天")
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                if (targetYear == year) {
                    Log.e(TAG, "$targetYear 年节假日数据抓取失败", error)
                    throw error
                }
                Log.w(TAG, "$targetYear 年节假日数据抓取失败，已跳过", error)
            }
        }
    }

    /** 返回 null 表示该年份各镜像都是 404，即数据尚未发布。 */
    private suspend fun fetchYear(year: Int): String? {
        var sawNotFound = false
        var lastError: Exception? = null
        for (template in orderedTemplates()) {
            val url = String.format(template, year)
            try {
                newCall(url).await().use { response ->
                    when (response.code) {
                        200 -> {
                            Log.d(TAG, "$year 年数据取自 $url")
                            preferredTemplate = template
                            return readBodyCapped(response)
                        }
                        404 -> {
                            sawNotFound = true
                            Log.d(TAG, "$year 年在 $url 返回 404")
                        }
                        else -> throw IOException("HTTP ${response.code}")
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                Log.w(TAG, "$year 年从 $url 获取失败，尝试下一个镜像", error)
                lastError = error
            }
        }
        // 全部镜像都返回 404 才是「未发布」；只要有镜像没连上就不能下这个结论。
        if (sawNotFound && lastError == null) return null
        throw lastError ?: IOException("all mirrors failed")
    }

    /**
     * 上次成功的镜像排最前：三个镜像被墙的概率各自独立，
     * 一个能通说明当前网络下它可用，同一次刷新的其它年份和下次刷新都受益。
     */
    @Volatile
    private var preferredTemplate: String? = null

    private fun orderedTemplates(): List<String> {
        val preferred = preferredTemplate ?: return urlTemplates
        return listOf(preferred) + urlTemplates.filter { it != preferred }
    }

    private fun newCall(url: String): Call =
        httpClient.newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build(),
        )

    private fun readBodyCapped(response: Response): String {
        response.body.use { body ->
            val source = body.source()
            val buffer = Buffer()
            while (true) {
                val read = source.read(buffer, BODY_CHUNK)
                if (read == -1L) break
                if (buffer.size > BODY_LIMIT) {
                    throw IOException("holiday data too large")
                }
            }
            return buffer.readUtf8()
        }
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resume(response) { _, _, _ -> response.close() }
                } else {
                    response.close()
                }
            }
        })
    }

    companion object {
        const val SOURCE_ID = "holiday_cn"
        private const val TAG = "HolidayCnDataSource"
        private const val USER_AGENT = "starSchedule-android"
        private const val BODY_LIMIT = 1L * 1024 * 1024
        private const val BODY_CHUNK = 8L * 1024

        /**
         * 镜像按国内可达性排序：jiejiariapi 是境内托管的同源 API（返回 date 为键的
         * 另一种格式，解析器兼容）；jsDelivr 换两个不同的边缘域名，SNI 阻断通常
         * 只打 cdn.jsdelivr.net 一个；GitHub raw 放最后兜底。
         */
        val DEFAULT_URL_TEMPLATES = listOf(
            "https://api.jiejiariapi.com/v1/holidays/%d",
            "https://fastly.jsdelivr.net/gh/NateScarlet/holiday-cn@master/%d.json",
            "https://testingcf.jsdelivr.net/gh/NateScarlet/holiday-cn@master/%d.json",
            "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master/%d.json",
            "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/%d.json",
        )
    }
}
