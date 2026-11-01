package com.star.schedule.core.designsystem.color

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 应用图标的品牌色，取自 M3 色板里同一色相的四档 tone：
 * 浅色主题用 tone 90 当底、tone 40 当图形，深色主题用 tone 20 当底、tone 80 当图形。
 *
 * 与自适应图标里的 `ic_launcher_background_light` / `ic_launcher_foreground_light`
 * （以及深色版）是同两个值，改图标颜色时这里要一起改。
 */
data class BrandColors(
    val container: Color,
    val content: Color,
)

/** 浅色底：tone 90。 */
private val BrandContainerLight = Color(0xFF91F1FF)

/** 浅色图形：tone 40。 */
private val BrandContentLight = Color(0xFF006973)

/** 深色底：tone 20。 */
private val BrandContainerDark = Color(0xFF00363C)

/** 深色图形：tone 80。 */
private val BrandContentDark = Color(0xFF4ED8E9)

/**
 * 和 [successColors] 一样按当前底色亮度切换，不直接读系统深浅色开关——
 * 跟随动态取色后，系统是深色但页面底色可能并不深。
 */
@Composable
fun brandColors(base: ColorScheme = MaterialTheme.colorScheme): BrandColors {
    val dark = base.surface.luminance() < 0.5f
    return remember(dark) {
        if (dark) {
            BrandColors(container = BrandContainerDark, content = BrandContentDark)
        } else {
            BrandColors(container = BrandContainerLight, content = BrandContentLight)
        }
    }
}
