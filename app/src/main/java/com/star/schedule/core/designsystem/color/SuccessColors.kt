package com.star.schedule.core.designsystem.color

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 「操作成功 / 保存」用的强调色。
 *
 * Material 3 的 `ColorScheme` 没有 success 角色（只有 primary/secondary/tertiary/error），
 * 所以单独给一组：浅色用深绿底 + 白字（6.5:1），深色用浅绿底 + 墨绿字（10:1），
 * 取法和 primary 一样按底色亮度切换，不直接读系统深浅色开关。
 */
data class SuccessColors(
    val container: Color,
    val content: Color,
)

/** Green 40，浅色主题下当底色。 */
private val SuccessLight = Color(0xFF146C2E)

/** Green 80，深色主题下当底色。 */
private val SuccessDark = Color(0xFF7FDA89)

private val SuccessOnDark = Color(0xFF002204)

@Composable
fun successColors(base: ColorScheme = MaterialTheme.colorScheme): SuccessColors {
    val dark = base.surface.luminance() < 0.5f
    return remember(dark) {
        if (dark) {
            SuccessColors(container = SuccessDark, content = SuccessOnDark)
        } else {
            SuccessColors(container = SuccessLight, content = Color(0xFFFFFFFF))
        }
    }
}
