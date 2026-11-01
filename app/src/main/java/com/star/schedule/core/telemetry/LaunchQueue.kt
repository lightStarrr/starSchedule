package com.star.schedule.core.telemetry

import com.star.schedule.core.network.ApiJson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 一次待上报的启动。
 *
 * [createdAtEpochMs] 用于过期淘汰，与 `launchedAt` 分开：后者是提交给服务端的时间，
 * 可能因为时钟校正而与本地时间不一致。
 */
@Serializable
data class LaunchRecord(
    val sessionId: String,
    val launchedAt: String,
    val launchType: String,
    val coldStartMs: Long? = null,
    val createdAtEpochMs: Long,
)

/**
 * 待投队列：单键存一个 JSON 数组。
 *
 * 之所以要落盘而不是只放内存，是契约要求的「进程被杀后下次启动补投」。
 * 同 `session_id` 的 upsert 是合并而不是新增——冷启动那条记录会在首个 onStart 时
 * 补上 `coldStartMs`，不能变成两条。
 */
class LaunchQueue(
    private val store: KeyValueStore,
    private val json: Json = ApiJson,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val maxRecords: Int = DEFAULT_MAX_RECORDS,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) {
    private val mutex = Mutex()

    suspend fun upsert(record: LaunchRecord) = mutex.withLock {
        val records = readLocked().toMutableList()
        val index = records.indexOfFirst { it.sessionId == record.sessionId }
        if (index >= 0) {
            records[index] = records[index].mergeWith(record)
        } else {
            records += record
        }
        writeLocked(records)
    }

    /** 取出全部待投记录，顺带把过期项裁掉。 */
    suspend fun snapshot(): List<LaunchRecord> = mutex.withLock {
        val records = readLocked()
        writeLocked(records)
        records
    }

    suspend fun remove(sessionId: String) = mutex.withLock {
        writeLocked(readLocked().filterNot { it.sessionId == sessionId })
    }

    suspend fun size(): Int = mutex.withLock { readLocked().size }

    private fun readLocked(): List<LaunchRecord> {
        val raw = store.getString(KEY_RECORDS) ?: return emptyList()
        val decoded = runCatching { json.decodeFromString<List<LaunchRecord>>(raw) }
            .getOrNull()
            ?: return emptyList()
        val threshold = nowMillis() - ttlMillis
        return decoded.filter { it.createdAtEpochMs >= threshold }
    }

    private fun writeLocked(records: List<LaunchRecord>) {
        val trimmed = records.sortedBy { it.createdAtEpochMs }.takeLast(maxRecords)
        store.putString(KEY_RECORDS, json.encodeToString(trimmed))
    }

    private fun LaunchRecord.mergeWith(update: LaunchRecord): LaunchRecord = copy(
        launchedAt = update.launchedAt.ifBlank { launchedAt },
        launchType = update.launchType.ifBlank { launchType },
        coldStartMs = update.coldStartMs ?: coldStartMs,
        createdAtEpochMs = minOf(createdAtEpochMs, update.createdAtEpochMs),
    )

    companion object {
        const val PREFERENCES_NAME = "launch_queue"
        const val KEY_RECORDS = "records"

        /** 每次冷启动最多两条记录，64 条足够覆盖一周完全离线的极端情况。 */
        const val DEFAULT_MAX_RECORDS = 64

        /** 超过一周的记录服务端也不认（`launched_at` 偏差超 7 天会被改成接收时间）。 */
        const val DEFAULT_TTL_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
