package com.star.schedule.feature.update.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.AppMenu
import com.star.schedule.core.designsystem.component.CircleIconButton
import com.star.schedule.core.designsystem.component.MenuEntry

@Composable
internal fun SoftwareUpdateOverflowMenu(
    includeSnapshots: Boolean,
    onIncludeSnapshotsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        CircleIconButton(
            icon = Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.update_more_options),
            onClick = { expanded = true },
        )
        AppMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            groups = listOf(
                listOf(
                    MenuEntry(
                        label = stringResource(R.string.update_include_snapshots),
                        selected = includeSnapshots,
                        onClick = {
                            // 切换选中状态不收起菜单，用户能直接看到状态翻转。
                            onIncludeSnapshotsChange(!includeSnapshots)
                        },
                    ),
                ),
            ),
        )
    }
}
