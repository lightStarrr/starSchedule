package com.star.schedule.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 设置页共用的列表组件。
 *
 * [SettingsSwitchItem] 有两种形态：
 * - 只传 [SettingsSwitchItem.onCheckedChange]：整行是一个开关，点哪里都切换；
 * - 同时传 [SettingsSwitchItem.onClick]：左侧区域只负责跳转，右侧 Switch 独立处理开关，
 *   两块点击区互不干扰（与 InstallerX-Revived 的 SwitchWidget 一致）。
 */
@Composable
internal fun SettingsSectionTitle(
    resId: Int,
    // 页面里第一组标题紧接在顶栏的留白之后，此时不需要再叠一层上边距，
    // 否则会比其它页面（顶栏 → 第一张卡片）多出一截，看着很空。
    topPadding: Dp = 24.dp,
) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 24.dp, top = topPadding, end = 24.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun settingsItemColors(): ListItemColors {
    // 列表项用 surfaceBright，页面底色是 surfaceContainer，项与项的空隙自然露出底色；
    // 禁用态与选中态保持同一底色，只靠内容颜色变淡，避免行在状态切换时换一个色阶。
    val container = MaterialTheme.colorScheme.surfaceBright
    return ListItemDefaults.colors(
        containerColor = container,
        selectedContainerColor = container,
        disabledContainerColor = container,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsListItem(
    title: String,
    supporting: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    shapeIndex: Int,
    shapeCount: Int,
    // 需要在滑动时临时改圆角（比如让相邻的角「让开」）就传一份自己的形状；
    // 平时传 null，用 M3 分段列表的原生形状。
    shapes: ListItemShapes? = null,
) {
    val colors = settingsItemColors()
    val resolvedShapes = shapes ?: ListItemDefaults.segmentedShapes(shapeIndex, shapeCount)
    if (onClick == null) {
        SegmentedListItem(
            modifier = modifier,
            shapes = resolvedShapes,
            enabled = enabled,
            colors = colors,
            leadingContent = leading,
            verticalAlignment = Alignment.CenterVertically,
            content = { Text(title) },
            supportingContent = { Text(supporting) },
            trailingContent = trailing,
        )
    } else {
        SegmentedListItem(
            onClick = onClick,
            modifier = modifier,
            shapes = resolvedShapes,
            enabled = enabled,
            colors = colors,
            leadingContent = leading,
            verticalAlignment = Alignment.CenterVertically,
            content = { Text(title) },
            supportingContent = { Text(supporting) },
            trailingContent = trailing,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsActionItem(
    title: String,
    supporting: String,
    onClick: () -> Unit,
    shapeIndex: Int,
    shapeCount: Int,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) = SettingsListItem(
    title = title,
    supporting = supporting,
    onClick = onClick,
    leading = leading,
    trailing = trailing,
    shapeIndex = shapeIndex,
    shapeCount = shapeCount,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsSwitchItem(
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shapeIndex: Int,
    shapeCount: Int,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val navigates = onClick != null
    val modifier = if (navigates) {
        // 左侧只负责跳转，开关另有一个独立节点，这里声明成按钮而不是开关，
        // 避免读屏把「进入下一页」念成「切换开关」。
        Modifier.semantics { role = Role.Button }
    } else {
        Modifier.semantics(mergeDescendants = true) {
            role = Role.Switch
            toggleableState = ToggleableState(checked)
        }
    }
    SegmentedListItem(
        onClick = onClick ?: { if (enabled) onCheckedChange(!checked) },
        shapes = ListItemDefaults.segmentedShapes(shapeIndex, shapeCount),
        modifier = modifier,
        enabled = enabled,
        verticalAlignment = Alignment.CenterVertically,
        colors = settingsItemColors(),
        leadingContent = leading,
        content = { Text(title) },
        supportingContent = { Text(supporting) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 左右分区时用一条竖线把「可点的标题区」和「开关区」分开，
                // 尺寸与安装器设置页一致：32dp 高，线后留 16dp。
                if (navigates) {
                    VerticalDivider(modifier = Modifier.height(32.dp))
                    Spacer(Modifier.width(16.dp))
                }
                Switch(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = onCheckedChange,
                    // 整行即开关时清空子节点语义，避免同一个开关被朗读两次；
                    // 左右分区时保留，让开关自己成为一个可聚焦节点。
                    modifier = if (navigates) Modifier else Modifier.clearAndSetSemantics { },
                )
            }
        },
    )
}
