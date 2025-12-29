package com.star.schedule.notification

import com.star.schedule.R

enum class FlymeLiveTemplate(
    val prefValue: String,
    val title: String,
    val description: String,
    val ongoingLayout: Int,
    val finishedLayout: Int
) {
    Classic(
        prefValue = "classic",
        title = "默认",
        description = "标准样式",
        ongoingLayout = R.layout.live_notification_card,
        finishedLayout = R.layout.live_notification_card_ok
    ),
    Compact(
        prefValue = "compact",
        title = "紧凑",
        description = "精简布局",
        ongoingLayout = R.layout.live_notification_card_1,
        finishedLayout = R.layout.live_notification_card_ok_1
    );

    companion object {
        fun fromPref(value: String?): FlymeLiveTemplate {
            return entries.firstOrNull { it.prefValue == value } ?: Classic
        }
    }
}
