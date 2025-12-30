package com.star.schedule.notification

import androidx.annotation.StringRes
import com.star.schedule.R

enum class FlymeLiveTemplate(
    val prefValue: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val ongoingLayout: Int,
    val finishedLayout: Int
) {
    Classic(
        prefValue = "classic",
        titleRes = R.string.flyme_template_title_classic,
        descriptionRes = R.string.flyme_template_desc_classic,
        ongoingLayout = R.layout.live_notification_card,
        finishedLayout = R.layout.live_notification_card_ok
    ),
    Compact(
        prefValue = "compact",
        titleRes = R.string.flyme_template_title_compact,
        descriptionRes = R.string.flyme_template_desc_compact,
        ongoingLayout = R.layout.live_notification_card_1,
        finishedLayout = R.layout.live_notification_card_ok_1
    );

    companion object {
        fun fromPref(value: String?): FlymeLiveTemplate {
            return entries.firstOrNull { it.prefValue == value } ?: Classic
        }
    }
}
