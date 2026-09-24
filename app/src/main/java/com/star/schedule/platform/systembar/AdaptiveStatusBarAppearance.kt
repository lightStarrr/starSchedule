package com.star.schedule.platform.systembar

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

internal fun ComponentActivity.installAdaptiveStatusBarAppearance(
    sampleIntervalMillis: Long = DEFAULT_SAMPLE_INTERVAL_MILLIS,
) {
    val monitor = StatusBarAppearanceMonitor(
        window = window,
        sampleIntervalMillis = sampleIntervalMillis,
    )

    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            monitor.observe()
        }
    }
}

internal const val DEFAULT_SAMPLE_INTERVAL_MILLIS = 100L
