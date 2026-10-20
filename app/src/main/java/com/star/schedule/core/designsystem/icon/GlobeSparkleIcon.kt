package com.star.schedule.core.designsystem.icon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 地球 + 右下角一颗星星，表示「在网站上用 AI 识别课表」。
 *
 * Material Icons 没有现成的组合图标。地球按 24dp 画满，星星 12dp 叠在右下角。
 *
 * 两个形状用不同颜色：同色时星星会陷进地球里，只能靠在星星底下垫一圈背景色来分开，
 * 而垫底会把地球挖掉一块，球看起来像缺了个角。给星星单独一个强调色之后，重叠关系
 * 一眼就能看清，地球也保持完整。
 */
@Composable
fun GlobeSparkleIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    sparkleTint: Color = MaterialTheme.colorScheme.primary,
) {
    Box(modifier = modifier.size(IconSize)) {
        Icon(
            imageVector = Icons.Rounded.Public,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(IconSize),
        )
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = sparkleTint,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(SparkleSize),
        )
    }
}

private val IconSize = 24.dp
private val SparkleSize = 12.dp
