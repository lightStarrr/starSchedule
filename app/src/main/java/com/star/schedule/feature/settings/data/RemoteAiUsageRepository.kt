package com.star.schedule.feature.settings.data

import android.content.Context
import com.star.schedule.BuildConfig
import com.star.schedule.core.network.ApiClient
import com.star.schedule.core.network.ApiJson
import com.star.schedule.core.network.ApiRequest
import com.star.schedule.core.network.ApiResult
import com.star.schedule.core.network.ApiUrlValidator
import com.star.schedule.core.network.RetryPolicy
import com.star.schedule.core.network.SharedApiHttpClient
import com.star.schedule.core.telemetry.DeviceCredentialStore
import com.star.schedule.core.telemetry.KeystoreAesGcmCipher
import com.star.schedule.core.telemetry.SharedPreferencesStore
import com.star.schedule.feature.settings.domain.AiUsage
import com.star.schedule.feature.settings.domain.AiUsageRepository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 从后端取云端 AI 的当日用量。
 *
 * 只读一次、不做 401 续期：用量是展示型数据，令牌失效时让界面显示「暂时取不到」即可，
 * 不必在这里牵扯设备注册流程（`onUnauthorized` 返回 null，直接按未授权失败）。
 */
class RemoteAiUsageRepository(context: Context) : AiUsageRepository {

    private val appContext = context.applicationContext

    private val credentials by lazy {
        DeviceCredentialStore(
            store = SharedPreferencesStore(
                appContext.getSharedPreferences(
                    DeviceCredentialStore.PREFERENCES_NAME,
                    Context.MODE_PRIVATE,
                ),
            ),
            cipher = KeystoreAesGcmCipher(),
        )
    }

    private val client by lazy {
        ApiClient(
            httpClient = SharedApiHttpClient.get(),
            baseUrl = BuildConfig.API_BASE_URL.trim(),
            tokenProvider = { credentials.token() },
            onUnauthorized = { null },
        )
    }

    override suspend fun fetchUsage(): Result<AiUsage> {
        val baseUrl = BuildConfig.API_BASE_URL.trim()
        if (!ApiUrlValidator.isUsable(baseUrl)) {
            return Result.failure(IllegalStateException("API 地址不可用"))
        }

        return when (
            val result = client.call(
                ApiRequest(method = "GET", path = "ai/usage", retryPolicy = RetryPolicy.Read),
            ) { body -> ApiJson.decodeFromString<AiUsageDto>(body) }
        ) {
            is ApiResult.Success -> Result.success(result.value.toDomain())
            is ApiResult.Failure -> Result.failure(IllegalStateException(result.error.toString()))
        }
    }
}

private fun AiUsageDto.toDomain() = AiUsage(
    dailyQuota = dailyQuota,
    usedTokens = usedTokens,
    remainingTokens = remainingTokens,
    requestCount = requestCount,
    resetAt = resetAt,
)

@Serializable
private data class AiUsageDto(
    @SerialName("daily_quota") val dailyQuota: Long = 0,
    @SerialName("used_tokens") val usedTokens: Long = 0,
    @SerialName("remaining_tokens") val remainingTokens: Long = 0,
    @SerialName("request_count") val requestCount: Long = 0,
    @SerialName("reset_at") val resetAt: String? = null,
)
