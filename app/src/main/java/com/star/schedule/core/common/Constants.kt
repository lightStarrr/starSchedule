package com.star.schedule.core.common

object Constants {
    const val PREF_CURRENT_TIMETABLE = "current_timetable"
    const val PREF_REMINDER_ENABLED_TIMETABLE = "reminder_enabled_timetable"
    const val PREF_WAKEUP_SIMULATION_ENABLED = "wakeup_simulation_enabled"
    const val PREF_NOTIFY_ONLY_FOR_FIRST_CONTINUOUS_CLASS = "notify_only_for_first_continuous_class"
    const val PREF_LIVE_CAPSULE_BG_COLOR = "live_capsule_bg_color"
    const val PREF_LIVE_CAPSULE_ICON_PATH = "live_capsule_icon_path"
    const val PREF_FLYME_LIVE_TEMPLATE = "flyme_live_template"
    const val PREF_HIDE_FROM_RECENTS = "hide_from_recents"
    const val PREF_STARTUP_HINT_CLOSED = "startup_hint_closed"
    const val PREF_TIMETABLE_WALLPAPER_PREFIX = "timetable_wallpaper_"

    fun timetableWallpaperPreference(timetableId: Long): String =
        "$PREF_TIMETABLE_WALLPAPER_PREFIX$timetableId"
}
