package com.star.schedule.platform.display

import android.os.Build
import android.view.RoundedCorner
import android.view.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 设备屏幕的圆角半径（dp）。取不到时返回 0dp，调用方自己兜底。
 *
 * Android 12（API 31）起可以从 [WindowInsets.getRoundedCorner] 读到每个角的半径和圆心；
 * 不支持的机型会返回 null 或抛异常，这里统一吞掉退回 0。
 */
@Composable
fun rememberScreenCornerRadius(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    var radiusPx by remember { mutableIntStateOf(0) }

    // 布局完成后再读 insets；旋转/分屏时按新的屏幕尺寸重来一次。
    LaunchedEffect(view, configuration.screenWidthDp, configuration.screenHeightDp) {
        withFrameNanos { }
        radiusPx = view.rootWindowInsets?.screenCornerRadiusPx() ?: 0
    }

    return with(density) { radiusPx.toDp() }
}

private fun WindowInsets.screenCornerRadiusPx(): Int {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0
    // IntArray 没有 mapNotNull，先 map 成 List<RoundedCorner?> 再挑半径。
    return SCREEN_CORNERS
        .map { position -> runCatching { getRoundedCorner(position) }.getOrNull() }
        .mapNotNull { corner -> corner?.radius }
        .filter { radius -> radius > 0 }
        .maxOrNull()
        ?: 0
}

private val SCREEN_CORNERS = intArrayOf(
    RoundedCorner.POSITION_TOP_LEFT,
    RoundedCorner.POSITION_TOP_RIGHT,
    RoundedCorner.POSITION_BOTTOM_LEFT,
    RoundedCorner.POSITION_BOTTOM_RIGHT,
)
