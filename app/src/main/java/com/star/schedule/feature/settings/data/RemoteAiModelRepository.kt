package com.star.schedule.feature.settings.data

import com.star.schedule.BuildConfig
import com.star.schedule.core.network.ApiClient
import com.star.schedule.core.network.ApiError
import com.star.schedule.core.network.ApiJson
import com.star.schedule.core.network.ApiRequest
import com.star.schedule.core.network.ApiResult
import com.star.schedule.core.network.ApiUrlValidator
import com.star.schedule.core.network.RetryPolicy
import com.star.schedule.core.telemetry.Telemetry
import com.star.schedule.feature.settings.domain.AiModel
import com.star.schedule.feature.settings.domain.AiReasoning
import com.star.schedule.feature.settings.domain.AiReasoningEffort
import com.star.schedule.feature.settings.domain.AiReasoningMode
import com.star.schedule.feature.settings.domain.AiModelCatalog
import com.star.schedule.feature.settings.domain.AiModelRepository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 从后端取「后台已启用」的模型列表。
 *
 * 使用启动统计共用的设备会话，令牌缺失或失效时由会话单飞重注册。
 */
class RemoteAiModelRepository internal constructor(
    private val baseUrl: String,
    clientProvider: () -> ApiClient,
) : AiModelRepository {
    constructor() : this(
        baseUrl = BuildConfig.API_BASE_URL.trim(),
        clientProvider = { Telemetry.apiClient() },
    )

    private val client by lazy(clientProvider)

    override suspend fun fetchModels(): Result<AiModelCatalog> {
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
                if (error is ApiError.Http &&
                    error.status == HTTP_SERVICE_UNAVAILABLE &&
                    error.code == "AI_NOT_CONFIGURED"
                ) {
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
        AiModel(alias = alias, displayName = displayName, reasoning = dto.reasoning.toDomain())
    },
    defaultAlias = defaultAlias,
)

/**
 * `reasoning` 的解析一律走 domain 的 `fromWire`：
 * 认不出的 mode 当 Unknown（猜成 Effort 会放开一个必然 400 的开关），认不出的档位直接丢掉。
 */
private fun AiReasoningDto?.toDomain(): AiReasoning {
    if (this == null) return AiReasoning()
    return AiReasoning(
        mode = AiReasoningMode.fromWire(mode),
        efforts = efforts.mapNotNull(AiReasoningEffort::fromWire).distinct(),
        defaultEffort = AiReasoningEffort.fromWire(defaultEffort),
    )
}

@Serializable
private data class AiModelListDto(
    val items: List<AiModelDto>,
    @SerialName("default") val defaultAlias: String = "",
)

@Serializable
private data class AiModelDto(
    @SerialName("alias") val alias: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("reasoning") val reasoning: AiReasoningDto? = null,
)

@Serializable
private data class AiReasoningDto(
    @SerialName("mode") val mode: String? = null,
    @SerialName("efforts") val efforts: List<String> = emptyList(),
    @SerialName("default") val defaultEffort: String? = null,
)
