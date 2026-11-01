package com.star.schedule.feature.settings.presentation

/**
 * 「服务商」分组里的行。常驻的只有服务商，额度跟着「是否选中云端」一起出现。
 *
 * 模型不在这里——它是单独一个分组，放在服务商分组下面，不参与这三者的分段圆角。
 */
internal enum class AiSettingsRow {
    Provider,
    Quota,
}

internal data class AiSettingsRowPosition(val row: AiSettingsRow, val index: Int, val count: Int)

internal fun aiSettingsRows(cloudSelected: Boolean): List<AiSettingsRow> = buildList {
    add(AiSettingsRow.Provider)
    if (cloudSelected) add(AiSettingsRow.Quota)
}

internal fun List<AiSettingsRow>.positions(): List<AiSettingsRowPosition> =
    mapIndexed { index, row -> AiSettingsRowPosition(row, index, size) }
