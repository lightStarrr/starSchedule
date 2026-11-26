package com.star.schedule.feature.timetable.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.star.schedule.R

/**
 * 7 格横向连接星期选择条：与周次快捷选择条一致，采用 M3E Connected Button Group 单选周几。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CourseWeekdayBar(
    selectedDayOfWeek: Int,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekdayLabels = listOf(
        stringResource(R.string.weekday_short_monday),
        stringResource(R.string.weekday_short_tuesday),
        stringResource(R.string.weekday_short_wednesday),
        stringResource(R.string.weekday_short_thursday),
        stringResource(R.string.weekday_short_friday),
        stringResource(R.string.weekday_short_saturday),
        stringResource(R.string.weekday_short_sunday),
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        weekdayLabels.forEachIndexed { index, shortLabel ->
            val day = index + 1
            val isSelected = day == selectedDayOfWeek
            val shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                weekdayLabels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            val dayDescription = stringResource(R.string.weekday_column_label, shortLabel)

            ToggleButton(
                checked = isSelected,
                onCheckedChange = { onSelectDay(day) },
                buttonSize = ToggleButtonSize.Small,
                shapes = shapes,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceBright,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = dayDescription
                    },
            ) {
                Text(
                    text = shortLabel,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
