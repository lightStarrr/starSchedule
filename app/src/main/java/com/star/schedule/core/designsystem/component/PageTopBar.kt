package com.star.schedule.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 二级页面统一的顶栏：expressive 大标题 + 圆形返回按钮。
 *
 * 底色与页面一致（`surfaceContainer`），滚动时也不变色，所以内容滑到顶栏下面
 * 不会出现一条色阶跳变。标题用 `onBackground`，比默认的 `onSurface` 更实一些。
 *
 * 用法与 [Scaffold] 的 `topBar` 槽位对齐：页面自己创建 [TopAppBarScrollBehavior]
 * （通常 `TopAppBarDefaults.exitUntilCollapsedScrollBehavior()`），传给顶栏的同时
 * 挂到 `Scaffold` 的 `Modifier.nestedScroll(behavior.nestedScrollConnection)` 上。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PageTopBar(
    title: String,
    onBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    LargeFlexibleTopAppBar(
        title = { Text(title) },
        modifier = modifier,
        navigationIcon = {
            Row {
                CircleBackButton(onClick = onBack)
                Spacer(Modifier.size(16.dp))
            }
        },
        actions = actions,
        // 顶栏自带的状态栏内边距之外，左右各留 12dp：
        // 系统栏本身没有横向内边距，只留左边的话返回键离边缘 16dp、右侧操作只有 4dp，看起来是歪的。
        windowInsets = TopAppBarDefaults.windowInsets.add(
            WindowInsets(left = 12.dp, right = 12.dp),
        ),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        scrollBehavior = scrollBehavior,
    )
}

/**
 * 不折叠的紧凑顶栏：只有一行小标题，右侧留给操作。
 *
 * 用在「整页预览 + 底部浮层」这类页面——标题不该占掉预览的高度。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CompactPageTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        modifier = modifier,
        navigationIcon = {
            Row {
                CircleBackButton(onClick = onBack)
                Spacer(Modifier.size(16.dp))
            }
        },
        actions = actions,
        windowInsets = TopAppBarDefaults.windowInsets.add(
            WindowInsets(left = 12.dp, right = 12.dp),
        ),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
        scrollBehavior = scrollBehavior,
    )
}
