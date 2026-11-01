package com.star.schedule.core.network

/**
 * 重试策略：`backoffMillis` 的条数就是允许的重试次数，总尝试次数 = 条数 + 1。
 *
 * 数值来自服务端契约的重试矩阵（`docs/api.md` 第 10 节）。所有等待都做上限截断，
 * 否则服务端回一个 `Retry-After: 3600` 就能把协程挂住一小时。
 */
data class RetryPolicy(
    val backoffMillis: List<Long>,
    val maxServerErrorRetries: Int,
    val rateLimitFallbackMillis: Long = DEFAULT_RATE_LIMIT_DELAY_MS,
    val maxDelayMillis: Long = MAX_DELAY_MS,
) {
    /** 第 [attempt] 次重试（从 0 开始）的退避时长。 */
    fun backoffDelay(attempt: Int): Long {
        val backoff = backoffMillis.getOrElse(attempt) { backoffMillis.lastOrNull() ?: 0L }
        return backoff.coerceIn(0L, maxDelayMillis)
    }

    /**
     * 429 的等待时长：服务端给了 `Retry-After` 就用它（同样封顶），
     * 没给就退回 [rateLimitFallbackMillis]，契约里建议的默认值是 3 秒。
     */
    fun rateLimitDelay(retryAfterSeconds: Long?): Long =
        retryAfterSeconds?.let { (it * 1000L).coerceIn(0L, maxDelayMillis) } ?: rateLimitFallbackMillis

    companion object {
        const val MAX_DELAY_MS = 30_000L
        const val DEFAULT_RATE_LIMIT_DELAY_MS = 3_000L

        /** 注册：网络错误重试 3 次，退避 1s / 3s / 7s。 */
        val Register = RetryPolicy(
            backoffMillis = listOf(1_000L, 3_000L, 7_000L),
            maxServerErrorRetries = 2,
        )

        /** 读接口（/devices/me）：重试 2 次。 */
        val Read = RetryPolicy(
            backoffMillis = listOf(1_000L, 3_000L),
            maxServerErrorRetries = 2,
        )

        /** 启动上报：契约「最多 5 次」，即首次 + 4 次重试。 */
        val Launch = RetryPolicy(
            backoffMillis = listOf(1_000L, 2_000L, 4_000L, 8_000L),
            maxServerErrorRetries = 2,
        )

        /** 明确不该重试的请求。 */
        val None = RetryPolicy(backoffMillis = emptyList(), maxServerErrorRetries = 0)
    }
}
