package com.star.schedule.core.designsystem

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * 导航栏（手势条 / 三键导航）占的高度。
 *
 * 页面走 edge-to-edge 后不能用 `navigationBarsPadding()` 把内容顶上去——那样手势条后面会留一条
 * 纯背景的带子。正确做法是把这份高度加到滚动内容的 bottom padding 里，让列表从手势条下面滑过去。
 */
@Composable
fun navigationBarBottomInset(): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
