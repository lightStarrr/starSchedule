package com.star.schedule.core.telemetry

import com.star.schedule.core.network.ApiError
import com.star.schedule.core.network.ApiResult
import com.star.schedule.core.network.ClientInfo
import com.star.schedule.core.network.ServerClock
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 启动记录的产生与投递。
 *
 * 两个触发点各自生成新的 `session_id`（契约要求）：
 * - [onProcessStart]（`Application.onCreate`）一定是冷启动，且**立刻落盘**——进程可能被
 *   Provider/Receiver 拉起而永远等不到 `onStart`，不落盘就会丢记录。
 * - [onForeground]（`ProcessLifecycleOwner.onStart`）一定是热启动；本进程首次触发时，
 *   顺便把 `cold_start_ms` 补到那条冷启动记录上。
 *
 * 两个方法都只在主线程调用，内部只做内存操作 + 一个 IO 协程，绝不阻塞启动。
 */
class LaunchRecorder(
    private val queue: LaunchQueue,
    private val api: DeviceApi,
    private val session: DeviceSession,
    private val clock: ServerClock,
    private val clientInfo: () -> ClientInfo,
    private val scope: CoroutineScope,
    private val processStartElapsedMillis: Long,
    private val elapsedMillis: () -> Long,
    private val newSessionId: () -> String = { UUID.randomUUID().toString() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private var coldRecord: LaunchRecord? = null
    private var foregroundHandled = false

    fun onProcessStart() {
        val record = LaunchRecord(
            sessionId = newSessionId(),
            launchedAt = clock.nowUtcRfc3339(),
            launchType = LAUNCH_TYPE_COLD,
            createdAtEpochMs = clock.nowUtc().toEpochMilli(),
        )
        coldRecord = record
        scope.launch(ioDispatcher) { queue.upsert(record) }
    }

    fun onForeground() {
        val firstForeground = !foregroundHandled
        foregroundHandled = true
        val cold = coldRecord
        val warm = LaunchRecord(
            sessionId = newSessionId(),
            launchedAt = clock.nowUtcRfc3339(),
            launchType = LAUNCH_TYPE_WARM,
            createdAtEpochMs = clock.nowUtc().toEpochMilli(),
        )
        val coldStartMs = (elapsedMillis() - processStartElapsedMillis).coerceAtLeast(0)

        scope.launch(ioDispatcher) {
            if (firstForeground && cold != null) {
                queue.upsert(cold.copy(coldStartMs = coldStartMs))
            }
            queue.upsert(warm)
            flush()
        }
    }

    /**
     * 依次注册、校验设备状态、投递队列。
     *
     * 返回 true 表示还有积压（网络/429/5xx 这类可重试的失败），下次启动再补投。
     * 4xx 与封禁是重发也不会成功的错误，必须直接出队，否则一条坏记录会卡住队头直到 TTL。
     */
    suspend fun flush(): Boolean = withContext(ioDispatcher) {
        when (session.ensureRegistered()) {
            is ApiResult.Failure -> return@withContext false
            is ApiResult.Success -> Unit
        }
        session.checkDeviceIfDue()

        var pending = false
        for (record in queue.snapshot()) {
            when (val result = api.reportLaunch(record.toBody(clientInfo()))) {
                is ApiResult.Success -> queue.remove(record.sessionId)
                is ApiResult.Failure -> {
                    if (isPermanentFailure(result.error)) {
                        queue.remove(record.sessionId)
                    } else {
                        pending = true
                        break
                    }
                }
            }
        }
        pending
    }

    private fun LaunchRecord.toBody(info: ClientInfo) = LaunchBody(
        sessionId = sessionId,
        launchedAt = launchedAt,
        launchType = launchType,
        appVersion = info.normalizedVersionName,
        versionCode = info.versionCode,
        osVersion = info.osVersion.take(16).ifBlank { "unknown" },
        sdkInt = info.sdkInt,
        arch = info.arch.take(32).ifBlank { "unknown" },
        extra = coldStartMs?.let { mapOf(KEY_COLD_START_MS to it) },
    )

    private fun isPermanentFailure(error: ApiError): Boolean = when (error) {
        is ApiError.Banned, is ApiError.Unauthorized -> true
        // 408 请求超时与 429 属于可重试；其余 4xx 重发也不会成功
        is ApiError.Http -> error.status in 400..499 && error.status != 408 && error.status != 429
        else -> false
    }

    companion object {
        const val LAUNCH_TYPE_COLD = "cold"
        const val LAUNCH_TYPE_WARM = "warm"
        const val KEY_COLD_START_MS = "cold_start_ms"
    }
}
