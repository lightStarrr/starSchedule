package com.star.schedule.feature.timetable.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    onRowHeightChange: (Long, Int) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    // pager 状态提到这一层：下面的设置列表要跟着「居中那张」走，而不是已经应用的那张。
    val pagerState = rememberPagerState(pageCount = { state.previews.size + 1 })
    val previewed = state.previews.getOrNull(pagerState.currentPage)

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
                    pagerState = pagerState,
                    previews = state.previews,
                    currentId = state.currentId,
                    onApply = onApply,
                )
                if (previewed != null) {
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
                                                previewed.id,
                                                previewed.rowHeight -
                                                    TimetableSettingsViewModel.ROW_HEIGHT_STEP
                                            )
                                        },
                                        enabled = previewed.rowHeight >
                                            TimetableSettingsViewModel.ROW_HEIGHT_MIN,
                                        shapes = IconButtonDefaults.shapes(),
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
                                            previewed.rowHeight
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.width(56.dp),
                                    )
                                    IconButton(
                                        onClick = {
                                            onRowHeightChange(
                                                previewed.id,
                                                previewed.rowHeight +
                                                    TimetableSettingsViewModel.ROW_HEIGHT_STEP
                                            )
                                        },
                                        enabled = previewed.rowHeight <
                                            TimetableSettingsViewModel.ROW_HEIGHT_MAX,
                                        shapes = IconButtonDefaults.shapes(),
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
    pagerState: PagerState,
    previews: List<TimetablePreview>,
    currentId: Long?,
    onApply: (Long) -> Unit,
) {
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
            val cardWidth = (maxWidth * PreviewCardWidthFraction).coerceAtMost(PreviewCardMaxWidth)
            val cardHeight = cardWidth * PreviewCardAspect
            val sidePadding = ((maxWidth - cardWidth) / 2).coerceAtLeast(16.dp)
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = sidePadding),
                pageSpacing = 12.dp,
                pageSize = PageSize.Fixed(cardWidth),
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
                        NewTimetableCard(width = cardWidth, height = cardHeight)
                    } else {
                        TimetablePreviewCard(
                            preview = preview,
                            applied = preview.id == currentId,
                            width = cardWidth,
                            height = cardHeight,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(page) }
                            },
                        )
                    }
                }
            }
        }

        // 用 currentPage 而不是 settledPage：滑动过半按钮就该跟着变，settledPage 要等动画完全停下。
        val previewed = previews.getOrNull(pagerState.currentPage)
        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Button(
                onClick = { previewed?.let { onApply(it.id) } },
                shapes = ButtonDefaults.shapes(),
                enabled = previewed != null && previewed.id != currentId,
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
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(width)
            .height(height),
        shape = PreviewCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceBright,
        ),
        // 当前课表用 primary 描边；卡片本身用 Card，点击、水波纹、语义都由组件自带。
        border = if (applied) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
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
private fun NewTimetableCard(width: Dp, height: Dp) {
    Card(
        modifier = Modifier
            .width(width)
            .height(height),
        shape = PreviewCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // TODO(设计): 新建课表的流程还没定，先只放占位，不挂点击。
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.timetable_settings_new),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

private val PreviewCardShape = RoundedCornerShape(24.dp)

// 预览卡占可用宽度的比例，高度按手机常见比例 9:19.5 推出来。
private const val PreviewCardWidthFraction = 0.64f
private val PreviewCardMaxWidth = 280.dp
private const val PreviewCardAspect = 19.5f / 9f
private const val THUMBNAIL_ROWS = 12
