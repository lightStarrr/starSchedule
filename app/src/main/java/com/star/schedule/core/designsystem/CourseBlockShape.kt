package com.star.schedule.core.designsystem

import androidx.compose.ui.unit.dp

/**
 * 课程方块的圆角，首页课表与设置页预览共用，两处看起来才是同一套东西。
 *
 * 预览卡是整张课表的缩小版，这里用同一个绝对值而不是跟着卡片圆角做同心计算——
 * 同心算出来的内圆角在窄列上会大到接近胶囊形。
 */
val CourseBlockCornerRadius = 10.dp
