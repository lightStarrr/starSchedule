package com.star.schedule.feature.importing.sharecode.presentation

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.star.schedule.core.designsystem.color.successColors

/**
 * 导入成功时用的颜色。
 *
 * 具体的绿色取自 design system 的 [successColors]（M3 没有 success 角色），
 * 这里只是包一层口令导入页自己的类型，避免两处各写一份色值。
 */
data class ShareCodeSuccessColors(
    val container: Color,
    val content: Color,
)

@Composable
fun shareCodeSuccessColors(base: ColorScheme = MaterialTheme.colorScheme): ShareCodeSuccessColors {
    val success = successColors(base)
    return remember(success) {
        ShareCodeSuccessColors(container = success.container, content = success.content)
    }
}
