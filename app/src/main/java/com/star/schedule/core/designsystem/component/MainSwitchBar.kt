package com.star.schedule.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val SwitchBarMinHeight = 72.dp
private val SwitchBarShape = RoundedCornerShape(35.dp)
private val SwitchBarHorizontalPadding = 20.dp
private val SwitchBarTitleMargin = 16.dp

/**
 * 仿 AOSP 设置页的主开关条：整条可点，右侧是 [Switch]。
 *
 * 与设置列表项里的开关不同，这一层是「页面级总开关」，只负责开关本身，
 * 不承载跳转，因此整条统一使用 [Role.Switch] 语义，内部的 Switch 不再单独暴露节点。
 */
@Composable
fun MainSwitchBar(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val containerColor = if (checked) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (checked) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SwitchBarMinHeight)
            .clip(SwitchBarShape)
            .alpha(if (enabled) 1f else 0.38f)
            .background(containerColor)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { newValue ->
                    if (newValue) {
                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    } else {
                        haptic.performHapticFeedback(HapticFeedbackType.ToggleOff)
                    }
                    onCheckedChange(newValue)
                },
            )
            .padding(horizontal = SwitchBarHorizontalPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = SwitchBarTitleMargin),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            // 整条已经带有开关语义，这里清空子节点，避免同一个开关被朗读两次。
            modifier = Modifier.clearAndSetSemantics { },
            // 用 M3 默认配色，和列表项里的开关保持完全一致，不再单独定制轨道颜色。
        )
    }
}
