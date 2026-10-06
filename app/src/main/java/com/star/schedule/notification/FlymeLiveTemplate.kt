package com.star.schedule.notification

import com.star.schedule.R

enum class FlymeLiveTemplate(
    val prefValue: String,
    val ongoingLayout: Int,
    val finishedLayout: Int,
    val titleRes: Int,
    val descriptionRes: Int,
) {
    Classic(
        prefValue = "classic",
        ongoingLayout = R.layout.live_notification_card,
        finishedLayout = R.layout.live_notification_card_ok,
        titleRes = R.string.flyme_template_title_classic,
        descriptionRes = R.string.flyme_template_desc_classic,
    ),
    Compact(
        prefValue = "compact",
        ongoingLayout = R.layout.live_notification_card_1,
        finishedLayout = R.layout.live_notification_card_ok_1,
        titleRes = R.string.flyme_template_title_compact,
        descriptionRes = R.string.flyme_template_desc_compact,
    );

    companion object {
        fun fromPref(value: String?): FlymeLiveTemplate {
            return entries.firstOrNull { it.prefValue == value } ?: Classic
        }
    }
}
