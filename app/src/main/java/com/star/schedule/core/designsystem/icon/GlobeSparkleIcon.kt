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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 地球 + 右下角一颗星星，表示「在网站上用 AI 识别课表」。
 *
 * Material Icons 没有现成的组合图标。地球按 24dp 画满，星星 10dp 叠在右下角。
 *
 * 星星下面铺一层同形状、[background] 色的描边：两个同色实心图形直接重叠会糊成
 * 一团，只露出星星的尖角；用圆形挖空切口又太深。
 *
 * 描边不能简单放大一个同位图标的尺寸——`AutoAwesome` 的大星偏左下、两颗小星在
 * 右上，缩放中心落在盒子中心会一侧厚一侧薄，看着就是错位。这里两层用同一个
 * 10dp 图标，只给描边一层套 [graphicsLayer]，并把缩放原点定在大星中心。
 *
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
                .size(SparkleSize),
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = background,
                modifier = Modifier
                    .size(SparkleSize)
                    .graphicsLayer {
                        scaleX = SparkleOutlineScale
                        scaleY = SparkleOutlineScale
                        transformOrigin = SparkleOutlineOrigin
                    },
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

// 描边沿四周扩到星星的 1.45 倍，10dp 下大约留出 1.5dp 的缝。
private const val SparkleOutlineScale = 1.45f

// AutoAwesome 的 viewBox 里大星中心约在 (9.6, 13)，缩放到 0..1 就是这两个比例。
private val SparkleOutlineOrigin = TransformOrigin(0.40f, 0.54f)
