package com.star.schedule.feature.timetable.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.CountdownSnackbar
import com.star.schedule.core.designsystem.component.ElasticSwipeRow
import com.star.schedule.core.designsystem.component.MESSAGE_SNACKBAR_TIMEOUT_MILLIS
import com.star.schedule.core.designsystem.component.PageContentTopSpacing
import com.star.schedule.core.designsystem.component.PageTopBar
import com.star.schedule.core.designsystem.component.UNDO_SNACKBAR_TIMEOUT_MILLIS
import com.star.schedule.core.designsystem.component.morphedSegmentedShapes
import com.star.schedule.core.designsystem.component.rememberElasticSwipeState
import com.star.schedule.core.designsystem.navigationBarBottomInset
import com.star.schedule.feature.schedule.presentation.EmptyTimetableState
import com.star.schedule.feature.schedule.presentation.TimetableEmptyState
import com.star.schedule.feature.settings.presentation.SettingsListItem
import com.star.schedule.feature.timetable.domain.formatTemplateUpdatedAt
import kotlin.math.abs

/** 与 SettingsGroup 的左右留白保持一致。 */
private val LIST_HORIZONTAL_PADDING = 16.dp

@Composable
fun LessonTimeTemplatesRoute(
    viewModel: LessonTimeTemplatesViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LessonTimeTemplatesScreen(
        state = state,
        onBack = onBack,
        onDelete = viewModel::delete,
        onUndoDelete = viewModel::undoDelete,
        onDismissUndo = viewModel::dismissUndo,
        onMessageShown = viewModel::onMessageShown,
    )
}

/**
 * 节次时间模板：和节次时间页同一个列表样式，一行一个模板，左滑删除并给一条可撤销的提示。
 *
 * 模板是全局资产，所以这里只有「看」和「删」，新建入口在节次时间页的保存按钮上。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LessonTimeTemplatesScreen(
    state: LessonTimeTemplatesUiState,
    onBack: () -> Unit,
    onDelete: (LessonTimeTemplateRow) -> Unit,
    onUndoDelete: (Long) -> Unit,
    onDismissUndo: (Long) -> Unit,
    onMessageShown: (Long) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navigationBarInset = navigationBarBottomInset()
    val snackbarHostState = remember { SnackbarHostState() }
    val swipeState = rememberElasticSwipeState()
    val undoLabel = stringResource(R.string.action_undo)
    val undo = state.undo
    val undoMessage = undo?.let { stringResource(R.string.lesson_time_template_deleted, it.name) }
    val message = state.message
    val messageText = message?.let { stringResource(it.type.textRes) }

    // 与节次时间页同一套撤销语义：删除已经落库，撤销就是把那条原样插回去。
    // key 用 token 而不是文案：同名模板被删两次时文案完全一样，用文案做 key 不会重启倒计时。
    LaunchedEffect(undo?.token) {
        if (undo == null || undoMessage == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = undoMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Indefinite,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onUndoDelete(undo.token)
        } else {
            onDismissUndo(undo.token)
        }
    }

    LaunchedEffect(message?.token) {
        if (message == null || messageText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = messageText,
            duration = SnackbarDuration.Indefinite,
        )
        onMessageShown(message.token)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            // 这一页为了列表能滑到导航栏下面把 inset 清零了，提示条自己把导航栏留白补回来。
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            ) { data ->
                CountdownSnackbar(
                    data = data,
                    timeoutMillis = if (data.visuals.actionLabel != null) {
                        UNDO_SNACKBAR_TIMEOUT_MILLIS
                    } else {
                        MESSAGE_SNACKBAR_TIMEOUT_MILLIS
                    },
                )
            }
        },
        topBar = {
            PageTopBar(
                title = stringResource(R.string.title_course_time_templates),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator()
                    }
                }

                state.rows.isEmpty() -> {
                    EmptyTimetableState(
                        state = TimetableEmptyState.NO_LESSON_TIMES,
                        modifier = Modifier.fillMaxSize(),
                        titleResId = R.string.lesson_time_templates_empty,
                        action = {
                            Text(
                                text = stringResource(R.string.lesson_time_templates_empty_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        },
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 720.dp)
                            .align(Alignment.TopCenter)
                            .padding(innerPadding),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        contentPadding = PaddingValues(
                            top = PageContentTopSpacing,
                            bottom = 24.dp + navigationBarInset,
                        ),
                    ) {
                        itemsIndexed(
                            items = state.rows,
                            key = { _, row -> row.id },
                        ) { index, row ->
                            ElasticSwipeRow(
                                index = index,
                                state = swipeState,
                                onDelete = { onDelete(row) },
                                modifier = Modifier
                                    .padding(horizontal = LIST_HORIZONTAL_PADDING)
                                    .animateItem(),
                            ) { info ->
                                // 朝向被拖走那一侧的圆角让开一点，另外两个角保持原样。
                                val morph = abs(info.follow)
                                val shapes = if (morph > 0.001f && info.morphSide != 0) {
                                    morphedSegmentedShapes(
                                        shapeIndex = index,
                                        shapeCount = state.rows.size,
                                        topMorph = if (info.morphSide < 0) morph else 0f,
                                        bottomMorph = if (info.morphSide > 0) morph else 0f,
                                    )
                                } else {
                                    null
                                }
                                SettingsListItem(
                                    title = row.name,
                                    supporting = templateSupportingText(row),
                                    shapeIndex = index,
                                    shapeCount = state.rows.size,
                                    shapes = shapes,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 副标题：共几节，以及最后一次改动的时间；具体几点到几点留给详情，不挤在列表里。 */
@Composable
private fun templateSupportingText(row: LessonTimeTemplateRow): String {
    val updatedAt = remember(row.updatedAt) { formatTemplateUpdatedAt(row.updatedAt) }
    return stringResource(R.string.lesson_time_template_summary, row.periodCount, updatedAt)
}

/** 提示类型是业务状态，字符串资源只在这一层出现。 */
private val LessonTimeTemplatesMessageType.textRes: Int
    get() = when (this) {
        LessonTimeTemplatesMessageType.RestoreFailed -> R.string.lesson_time_template_restore_failed
    }
