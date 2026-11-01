package com.star.schedule.feature.settings.domain

/**
 * 模型的思考能力。
 *
 * 服务端每个模型带一条 `reasoning`：`effort` 可调档位、`fixed` 固定带思考、`none` 不支持、
 * `unknown` 尚未检测。认不出的取值一律当 [Unknown]——绝不能猜成 [Effort]，
 * 否则会放开一个「传了必然 400」的开关。
 */
enum class AiReasoningMode {
    Unknown,
    None,
    Effort,
    Fixed,
    ;

    companion object {
        fun fromWire(value: String?): AiReasoningMode = when (value) {
            "none" -> None
            "effort" -> Effort
            "fixed" -> Fixed
            else -> Unknown
        }
    }
}

/** 思考强度档位。[wireValue] 就是请求体里 `reasoning_effort` 的取值。 */
enum class AiReasoningEffort(val wireValue: String) {
    Minimal("minimal"),
    Low("low"),
    Medium("medium"),
    High("high"),
    ;

    companion object {
        fun fromWire(value: String?): AiReasoningEffort? =
            entries.firstOrNull { it.wireValue == value }
    }
}

data class AiReasoning(
    val mode: AiReasoningMode = AiReasoningMode.Unknown,
    /** 保持服务端给的顺序、去重后的集合：只给两档的模型就只有两项。 */
    val efforts: List<AiReasoningEffort> = emptyList(),
    /**
     * 服务端配置的全局默认档位，已按本模型的档位裁剪过——也就是不传档位时**真正会下发**的值。
     * 只有 [AiReasoningMode.Effort] 下才有意义；为空表示不会下发该字段。
     */
    val defaultEffort: AiReasoningEffort? = null,
) {
    /** 能不能让用户选档位。`effort` 但没给任何档位属于异常数据，按不支持处理。 */
    val isAdjustable: Boolean
        get() = mode == AiReasoningMode.Effort && efforts.isNotEmpty()
}

/**
 * 云端可用的模型。
 *
 * 只保留选择器用得到的字段：菜单显示 [displayName]，落库与请求用 [alias]。
 * `context_window` 不展示、`supports_stream` 服务端恒为 true，收进来只会变成死字段。
 */
data class AiModel(
    val alias: String,
    val displayName: String,
    val reasoning: AiReasoning = AiReasoning(),
)

data class AiModelCatalog(
    val models: List<AiModel> = emptyList(),
    /** 服务端当前默认模型的 alias，可能为空串。 */
    val defaultAlias: String = "",
) {
    val isEmpty: Boolean
        get() = models.isEmpty()

    fun find(alias: String?): AiModel? =
        alias?.takeIf { it.isNotEmpty() }?.let { key -> models.firstOrNull { it.alias == key } }

    /**
     * 服务端默认模型。
     *
     * `default` 可能是空串（默认模型在两次查询之间被关闭），也可能指向已下架的 alias（本地还是旧列表），
     * 两种情况都退回第一条：宁可显示一个真实存在的名字，也不要让界面空着。
     */
    val defaultModel: AiModel?
        get() = find(defaultAlias) ?: models.firstOrNull()
}

interface AiModelRepository {
    /** 拉取后台已启用的模型；服务端一个都没配时返回空目录，而不是失败。 */
    suspend fun fetchModels(): Result<AiModelCatalog>
}
