package com.star.schedule.feature.settings.data

import android.content.Context
import com.star.schedule.BuildConfig
import com.star.schedule.core.network.ApiClient
import com.star.schedule.core.network.ApiError
import com.star.schedule.core.network.ApiJson
import com.star.schedule.core.network.ApiRequest
import com.star.schedule.core.network.ApiResult
import com.star.schedule.core.network.ApiUrlValidator
import com.star.schedule.core.network.RetryPolicy
import com.star.schedule.core.network.SharedApiHttpClient
import com.star.schedule.core.telemetry.DeviceCredentialStore
import com.star.schedule.core.telemetry.KeystoreAesGcmCipher
import com.star.schedule.core.telemetry.SharedPreferencesStore
import com.star.schedule.feature.settings.domain.AiModel
import com.star.schedule.feature.settings.domain.AiModelCatalog
import com.star.schedule.feature.settings.domain.AiModelRepository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 从后端取「后台已启用」的模型列表。
 *
 * 与用量一样只读一次、不做 401 续期：列表是展示型数据，令牌失效就显示「暂时取不到」，
 * 不把设备注册流程牵扯进来。
 */
class RemoteAiModelRepository(context: Context) : AiModelRepository {

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

    override suspend fun fetchModels(): Result<AiModelCatalog> {
        val baseUrl = BuildConfig.API_BASE_URL.trim()
        if (!ApiUrlValidator.isUsable(baseUrl)) {
            return Result.failure(IllegalStateException("API 地址不可用"))
        }

        return when (
            val result = client.call(
                ApiRequest(method = "GET", path = "ai/models", retryPolicy = RetryPolicy.Read),
            ) { body -> ApiJson.decodeFromString<AiModelListDto>(body) }
        ) {
            is ApiResult.Success -> Result.success(result.value.toDomain())
            is ApiResult.Failure -> {
                // 503 AI_NOT_CONFIGURED 表示后台一个模型都没配，这属于「空列表」而不是网络故障：
                // 界面该提示「服务端暂未提供模型」，而不是让人以为网络有问题
                val error = result.error
                if (error is ApiError.Http && error.status == HTTP_SERVICE_UNAVAILABLE) {
                    Result.success(AiModelCatalog())
                } else {
                    Result.failure(IllegalStateException(error.toString()))
                }
            }
        }
    }

    private companion object {
        const val HTTP_SERVICE_UNAVAILABLE = 503
    }
}

private fun AiModelListDto.toDomain() = AiModelCatalog(
    models = items.mapNotNull { dto ->
        // alias 是落库与请求用的键，缺了这条就没法选；展示名缺失时退回 alias
        val alias = dto.alias?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val displayName = dto.displayName?.takeIf { it.isNotBlank() } ?: alias
        AiModel(alias = alias, displayName = displayName)
    },
)

@Serializable
private data class AiModelListDto(
    val items: List<AiModelDto> = emptyList(),
)

@Serializable
private data class AiModelDto(
    @SerialName("alias") val alias: String? = null,
    @SerialName("display_name") val displayName: String? = null,
)
