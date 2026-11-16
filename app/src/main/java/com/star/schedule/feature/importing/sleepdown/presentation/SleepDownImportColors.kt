package com.star.schedule.feature.importing.sleepdown.presentation

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * SleepDown 导入页的品牌配色，取自看板娘的薰衣草紫。
 * 只在本页生效，出了这页回到应用自己的配色。
 */
@Composable
fun sleepDownImportColorScheme(base: ColorScheme = MaterialTheme.colorScheme): ColorScheme {
    val dark = base.surface.luminance() < 0.5f
    return remember(base, dark) { if (dark) base.sleepDownDark() else base.sleepDownLight() }
}

private fun ColorScheme.sleepDownLight() = copy(
    primary = Color(0xFF7C5CC4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBDDFF),
    onPrimaryContainer = Color(0xFF2E1760),
    surface = Color(0xFFFCF8FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF8FF),
    surfaceContainer = Color(0xFFF4EEFC),
    surfaceContainerHigh = Color(0xFFEDE4FA),
    surfaceContainerHighest = Color(0xFFE6DCF5),
    onSurface = Color(0xFF1E1A26),
    onSurfaceVariant = Color(0xFF4C4459),
    outline = Color(0xFF7D7390),
    outlineVariant = Color(0xFFD4C8EA),
)

private fun ColorScheme.sleepDownDark() = copy(
    primary = Color(0xFFCDB8FF),
    onPrimary = Color(0xFF3A2470),
    primaryContainer = Color(0xFF5A3F99),
    onPrimaryContainer = Color(0xFFEBDDFF),
    surface = Color(0xFF141119),
    surfaceContainerLowest = Color(0xFF0F0C14),
    surfaceContainerLow = Color(0xFF1C1823),
    surfaceContainer = Color(0xFF221E2A),
    surfaceContainerHigh = Color(0xFF2C2735),
    surfaceContainerHighest = Color(0xFF373140),
    onSurface = Color(0xFFE9E1F2),
    onSurfaceVariant = Color(0xFFCEC3DE),
    outline = Color(0xFF978CA8),
    outlineVariant = Color(0xFF4A4358),
)
