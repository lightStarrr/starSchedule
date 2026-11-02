package com.star.schedule.core.telemetry

/** 生命周期事件可能早于 Room 偏好加载；读取隐私设置前不能注册或上报。 */
internal class TelemetryStartupGate(
    private val onProcessStart: () -> Unit,
    private val onForeground: () -> Unit,
    private val onConsentChanged: (Boolean) -> Unit,
) {
    @Volatile
    var enabled: Boolean = false
        private set

    private var consentLoaded = false
    private var pendingProcessStart = false
    private var pendingForeground = false

    fun processStarted() {
        if (!consentLoaded) pendingProcessStart = true else if (enabled) onProcessStart()
    }

    fun foregrounded() {
        if (!consentLoaded) pendingForeground = true else if (enabled) onForeground()
    }

    fun updateConsent(value: Boolean) {
        if (consentLoaded && enabled == value) return
        consentLoaded = true
        enabled = value
        onConsentChanged(value)
        if (value) {
            if (pendingProcessStart) onProcessStart()
            if (pendingForeground) onForeground()
        }
        pendingProcessStart = false
        pendingForeground = false
    }
}
