package com.star.schedule.core.designsystem.icon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Public
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
 * Material Icons 没有现成的组合图标。地球按 24dp 画满，星星 10dp 叠在右下角。
 *
 * 星星先铺一层同形状、用 [background] 色的放大版当描边。两个同色实心图形直接重叠
 * 会糊成一团，只露出星星的尖角，看起来像地球缺了一块；用圆形挖空切口又太深。
 * 让描边跟着星星的形状走，地球被切掉的轮廓才和星星对得上。
 * 调用方必须传入所在容器的实际颜色，否则描边处会出现色块。
 */
@Composable
fun GlobeSparkleIcon(
    background: Color,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Box(modifier = modifier.size(IconSize)) {
        Icon(
            imageVector = Icons.Rounded.Public,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(IconSize),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(SparkleOutlineSize),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = background,
                modifier = Modifier.size(SparkleOutlineSize),
            )
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(SparkleSize),
            )
        }
    }
}

private val IconSize = 24.dp
private val SparkleSize = 10.dp

// 描边比星星大一圈，间隙在 10dp 的尺寸下才不会糊成一条线。
private val SparkleOutlineSize = 14.dp
