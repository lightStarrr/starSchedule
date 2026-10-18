package com.star.schedule.feature.settings.presentation

internal enum class ReminderDetailRow {
    OnlyFirst,
    Icon,
    CapsuleColor,
    Template,
}

internal data class ReminderDetailPosition(
    val row: ReminderDetailRow,
    val index: Int,
    val count: Int,
)

internal fun reminderDetailRows(liveCapsuleAvailable: Boolean): List<ReminderDetailRow> = buildList {
    add(ReminderDetailRow.OnlyFirst)
    add(ReminderDetailRow.Icon)
    if (liveCapsuleAvailable) {
        add(ReminderDetailRow.CapsuleColor)
        add(ReminderDetailRow.Template)
    }
}

internal fun List<ReminderDetailRow>.detailPositions(): List<ReminderDetailPosition> =
    mapIndexed { index, row -> ReminderDetailPosition(row, index, size) }
