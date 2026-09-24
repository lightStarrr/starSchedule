package com.star.schedule.platform.systembar

internal enum class StatusBarIconMode {
    DarkIcons,
    LightIcons,
}

internal class StatusBarContrastSelector(
    private val switchToDarkIconsAt: Double = 0.62,
    private val switchToLightIconsAt: Double = 0.42,
) {
    init {
        require(switchToLightIconsAt in 0.0..1.0)
        require(switchToDarkIconsAt in 0.0..1.0)
        require(switchToLightIconsAt < switchToDarkIconsAt)
    }

    fun select(
        backgroundLuminance: Double,
        currentMode: StatusBarIconMode?,
    ): StatusBarIconMode {
        val luminance = backgroundLuminance.coerceIn(0.0, 1.0)

        return when (currentMode) {
            StatusBarIconMode.DarkIcons -> {
                if (luminance < switchToLightIconsAt) {
                    StatusBarIconMode.LightIcons
                } else {
                    StatusBarIconMode.DarkIcons
                }
            }

            StatusBarIconMode.LightIcons -> {
                if (luminance > switchToDarkIconsAt) {
                    StatusBarIconMode.DarkIcons
                } else {
                    StatusBarIconMode.LightIcons
                }
            }

            null -> {
                if (luminance >= INITIAL_LUMINANCE_THRESHOLD) {
                    StatusBarIconMode.DarkIcons
                } else {
                    StatusBarIconMode.LightIcons
                }
            }
        }
    }

    private companion object {
        const val INITIAL_LUMINANCE_THRESHOLD = 0.5
    }
}
