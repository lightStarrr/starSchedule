package com.star.schedule.feature.settings.presentation

import androidx.annotation.StringRes
import com.star.schedule.R
import com.star.schedule.feature.settings.domain.AiReasoningEffort

/**
 * 思考强度的文案映射。
 *
 * 单独放一份纯映射（不含 Compose）：菜单项与行的 supporting 都要用同一套标签，
 * 两边各写一份 when 迟早会不一致。四档固定为「极简 / 低 / 中 / 高」，
 * 与 `reasoning_effort` 的取值一一对应——某个模型只给两档时也不会出现「标准档不见了」的困惑。
 */
@get:StringRes
internal val AiReasoningEffort.labelRes: Int
    get() = when (this) {
        AiReasoningEffort.Minimal -> R.string.ai_reasoning_effort_minimal
        AiReasoningEffort.Low -> R.string.ai_reasoning_effort_low
        AiReasoningEffort.Medium -> R.string.ai_reasoning_effort_medium
        AiReasoningEffort.High -> R.string.ai_reasoning_effort_high
    }

/** 思考强度行的四种「不可调」表现，UI 只做「状态 → 文案」映射。 */
enum class AiReasoningAvailability {
    /** 模型列表还在读。 */
    Loading,

    /** 拉取失败、或无法确定当前模型、或服务端标记为 unknown。 */
    Unknown,

    /** 模型固定带思考，传档位会被服务端拒绝。 */
    Fixed,

    /** 模型完全不支持思考强度。 */
    Unsupported,

    /** 可选档位。 */
    Adjustable,
}

@get:StringRes
internal val AiReasoningAvailability.supportingRes: Int
    get() = when (this) {
        AiReasoningAvailability.Loading -> R.string.ai_model_loading
        AiReasoningAvailability.Unknown -> R.string.ai_reasoning_unknown
        AiReasoningAvailability.Fixed -> R.string.ai_reasoning_fixed
        AiReasoningAvailability.Unsupported -> R.string.ai_reasoning_unsupported
        AiReasoningAvailability.Adjustable -> R.string.ai_reasoning_follow_default
    }
