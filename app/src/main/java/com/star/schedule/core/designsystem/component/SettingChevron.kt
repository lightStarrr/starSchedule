package com.star.schedule.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 列表行末尾的「可进入下一页」指示。
 *
 * 只给真的会跳到另一个页面的行用；打开对话框、拉起文件选择器、跳浏览器都不要用，
 * 否则这个箭头就是在骗人，用户点一次发现没跳页就不再信它了。
 */
@Composable
fun SettingChevron(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Rounded.ChevronRight,
        contentDescription = null,
        modifier = modifier,
    )
}
