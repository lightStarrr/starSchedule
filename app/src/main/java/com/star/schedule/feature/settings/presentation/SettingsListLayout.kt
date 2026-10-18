package com.star.schedule.feature.settings.presentation

internal enum class SettingsRow {
    WakeUp,
    Reminder,
    InstantTest,
    DelayedTest,
}

internal data class SettingsRowPosition(val row: SettingsRow, val index: Int, val count: Int)

/**
 * 通知分组里常驻的行。课前提醒的具体选项已经搬到独立的 ReminderActivity，
 * 这里只保留一行入口，因此不再有随开关展开/收起而变化的行。
 */
internal fun notificationRows(wakeUpAvailable: Boolean): List<SettingsRow> = buildList {
    if (wakeUpAvailable) add(SettingsRow.WakeUp)
    add(SettingsRow.Reminder)
    add(SettingsRow.InstantTest)
    add(SettingsRow.DelayedTest)
}

internal fun List<SettingsRow>.positions(): List<SettingsRowPosition> =
    mapIndexed { index, row -> SettingsRowPosition(row, index, size) }
