package com.star.schedule.feature.settings.presentation

internal enum class SettingsRow {
    WakeUp,
    Reminder,
    OnlyFirst,
    Icon,
    CapsuleColor,
    Template,
    InstantTest,
    DelayedTest,
}

internal data class SettingsRowPosition(val row: SettingsRow, val index: Int, val count: Int)

internal fun notificationRows(
    reminderEnabled: Boolean,
    wakeUpAvailable: Boolean,
    liveCapsuleAvailable: Boolean,
): List<SettingsRow> = buildList {
    if (wakeUpAvailable) add(SettingsRow.WakeUp)
    add(SettingsRow.Reminder)
    if (reminderEnabled) {
        add(SettingsRow.OnlyFirst)
        add(SettingsRow.Icon)
        if (liveCapsuleAvailable) {
            add(SettingsRow.CapsuleColor)
            add(SettingsRow.Template)
        }
    }
    add(SettingsRow.InstantTest)
    add(SettingsRow.DelayedTest)
}

internal fun List<SettingsRow>.positions(): List<SettingsRowPosition> =
    mapIndexed { index, row -> SettingsRowPosition(row, index, size) }

internal fun showStartupHint(closed: Boolean): Boolean = !closed
