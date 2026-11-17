@file:SuppressLint("RestrictedApi")

package com.star.schedule.core.designsystem.theme

import android.annotation.SuppressLint
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.google.android.material.color.utilities.DynamicColor
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot

/**
 * 用官方 Material Color Utilities 从壁纸种子色生成完整的 Compose [ColorScheme]。
 *
 * color.utilities 包里的类都标了 @RestrictTo(LIBRARY_GROUP)：Google 没有单独发布
 * MCU 的 Java 库，官方实现只随 MDC 发行。所有调用集中在这一个文件，
 * 升级 com.google.android.material:material 时只需要检查这里的 API 是否变动。
 */
internal fun seedColorScheme(seedArgb: Int, isDark: Boolean): ColorScheme {
    // TonalSpot 与系统「壁纸与风格」的默认取色一致；contrast 0 为标准对比度。
    val scheme = SchemeTonalSpot(Hct.fromInt(seedArgb), isDark, 0.0)
    val colors = MaterialDynamicColors()

    fun argb(color: DynamicColor) = Color(color.getArgb(scheme))

    return if (isDark) {
        darkColorScheme(
            primary = argb(colors.primary()),
            onPrimary = argb(colors.onPrimary()),
            primaryContainer = argb(colors.primaryContainer()),
            onPrimaryContainer = argb(colors.onPrimaryContainer()),
            inversePrimary = argb(colors.inversePrimary()),
            secondary = argb(colors.secondary()),
            onSecondary = argb(colors.onSecondary()),
            secondaryContainer = argb(colors.secondaryContainer()),
            onSecondaryContainer = argb(colors.onSecondaryContainer()),
            tertiary = argb(colors.tertiary()),
            onTertiary = argb(colors.onTertiary()),
            tertiaryContainer = argb(colors.tertiaryContainer()),
            onTertiaryContainer = argb(colors.onTertiaryContainer()),
            background = argb(colors.background()),
            onBackground = argb(colors.onBackground()),
            surface = argb(colors.surface()),
            onSurface = argb(colors.onSurface()),
            surfaceVariant = argb(colors.surfaceVariant()),
            onSurfaceVariant = argb(colors.onSurfaceVariant()),
            surfaceTint = argb(colors.surfaceTint()),
            inverseSurface = argb(colors.inverseSurface()),
            inverseOnSurface = argb(colors.inverseOnSurface()),
            error = argb(colors.error()),
            onError = argb(colors.onError()),
            errorContainer = argb(colors.errorContainer()),
            onErrorContainer = argb(colors.onErrorContainer()),
            outline = argb(colors.outline()),
            outlineVariant = argb(colors.outlineVariant()),
            scrim = argb(colors.scrim()),
            surfaceBright = argb(colors.surfaceBright()),
            surfaceDim = argb(colors.surfaceDim()),
            surfaceContainer = argb(colors.surfaceContainer()),
            surfaceContainerHigh = argb(colors.surfaceContainerHigh()),
            surfaceContainerHighest = argb(colors.surfaceContainerHighest()),
            surfaceContainerLow = argb(colors.surfaceContainerLow()),
            surfaceContainerLowest = argb(colors.surfaceContainerLowest()),
            primaryFixed = argb(colors.primaryFixed()),
            primaryFixedDim = argb(colors.primaryFixedDim()),
            onPrimaryFixed = argb(colors.onPrimaryFixed()),
            onPrimaryFixedVariant = argb(colors.onPrimaryFixedVariant()),
            secondaryFixed = argb(colors.secondaryFixed()),
            secondaryFixedDim = argb(colors.secondaryFixedDim()),
            onSecondaryFixed = argb(colors.onSecondaryFixed()),
            onSecondaryFixedVariant = argb(colors.onSecondaryFixedVariant()),
            tertiaryFixed = argb(colors.tertiaryFixed()),
            tertiaryFixedDim = argb(colors.tertiaryFixedDim()),
            onTertiaryFixed = argb(colors.onTertiaryFixed()),
            onTertiaryFixedVariant = argb(colors.onTertiaryFixedVariant()),
        )
    } else {
        lightColorScheme(
            primary = argb(colors.primary()),
            onPrimary = argb(colors.onPrimary()),
            primaryContainer = argb(colors.primaryContainer()),
            onPrimaryContainer = argb(colors.onPrimaryContainer()),
            inversePrimary = argb(colors.inversePrimary()),
            secondary = argb(colors.secondary()),
            onSecondary = argb(colors.onSecondary()),
            secondaryContainer = argb(colors.secondaryContainer()),
            onSecondaryContainer = argb(colors.onSecondaryContainer()),
            tertiary = argb(colors.tertiary()),
            onTertiary = argb(colors.onTertiary()),
            tertiaryContainer = argb(colors.tertiaryContainer()),
            onTertiaryContainer = argb(colors.onTertiaryContainer()),
            background = argb(colors.background()),
            onBackground = argb(colors.onBackground()),
            surface = argb(colors.surface()),
            onSurface = argb(colors.onSurface()),
            surfaceVariant = argb(colors.surfaceVariant()),
            onSurfaceVariant = argb(colors.onSurfaceVariant()),
            surfaceTint = argb(colors.surfaceTint()),
            inverseSurface = argb(colors.inverseSurface()),
            inverseOnSurface = argb(colors.inverseOnSurface()),
            error = argb(colors.error()),
            onError = argb(colors.onError()),
            errorContainer = argb(colors.errorContainer()),
            onErrorContainer = argb(colors.onErrorContainer()),
            outline = argb(colors.outline()),
            outlineVariant = argb(colors.outlineVariant()),
            scrim = argb(colors.scrim()),
            surfaceBright = argb(colors.surfaceBright()),
            surfaceDim = argb(colors.surfaceDim()),
            surfaceContainer = argb(colors.surfaceContainer()),
            surfaceContainerHigh = argb(colors.surfaceContainerHigh()),
            surfaceContainerHighest = argb(colors.surfaceContainerHighest()),
            surfaceContainerLow = argb(colors.surfaceContainerLow()),
            surfaceContainerLowest = argb(colors.surfaceContainerLowest()),
            primaryFixed = argb(colors.primaryFixed()),
            primaryFixedDim = argb(colors.primaryFixedDim()),
            onPrimaryFixed = argb(colors.onPrimaryFixed()),
            onPrimaryFixedVariant = argb(colors.onPrimaryFixedVariant()),
            secondaryFixed = argb(colors.secondaryFixed()),
            secondaryFixedDim = argb(colors.secondaryFixedDim()),
            onSecondaryFixed = argb(colors.onSecondaryFixed()),
            onSecondaryFixedVariant = argb(colors.onSecondaryFixedVariant()),
            tertiaryFixed = argb(colors.tertiaryFixed()),
            tertiaryFixedDim = argb(colors.tertiaryFixedDim()),
            onTertiaryFixed = argb(colors.onTertiaryFixed()),
            onTertiaryFixedVariant = argb(colors.onTertiaryFixedVariant()),
        )
    }
}
