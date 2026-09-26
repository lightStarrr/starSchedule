package com.star.schedule.notification

import com.star.schedule.R

enum class FlymeLiveTemplate(
    val prefValue: String,
    val ongoingLayout: Int,
    val finishedLayout: Int
) {
    Classic(
        prefValue = "classic",
        ongoingLayout = R.layout.live_notification_card,
        finishedLayout = R.layout.live_notification_card_ok
    ),
    Compact(
        prefValue = "compact",
        ongoingLayout = R.layout.live_notification_card_1,
        finishedLayout = R.layout.live_notification_card_ok_1
    );

    companion object {
        fun fromPref(value: String?): FlymeLiveTemplate {
            return entries.firstOrNull { it.prefValue == value } ?: Classic
        }
    }
}
