package com.star.schedule.core.designsystem.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.star.schedule.R

/**
 * WakeUp 的小鸟 logo，单色版本。
 *
 * 原始 `ic_launcher_foreground.xml` 是 800×800、13 个 path 的多色图标，内容只占
 * viewport 的 42%（留给启动器遮罩的留白）。这里把路径重算到 24 视口、内容占 20dp，
 * 与 Material 图标一致，不再是缩在中间的一小块。
 *
 * 眼睛没有直接画进 `ic_wakeup_logo.xml`：单色剪影里同色的眼睛等于消失，而 vector
 * 的 `evenOdd` 只对单个 path 内的子路径生效，没法从多个层叠 path 里挖洞。所以眼睛
 * 在这里用 [BlendMode.Clear] 挖掉，需要先 `saveLayer` 让 Clear 只作用在图标自身。
 */
@Composable
fun WakeUpLogoIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val painter = painterResource(R.drawable.ic_wakeup_logo)
    Canvas(modifier = modifier.size(IconSize)) {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), Paint())
            with(painter) {
                draw(size = size, colorFilter = ColorFilter.tint(tint))
            }
            drawCircle(
                color = Color.Black,
                radius = size.minDimension * EyeRadiusRatio,
                center = Offset(size.width * EyeCenterXRatio, size.height * EyeCenterYRatio),
                blendMode = BlendMode.Clear,
            )
            canvas.restore()
        }
    }
}

private val IconSize = 24.dp

// 眼睛在 24 视口里的位置：圆心 (17.50, 11.967)。
// 原图眼睛（含 6dp 描边）在 24 视口里半径只有 0.84dp，实际渲染不到 5 像素，
// 会被抗锯齿糊成方块。这里放大到 1.05dp，保住圆形，仍在头部轮廓之内。
private const val EyeCenterXRatio = 0.7291f
private const val EyeCenterYRatio = 0.4986f
private const val EyeRadiusRatio = 0.0438f
