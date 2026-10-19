package com.star.schedule.core.designsystem.icon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 地球 + 右下角一颗星星，表示「在网站上用 AI 识别课表」。
 *
 * Material Icons 没有现成的组合图标。这里把地球缩小到 17dp 占左上角，
 * 星星 9dp 放右下角，两块不重叠；整体仍是 24dp，和其他列表图标尺寸一致。
 *
 * 用两个现成图标叠加而不是手写 `ImageVector`：形状直接取自 Material，
 * 不会因为自己描路径在多密度下走形。
 */
@Composable
fun GlobeSparkleIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Box(modifier = modifier.size(GlobeSparkleIconSize)) {
        Icon(
            imageVector = Icons.Rounded.Public,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(GlobeSize)
                .align(Alignment.TopStart),
        )
        Icon(
            imageVector = Icons.Rounded.StarRate,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(SparkleSize)
                .align(Alignment.BottomEnd),
        )
    }
}

private val GlobeSparkleIconSize = 24.dp
private val GlobeSize = 17.dp
private val SparkleSize = 9.dp
