package com.star.schedule.core.designsystem.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 地球 + 右下角一颗星星，表示「在网站上用 AI 识别课表」。
 *
 * Material Icons 没有现成的组合图标。地球按 24dp 画满，星星 12dp 叠在右下角。
 *
 * 星星底下要先垫一圈 [background] 色的边，否则同色实心图形叠在一起会糊成一块。
 * 这里没有用"放大一个同位图标"来做这圈边——整体缩放会把两颗小星也朝外推，
 * 上面那颗会被推到地球轮廓外，它和球之间就再也没有可对比的背景，看着像没有间隙。
 * 改成给星星路径本身描边：路径位置不动，只把轮廓向外扩 [SparkleGap]，三颗星都在原位。
 *
 * 调用方必须传入所在容器的实际颜色，否则描边处会出现色块。
 */
@Composable
fun GlobeSparkleIcon(
    background: Color,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    sparkleTint: Color = tint,
) {
    val starPath = remember { PathParser().parsePathString(AutoAwesomePath).toPath() }

    Box(modifier = modifier.size(IconSize)) {
        Icon(
            imageVector = Icons.Rounded.Public,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(IconSize),
        )
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(SparkleSize),
        ) {
            val scale = size.width / StarViewport
            val scaled = Path().apply { addPath(starPath) }
            scaled.transform(Matrix().apply { scale(scale, scale) })
            drawPath(
                path = scaled,
                color = background,
                style = Stroke(width = SparkleGap.toPx() * 2),
            )
            drawPath(path = scaled, color = sparkleTint)
        }
    }
}

private val IconSize = 24.dp
private val SparkleSize = 12.dp
private val SparkleGap = 0.8.dp

// AutoAwesome 画在 24 的画布上，缩放到 12dp 只需要整体乘 0.5。
private const val StarViewport = 24f

// Material Icons `auto_awesome` 的标准路径：一颗大星 + 两颗小星。
private const val AutoAwesomePath =
    "M19,9l1.25,-2.75L23,5l-2.75,-1.25L19,1l-1.25,2.75L15,5l2.75,1.25L19,9z" +
        "M11.5,9.5L9,4 6.5,9.5 1,12l5.5,2.5L9,20l2.5,-5.5L17,12l-5.5,-2.5z" +
        "M19,15l-1.25,2.75L15,19l2.75,1.25L19,23l1.25,-2.75L23,19l-2.75,-1.25L19,15z"
