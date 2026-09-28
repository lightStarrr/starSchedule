package com.star.schedule.core.designsystem.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.star.schedule.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StarScheduleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seedColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        seedColor != null && darkTheme -> photoDarkColorScheme(seedColor)
        seedColor != null -> photoLightColorScheme(seedColor)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

@Composable
fun rememberImageSeedColor(@DrawableRes imageResId: Int?): Color? {
    val context = LocalContext.current
    val color by produceState<Color?>(initialValue = null, imageResId) {
        value = imageResId?.let { resourceId ->
            withContext(Dispatchers.Default) { extractSeedColor(context, resourceId) }
        }
    }
    return color
}

private fun extractSeedColor(context: android.content.Context, @DrawableRes resourceId: Int): Color? {
    val source = BitmapFactory.decodeResource(context.resources, resourceId) ?: return null
    val sample = Bitmap.createScaledBitmap(source, 32, 32, true)
    if (sample !== source) source.recycle()

    var red = 0L
    var green = 0L
    var blue = 0L
    var count = 0L
    for (y in 0 until sample.height) {
        for (x in 0 until sample.width) {
            val pixel = sample.getPixel(x, y)
            val alpha = pixel ushr 24 and 0xff
            if (alpha < 180) continue
            red += pixel ushr 16 and 0xff
            green += pixel ushr 8 and 0xff
            blue += pixel and 0xff
            count++
        }
    }
    sample.recycle()
    if (count == 0L) return null
    return Color(
        red = (red / count).toInt(),
        green = (green / count).toInt(),
        blue = (blue / count).toInt(),
    )
}

private fun photoLightColorScheme(seed: Color) = lightColorScheme(
    primary = seed.adjustLightness(0.34f),
    onPrimary = seed.adjustLightness(0.98f),
    primaryContainer = seed.adjustLightness(0.88f),
    onPrimaryContainer = seed.adjustLightness(0.16f),
    secondary = seed.adjustSaturation(0.65f).adjustLightness(0.40f),
    onSecondary = seed.adjustLightness(0.98f),
    secondaryContainer = seed.adjustLightness(0.90f),
    onSecondaryContainer = seed.adjustLightness(0.18f),
    surface = seed.adjustLightness(0.97f),
    surfaceContainerLowest = seed.adjustLightness(0.985f),
    surfaceContainer = seed.adjustLightness(0.93f),
    onSurface = seed.adjustLightness(0.12f),
    onSurfaceVariant = seed.adjustLightness(0.30f),
)

private fun photoDarkColorScheme(seed: Color) = darkColorScheme(
    primary = seed.adjustLightness(0.78f),
    onPrimary = seed.adjustLightness(0.18f),
    primaryContainer = seed.adjustLightness(0.30f),
    onPrimaryContainer = seed.adjustLightness(0.90f),
    secondary = seed.adjustSaturation(0.65f).adjustLightness(0.72f),
    onSecondary = seed.adjustLightness(0.16f),
    secondaryContainer = seed.adjustLightness(0.28f),
    onSecondaryContainer = seed.adjustLightness(0.90f),
    surface = seed.adjustLightness(0.08f),
    surfaceContainerLowest = seed.adjustLightness(0.05f),
    surfaceContainer = seed.adjustLightness(0.13f),
    onSurface = seed.adjustLightness(0.93f),
    onSurfaceVariant = seed.adjustLightness(0.78f),
)

private fun Color.adjustLightness(target: Float): Color {
    val hsl = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsl)
    val saturation = min(1f, max(0.12f, hsl[1]))
    return Color.hsl(hsl[0], saturation, target)
}

private fun Color.adjustSaturation(multiplier: Float): Color {
    val hsl = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsl)
    return Color.hsl(hsl[0], (hsl[1] * multiplier).coerceIn(0.18f, 0.90f), hsl[2])
}
