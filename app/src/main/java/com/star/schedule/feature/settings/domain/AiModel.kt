package com.star.schedule.feature.settings.domain

/**
 * 云端可用的模型。
 *
 * 只保留选择器用得到的字段：菜单显示 [displayName]，落库与请求用 [alias]。
 * `context_window` 不展示、`supports_stream` 服务端恒为 true，收进来只会变成死字段。
 */
data class AiModel(
    val alias: String,
    val displayName: String,
)

data class AiModelCatalog(
    val models: List<AiModel> = emptyList(),
) {
    val isEmpty: Boolean
        get() = models.isEmpty()

    fun find(alias: String?): AiModel? =
        alias?.takeIf { it.isNotEmpty() }?.let { key -> models.firstOrNull { it.alias == key } }
}

interface AiModelRepository {
    /** 拉取后台已启用的模型；服务端一个都没配时返回空目录，而不是失败。 */
    suspend fun fetchModels(): Result<AiModelCatalog>
}
