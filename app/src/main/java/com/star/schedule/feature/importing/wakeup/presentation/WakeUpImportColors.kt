package com.star.schedule.feature.importing.wakeup.presentation

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * WakeUp 导入页的品牌配色。
 *
 * 颜色取自 WakeUp 官方图标：主体珊瑚粉 `#FF9292`、深部 `#FF6C6C`、暗角 `#C16363`。
 * 只在本页生效，不动全局主题——导入页是别人的品牌，出了这页就回到应用自己的配色。
 *
 * 只在 [base] 上覆盖用得到的槽位，其余（secondary/tertiary/error 等）沿用当前主题，
 * 这样动态取色开了也不会跟本页的粉色打架。
 */
@Composable
fun wakeUpImportColorScheme(base: ColorScheme = MaterialTheme.colorScheme): ColorScheme {
    // 用 surface 的亮度判明暗，比读 isSystemInDarkTheme 稳：主题可能被外部强行指定。
    val dark = base.surface.luminance() < 0.5f
    return remember(base, dark) { if (dark) base.wakeUpDark() else base.wakeUpLight() }
}

/** 图标里最深的那档珊瑚粉，白箭头压上去是 3.47:1，够图形用的 3:1。 */
private val CoralDeep = Color(0xFFE85B55)

/**
 * 浅色：页面底色是掺了一点粉的米白，控件底色再深一档，重点色用珊瑚粉。
 *
 * 不能直接拿图标原色 `#FF6C6C` 当按钮底色：白箭头对它只有 2.76:1，压不住。
 */
private fun ColorScheme.wakeUpLight() = copy(
    primary = CoralDeep,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410001),
    surface = Color(0xFFFFF8F7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF3F1),
    // 页面底色
    surfaceContainer = Color(0xFFFFEDEB),
    surfaceContainerHigh = Color(0xFFFAE1DE),
    // 返回按钮的 60% 容器底
    surfaceContainerHighest = Color(0xFFF4D8D5),
    onSurface = Color(0xFF2A1614),
    onSurfaceVariant = Color(0xFF6B4B47),
    outline = Color(0xFFB08A86),
    outlineVariant = Color(0xFFE6C4C0),
)

/**
 * 深色：把珊瑚粉提亮到 `#FFB4AC` 当重点色，底色用很暗的暖棕红，
 * 避免整页发灰、也避免用高饱和粉做大面积底色晃眼。
 */
private fun ColorScheme.wakeUpDark() = copy(
    primary = Color(0xFFFFB4AC),
    onPrimary = Color(0xFF5C1410),
    primaryContainer = Color(0xFF8A2C26),
    onPrimaryContainer = Color(0xFFFFDAD5),
    surface = Color(0xFF1A1211),
    surfaceContainerLowest = Color(0xFF140D0C),
    surfaceContainerLow = Color(0xFF201615),
    surfaceContainer = Color(0xFF251A19),
    surfaceContainerHigh = Color(0xFF302322),
    surfaceContainerHighest = Color(0xFF3A2C2B),
    onSurface = Color(0xFFF5DEDC),
    onSurfaceVariant = Color(0xFFD9BDB9),
    outline = Color(0xFF9C8480),
    outlineVariant = Color(0xFF4E3836),
)
