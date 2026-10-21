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
 * 色值全部来自 WakeUp 官方 `ic_launcher_foreground.xml`：
 *
 * | 用途 | 色值 |
 * | --- | --- |
 * | 主体珊瑚粉 | `#FF6C6C` |
 * | 亮部 | `#FF9292` |
 * | 暗部（鹿角阴影） | `#C16363` |
 * | 眼白 | `#FFFFFF` |
 * | 瞳孔 / 描边 | `#000000` |
 *
 * 关键规律：这三个粉色的 G 与 B **完全相等**（hue = 0°），粉感来自亮度而不是色相偏移。
 * 手调色值很容易让 G > B 而偏橙，一旦偏橙整页就会从「粉」变成「脏橘」——所以下面
 * 所有衍生色都遵守 G == B，浅色 tint 用 [BrandBright] 往白色方向混，深色用 [BrandMain] 压暗。
 *
 * 只在本页生效，出了这页回到应用自己的配色。
 */
@Composable
fun wakeUpImportColorScheme(base: ColorScheme = MaterialTheme.colorScheme): ColorScheme {
    // 用 surface 的亮度判明暗，比读 isSystemInDarkTheme 稳：主题可能被外部强行指定。
    val dark = base.surface.luminance() < 0.5f
    return remember(base, dark) { if (dark) base.wakeUpDark() else base.wakeUpLight() }
}

/** 官方主体色，也是按钮底色。白箭头上 2.76:1，对 32dp 的实心图形够看。 */
private val BrandMain = Color(0xFFFF6C6C)

/** 官方亮部色，用作浅色 tint 的基底。 */
private val BrandBright = Color(0xFFFF9292)

/**
 * 浅色：底色是从 [BrandBright] 往白色里掺出来的粉白，重点色用官方主体色。
 *
 * 没有为了对比度去加深按钮——加深必然改变色相方向，颜色就不像 WakeUp 了。
 */
private fun ColorScheme.wakeUpLight() = copy(
    primary = BrandMain,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDADA),
    onPrimaryContainer = Color(0xFF5A1616),
    surface = Color(0xFFFFF8F8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFBFB),
    // 页面底色
    surfaceContainer = Color(0xFFFFF2F2),
    surfaceContainerHigh = Color(0xFFFFE7E7),
    // 返回按钮取它再叠 60% 透明度
    surfaceContainerHighest = Color(0xFFFFDCDC),
    onSurface = Color(0xFF2E1616),
    onSurfaceVariant = Color(0xFF6E4A4A),
    outline = Color(0xFFB58A8A),
    outlineVariant = Color(0xFFFFC4C4),
)

/**
 * 深色：重点色换成官方亮部 `#FF9292`（深底上 8:1 以上），
 * 底色是压暗的暖粉黑，不是纯灰——否则粉色在暗色下会显得发脏。
 */
private fun ColorScheme.wakeUpDark() = copy(
    primary = BrandBright,
    onPrimary = Color(0xFF4A0F0F),
    primaryContainer = Color(0xFF8A3434),
    onPrimaryContainer = Color(0xFFFFDADA),
    surface = Color(0xFF190F0F),
    surfaceContainerLowest = Color(0xFF130B0B),
    surfaceContainerLow = Color(0xFF211414),
    surfaceContainer = Color(0xFF281919),
    surfaceContainerHigh = Color(0xFF332222),
    surfaceContainerHighest = Color(0xFF3D2A2A),
    onSurface = Color(0xFFF7E2E2),
    onSurfaceVariant = Color(0xFFDDBFBF),
    outline = Color(0xFFA67C7C),
    outlineVariant = Color(0xFF4F3535),
)
