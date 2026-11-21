package com.star.schedule.feature.wallpaper.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import com.star.schedule.core.designsystem.navigationBarBottomInset

@Composable
internal fun SolidWallpaperColors(
    selectedArgb: Int,
    onSelectColor: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val colors = remember(context, isDark) {
        val system = if (isDark) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
        listOf(
            system.primaryContainer, system.secondaryContainer, system.tertiaryContainer,
            system.primary, system.secondary, system.tertiary, Color.White, Color.Black,
        ).distinct()
    }
    Surface(
        modifier = modifier.padding(horizontal = 16.dp)
            .padding(bottom = 16.dp + navigationBarBottomInset()),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            colors.forEachIndexed { index, color ->
                val label = stringResource(R.string.wallpaper_color_option, index + 1)
                val isSelected = selectedArgb == color.toArgb()
                Surface(
                    onClick = { onSelectColor(color.toArgb()) },
                    shape = CircleShape,
                    color = color,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.size(48.dp).semantics {
                        contentDescription = label
                        selected = isSelected
                    },
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}
