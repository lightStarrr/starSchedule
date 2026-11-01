package com.star.schedule.feature.settings.domain

/**
 * 云端 AI 的当日额度。
 *
 * 配额按设备维度按天算，`resetAt` 是次日零点的 ISO 时间串（服务端时区）。
 */
data class AiUsage(
    val dailyQuota: Long,
    val usedTokens: Long,
    val remainingTokens: Long,
    val requestCount: Long,
    val resetAt: String?,
) {
    /** 已用比例，0..1；配额为 0 时按 0 处理，避免除零。 */
    val usedFraction: Float
        get() = if (dailyQuota <= 0L) 0f else (usedTokens.toFloat() / dailyQuota).coerceIn(0f, 1f)
}

interface AiUsageRepository {
    suspend fun fetchUsage(): Result<AiUsage>
}
