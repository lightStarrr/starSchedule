package com.star.schedule.platform.systembar

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.pow

internal class StatusBarAppearanceMonitor(
    private val window: Window,
    private val sampleIntervalMillis: Long,
    private val contrastSelector: StatusBarContrastSelector = StatusBarContrastSelector(),
) {
    private var currentMode: StatusBarIconMode? = null
    private val pixelCopyHandler = Handler(Looper.getMainLooper())

    init {
        require(sampleIntervalMillis > 0)
    }

    suspend fun observe() {
        while (currentCoroutineContext().isActive) {
            updateAppearance()
            delay(sampleIntervalMillis)
        }
    }

    private suspend fun updateAppearance() {
        val decorView = window.decorView
        if (!decorView.isLaidOut || decorView.width <= 0) return

        val statusBarHeight = ViewCompat.getRootWindowInsets(decorView)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())
            ?.top
            ?: return
        if (statusBarHeight <= 0) return

        val luminance = sampleLuminance(statusBarHeight) ?: return
        val nextMode = contrastSelector.select(luminance, currentMode)
        if (nextMode == currentMode) return

        WindowCompat.getInsetsController(window, decorView).isAppearanceLightStatusBars =
            nextMode == StatusBarIconMode.DarkIcons
        currentMode = nextMode
    }

    private suspend fun sampleLuminance(statusBarHeight: Int): Double? {
        val decorView = window.decorView
        val bitmap = Bitmap.createBitmap(
            SAMPLE_COLUMN_COUNT,
            SAMPLE_ROW_COUNT,
            Bitmap.Config.ARGB_8888,
        )

        return suspendCancellableCoroutine { continuation ->
            var completed = false

            fun finish(result: Int) {
                if (completed) return
                completed = true

                val luminance = if (
                    result == PixelCopy.SUCCESS && !bitmap.isRecycled
                ) {
                    val pixels = IntArray(SAMPLE_COLUMN_COUNT * SAMPLE_ROW_COUNT)
                    bitmap.getPixels(
                        pixels,
                        0,
                        SAMPLE_COLUMN_COUNT,
                        0,
                        0,
                        SAMPLE_COLUMN_COUNT,
                        SAMPLE_ROW_COUNT,
                    )
                    averageLuminance(pixels)
                } else {
                    null
                }
                if (!bitmap.isRecycled) bitmap.recycle()
                if (continuation.isActive) continuation.resume(luminance)
            }

            continuation.invokeOnCancellation {
                completed = true
                if (!bitmap.isRecycled) bitmap.recycle()
            }

            runCatching {
                PixelCopy.request(
                    window,
                    Rect(0, 0, decorView.width, statusBarHeight),
                    bitmap,
                    ::finish,
                    pixelCopyHandler,
                )
            }.onFailure {
                finish(PixelCopy.ERROR_UNKNOWN)
            }
        }
    }

    private fun averageLuminance(pixels: IntArray): Double? {
        var totalLuminance = 0.0
        var sampledPixelCount = 0

        for (pixel in pixels) {
            val alpha = pixel ushr 24 and 0xFF
            if (alpha == 0) continue

            val red = pixel ushr 16 and 0xFF
            val green = pixel ushr 8 and 0xFF
            val blue = pixel and 0xFF
            totalLuminance += relativeLuminance(red, green, blue)
            sampledPixelCount++
        }

        return if (sampledPixelCount == 0) {
            null
        } else {
            totalLuminance / sampledPixelCount
        }
    }

    private fun relativeLuminance(
        red: Int,
        green: Int,
        blue: Int,
    ): Double {
        val linearRed = linearizeColorChannel(red)
        val linearGreen = linearizeColorChannel(green)
        val linearBlue = linearizeColorChannel(blue)
        return 0.2126 * linearRed + 0.7152 * linearGreen + 0.0722 * linearBlue
    }

    private fun linearizeColorChannel(channel: Int): Double {
        val normalized = channel / 255.0
        return if (normalized <= 0.04045) {
            normalized / 12.92
        } else {
            ((normalized + 0.055) / 1.055).pow(2.4)
        }
    }

    private companion object {
        const val SAMPLE_COLUMN_COUNT = 48
        const val SAMPLE_ROW_COUNT = 6
    }
}
