package com.star.schedule.feature.settings.data

import com.star.schedule.BuildConfig
import com.star.schedule.core.network.ApiClient
import com.star.schedule.core.network.ApiJson
import com.star.schedule.core.network.ApiRequest
import com.star.schedule.core.network.ApiResult
import com.star.schedule.core.network.ApiUrlValidator
import com.star.schedule.core.network.RetryPolicy
import com.star.schedule.core.telemetry.Telemetry
import com.star.schedule.feature.settings.domain.AiUsage
import com.star.schedule.feature.settings.domain.AiUsageRepository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 从后端取云端 AI 的当日用量。
 *
 * 认证复用启动统计的设备会话，兼容新旧服务端的不限量语义。
 */
class RemoteAiUsageRepository internal constructor(
    private val baseUrl: String,
    clientProvider: () -> ApiClient,
) : AiUsageRepository {

    constructor() : this(
        baseUrl = BuildConfig.API_BASE_URL.trim(),
        clientProvider = { Telemetry.apiClient() },
    )

    private val client by lazy(clientProvider)

    override suspend fun fetchUsage(): Result<AiUsage> {
        if (!ApiUrlValidator.isUsable(baseUrl)) {
            return Result.failure(IllegalStateException("API 地址不可用"))
        }

        return when (
            val result = client.call(
                ApiRequest(method = "GET", path = "ai/usage", retryPolicy = RetryPolicy.Read),
            ) { body -> ApiJson.decodeFromString<AiUsageDto>(body).toDomain() }
        ) {
            is ApiResult.Success -> Result.success(result.value)
            is ApiResult.Failure -> Result.failure(IllegalStateException(result.error.toString()))
        }
    }
}

private fun AiUsageDto.toDomain(): AiUsage {
    val unlimited = quotaUnlimited ?: (dailyQuota == 0L)
    require(dailyQuota >= 0L && usedTokens >= 0L && requestCount >= 0L)
    require(unlimited || dailyQuota > 0L)
    return AiUsage(
        dailyQuota = dailyQuota,
        quotaUnlimited = unlimited,
        usedTokens = usedTokens,
        remainingTokens = if (unlimited) {
            null
        } else {
            remainingTokens ?: (dailyQuota - usedTokens).coerceAtLeast(0L)
        },
        requestCount = requestCount,
        resetAt = resetAt,
    )
}

@Serializable
private data class AiUsageDto(
    @SerialName("daily_quota") val dailyQuota: Long,
    @SerialName("quota_unlimited") val quotaUnlimited: Boolean? = null,
    @SerialName("used_tokens") val usedTokens: Long = 0,
    @SerialName("remaining_tokens") val remainingTokens: Long? = null,
    @SerialName("request_count") val requestCount: Long = 0,
    @SerialName("reset_at") val resetAt: String? = null,
)
