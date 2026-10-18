package com.star.schedule.feature.timetable.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.PageTopBar
import com.star.schedule.feature.settings.domain.TimetablePreview
import com.star.schedule.feature.settings.presentation.SettingsGroup
import com.star.schedule.feature.settings.presentation.SettingsListItem

@Composable
fun TimetableSettingsRoute(
    viewModel: TimetableSettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TimetableSettingsScreen(
        state = state,
        onBack = onBack,
        onApply = viewModel::apply,
        onRowHeightChange = viewModel::setRowHeight,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TimetableSettingsScreen(
    state: TimetableSettingsUiState,
    onBack: () -> Unit,
    onApply: (Long) -> Unit,
    onRowHeightChange: (Int) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            PageTopBar(
                title = stringResource(R.string.timetable_settings_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
            ) {
                TimetablePreviewPager(
                    previews = state.previews,
                    currentId = state.currentId,
                    onApply = onApply,
                )
                val current = state.current
                if (current != null) {
                    Spacer(Modifier.height(24.dp))
                    SettingsGroup {
                        SettingsListItem(
                            title = stringResource(R.string.timetable_settings_row_height),
                            supporting = stringResource(R.string.timetable_settings_row_height_support),
                            shapeIndex = 0,
                            shapeCount = 1,
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            onRowHeightChange(
                                                current.rowHeight -
                                                    TimetableSettingsViewModel.ROW_HEIGHT_STEP
                                            )
                                        },
                                        enabled = current.rowHeight >
                                            TimetableSettingsViewModel.ROW_HEIGHT_MIN,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Remove,
                                            contentDescription = stringResource(
                                                R.string.timetable_settings_row_height_decrease
                                            ),
                                        )
                                    }
                                    Text(
                                        text = stringResource(
                                            R.string.row_height_value,
                                            current.rowHeight
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.width(56.dp),
                                    )
                                    IconButton(
                                        onClick = {
                                            onRowHeightChange(
                                                current.rowHeight +
                                                    TimetableSettingsViewModel.ROW_HEIGHT_STEP
                                            )
                                        },
                                        enabled = current.rowHeight <
                                            TimetableSettingsViewModel.ROW_HEIGHT_MAX,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = stringResource(
                                                R.string.timetable_settings_row_height_increase
                                            ),
                                        )
                                    }
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/**
 * 顶部预览：左右滑动看不同课表，最右一张是「新建」占位。
 * 滑动只改变预览，真正切换要点下面的「应用」，避免顺手一滑就把课表换掉。
 */
@Composable
private fun TimetablePreviewPager(
    previews: List<TimetablePreview>,
    currentId: Long?,
    onApply: (Long) -> Unit,
) {
    val pageCount = previews.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    // 当前课表变化（首次加载、点应用之后）时把预览滚到对应位置。
    LaunchedEffect(currentId, previews.size) {
        val index = previews.indexOfFirst { it.id == currentId }
        if (index >= 0 && pagerState.currentPage != index) {
            pagerState.animateScrollToPage(index)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 左右各留 (宽 - 卡片宽)/2，居中的那一页才落在屏幕正中，两侧各露出半张相邻课表。
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val sidePadding = ((maxWidth - PreviewCardWidth) / 2).coerceAtLeast(16.dp)
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = sidePadding),
                pageSpacing = 12.dp,
                pageSize = PageSize.Fixed(PreviewCardWidth),
            ) { page ->
                val preview = previews.getOrNull(page)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.height(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (preview != null) {
                            Text(
                                text = preview.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (preview.id == currentId) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (preview == null) {
                        NewTimetableCard()
                    } else {
                        TimetablePreviewCard(
                            preview = preview,
                            applied = preview.id == currentId,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(page) }
                            },
                        )
                    }
                }
            }
        }

        val settled = previews.getOrNull(pagerState.settledPage)
        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FilledTonalButton(
                onClick = { settled?.let { onApply(it.id) } },
                enabled = settled != null && settled.id != currentId,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.timetable_settings_apply))
            }
        }
    }
}

@Composable
private fun TimetablePreviewCard(
    preview: TimetablePreview,
    applied: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val outline = if (applied) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
    } else {
        Modifier
    }
    Box(
        modifier = Modifier
            .width(PreviewCardWidth)
            .height(PreviewCardHeight)
            .then(outline)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceBright)
            // 点露出来的相邻课表就把它滑到中间，不用手动划。
            .clickable(onClick = onClick),
    ) {
        TimetableThumbnail(
            preview = preview,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        )
    }
}

/** 缩略网格：只画课程块，不放文字，和系统「壁纸与风格」里的预览卡同一路数。 */
@Composable
private fun TimetableThumbnail(
    preview: TimetablePreview,
    modifier: Modifier = Modifier,
) {
    val columns = if (preview.hasWeekendCourses) 7 else 5
    val blockColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)

    Canvas(modifier = modifier) {
        val cellWidth = size.width / columns
        val cellHeight = size.height / THUMBNAIL_ROWS
        val gap = 1.dp.toPx()
        preview.blocks.forEach { block ->
            if (block.dayOfWeek > columns) return@forEach
            val top = (block.startPeriod - 1) * cellHeight
            if (top >= size.height) return@forEach
            drawRoundRect(
                color = blockColor,
                topLeft = Offset((block.dayOfWeek - 1) * cellWidth + gap, top + gap),
                size = Size(
                    width = (cellWidth - gap * 2).coerceAtLeast(1f),
                    height = (cellHeight * block.periodCount - gap * 2).coerceAtLeast(1f),
                ),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun NewTimetableCard() {
    Box(
        modifier = Modifier
            .width(PreviewCardWidth)
            .height(PreviewCardHeight)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        // TODO(设计): 新建课表的流程还没定，先只放占位，不挂点击。
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.timetable_settings_new),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
    }
}

private val PreviewCardWidth = 128.dp
private val PreviewCardHeight = 240.dp
private const val THUMBNAIL_ROWS = 12
