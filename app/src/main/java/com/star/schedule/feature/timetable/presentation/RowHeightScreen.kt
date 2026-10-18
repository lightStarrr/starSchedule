package com.star.schedule.feature.timetable.presentation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.CompactPageTopBar
import com.star.schedule.core.designsystem.navigationBarBottomInset
import com.star.schedule.feature.schedule.presentation.TimetableGrid
import kotlin.math.roundToInt

/** 恢复默认按钮的直径，同时用作标题行里预留的槽位大小。 */
private val RestoreButtonSize = 32.dp

@Composable
fun RowHeightRoute(
    viewModel: RowHeightViewModel,
    onBack: () -> Unit,
    onApplied: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RowHeightScreen(
        state = state,
        onBack = onBack,
        onRowHeightChange = viewModel::setRowHeight,
        onApply = {
            viewModel.apply()
            onApplied()
        },
    )
}

/**
 * 行高调整页：整页就是一张课表，底部浮一张卡片做调节。
 *
 * 课表用「课最多的一周」，这样行高变化在密集的列上最容易看出来。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowHeightScreen(
    state: RowHeightUiState,
    onBack: () -> Unit,
    onRowHeightChange: (Int) -> Unit,
    onApply: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CompactPageTopBar(
                title = stringResource(R.string.timetable_settings_row_height),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    Button(
                        onClick = onApply,
                        enabled = state.canApply,
                        // 和左侧返回按钮同一套 expressive 形状，按下去都会收一下。
                        shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(50)),
                    ) {
                        Text(stringResource(R.string.timetable_settings_apply))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!state.isLoading) {
                TimetableGrid(
                    lessonTimes = state.lessonTimes,
                    courses = state.courses,
                    currentWeek = state.week,
                    weekStartDate = state.weekStartDate,
                    showWeekend = state.showWeekend,
                    rowHeight = state.rowHeight.dp,
                    // 给底部悬浮卡片让出位置，最后一节课才不会被挡住。
                    bottomContentPadding = 140.dp + navigationBarBottomInset(),
                    modifier = Modifier.fillMaxSize(),
                )
            }

            RowHeightPanel(
                rowHeight = state.rowHeight,
                onRowHeightChange = onRowHeightChange,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/** 恢复默认行高：小号圆形图标按钮，按压形变与顶栏返回键同源。 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RestoreDefaultButton(
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(shape = CircleShape),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = Modifier.size(RestoreButtonSize),
    ) {
        Icon(
            imageVector = Icons.Rounded.RestartAlt,
            contentDescription = stringResource(R.string.timetable_settings_row_height_reset),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun RowHeightPanel(
    rowHeight: Int,
    onRowHeightChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.timetable_settings_row_height),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    // 位置和尺寸always留着（32dp 的槽），只做淡入淡出 + 缩放，
                    // 这样按钮出现/消失时标题行高度不变，右边的数值也不会被挤动。
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier.size(RestoreButtonSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 用顶层重载，别用 RowScope 的那个（会按行内布局撑开宽度）。
                        androidx.compose.animation.AnimatedVisibility(
                            visible = rowHeight != RowHeightViewModel.DEFAULT_ROW_HEIGHT,
                            enter = fadeIn() + scaleIn(initialScale = 0.6f),
                            exit = fadeOut() + scaleOut(targetScale = 0.6f),
                        ) {
                            RestoreDefaultButton(
                                onClick = {
                                    onRowHeightChange(RowHeightViewModel.DEFAULT_ROW_HEIGHT)
                                },
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.row_height_value, rowHeight),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = rowHeight.toFloat(),
                onValueChange = { onRowHeightChange(it.roundToInt()) },
                valueRange = RowHeightViewModel.ROW_HEIGHT_MIN.toFloat()..
                    RowHeightViewModel.ROW_HEIGHT_MAX.toFloat(),
                // 每 10dp 一个刻点，正好对上能停的位置。
                steps = (RowHeightViewModel.ROW_HEIGHT_MAX - RowHeightViewModel.ROW_HEIGHT_MIN) /
                    RowHeightViewModel.ROW_HEIGHT_STEP - 1,
            )
        }
    }
}
