package com.star.schedule.feature.timetable.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.CircleIconButton
import com.star.schedule.core.designsystem.component.PageContentTopSpacing
import com.star.schedule.core.designsystem.component.PageTopBar
import com.star.schedule.core.designsystem.navigationBarBottomInset
import com.star.schedule.feature.schedule.presentation.EmptyTimetableState
import com.star.schedule.feature.schedule.presentation.TimetableEmptyState
import com.star.schedule.feature.settings.presentation.SettingsListItem
import com.star.schedule.feature.timetable.domain.LessonTimeRules
import com.star.schedule.feature.timetable.presentation.components.LessonTimeEditorSheet

/** 相邻两节空档超过这个分钟数就在副标题里提示，正常的课间不提示。 */
private const val LONG_GAP_MINUTES = 15

/** 左滑到底的判定比例。 */
private const val DISMISS_THRESHOLD_RATIO = 0.5f

/** 与 SettingsGroup 的左右留白保持一致。 */
private val LIST_HORIZONTAL_PADDING = 16.dp

/** 撤销提示停留多久，进度条走完就自动消失。 */
private const val UNDO_TIMEOUT_MILLIS = 5_000

@Composable
fun LessonTimeSettingsRoute(
    viewModel: LessonTimeSettingsViewModel,
    onBack: () -> Unit,
    onOpenTemplates: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LessonTimeSettingsScreen(
        state = state,
        onBack = onBack,
        onOpenTemplates = onOpenTemplates,
        onAdd = viewModel::startAdd,
        onEdit = viewModel::startEdit,
        onDelete = viewModel::delete,
        onUndoDelete = viewModel::undoDelete,
        onDismissUndo = viewModel::dismissUndo,
        onSelectField = viewModel::selectField,
        onSetTime = viewModel::setTime,
        onDismissEditor = viewModel::dismissEditor,
        onSave = viewModel::save,
    )
}

/**
 * 节次时间：一行就是时间列里的一节，主要信息是几点到几点，节次号放在副标题。
 *
 * 点整行改时间（浮层里的按钮提交），左滑删除，删完给一条可撤销的提示。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LessonTimeSettingsScreen(
    state: LessonTimeSettingsUiState,
    onBack: () -> Unit,
    onOpenTemplates: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (LessonTimeRow) -> Unit,
    onDelete: (LessonTimeRow) -> Unit,
    onUndoDelete: () -> Unit,
    onDismissUndo: () -> Unit,
    onSelectField: (LessonTimeField) -> Unit,
    onSetTime: (LessonTimeField, Int) -> Unit,
    onDismissEditor: () -> Unit,
    onSave: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navigationBarInset = navigationBarBottomInset()
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)
    val deletedMessage = state.deletedPeriod
        ?.let { period -> stringResource(R.string.lesson_time_deleted, period) }

    // 提示只负责展示和撤销：删除已经落库，撤销就是把那条原样插回去。
    // 用 Indefinite + 自己的倒计时，而不是 Duration.Short 的 4 秒：撤销要留出看得见的时间。
    LaunchedEffect(deletedMessage) {
        if (deletedMessage == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Indefinite,
        )
        if (result == SnackbarResult.ActionPerformed) onUndoDelete() else onDismissUndo()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            // 位置由 Scaffold 决定：没有 bottomBar/FAB 时它会把 snackbar 抬到
            // contentWindowInsets.bottom 之上。这一页为了列表能滑到导航栏下面把 inset 清零了，
            // 所以这里自己把导航栏 inset 加回去，别让提示压在手势条后面。
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            ) { data ->
                UndoSnackbar(
                    data = data,
                    onAction = { data.performAction() },
                    onTimeout = { data.dismiss() },
                )
            }
        },
        topBar = {
            PageTopBar(
                title = stringResource(R.string.timetable_settings_lesson_times),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    // 模板是全局资产、和本课表的节次不是一个作用域，只留纯图标入口。
                    CircleIconButton(
                        icon = Icons.Rounded.Bookmarks,
                        contentDescription = stringResource(R.string.title_course_time_templates),
                        onClick = onOpenTemplates,
                    )
                    CircleIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.title_add_lesson_time),
                        onClick = onAdd,
                    )
                },
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
                        titleResId = R.string.lesson_time_empty_hint,
                        action = {
                            Button(onClick = onAdd) {
                                // 页面叫「节次时间」，这里也用「节次」的说法，不再叫课程时间。
                                Text(stringResource(R.string.lesson_time_add))
                            }
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
                        // 和 SettingsGroup 一致：行与行之间是分段间距，左右各留 16dp，
                        // 分段列表的圆角要能露出页面底色，行本身不能贴边。
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
                            LessonTimeSwipeRow(
                                row = row,
                                previous = state.rows.getOrNull(index - 1),
                                shapeIndex = index,
                                shapeCount = state.rows.size,
                                onClick = { onEdit(row) },
                                onDelete = { onDelete(row) },
                                modifier = Modifier
                                    .padding(horizontal = LIST_HORIZONTAL_PADDING)
                                    .animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }

    val editor = state.editor
    if (editor != null) {
        LessonTimeEditorSheet(
            title = stringResource(
                if (editor.editingId == null) {
                    R.string.title_add_lesson_time
                } else {
                    R.string.title_edit_lesson_time
                },
            ),
            startTime = editor.startTime,
            endTime = editor.endTime,
            activeField = editor.field,
            onSelectField = onSelectField,
            onTimeChange = { field, hour, minute -> onSetTime(field, hour * 60 + minute) },
            onDismiss = onDismissEditor,
            onConfirm = onSave,
            errorText = editor.error?.let { error -> lessonTimeErrorText(error) },
            confirmEnabled = !editor.isSaving,
        )
    }
}

/**
 * 可左滑删除的一行。
 *
 * 左滑露出的底色要按这一行的分段形状裁一下，否则组首/组尾的圆角后面会补出一个方块。
 * 右滑（StartToEnd）不接动作：这一页没有需要右滑完成的操作，留着容易误触。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonTimeSwipeRow(
    row: LessonTimeRow,
    previous: LessonTimeRow?,
    shapeIndex: Int,
    shapeCount: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shapes = ListItemDefaults.segmentedShapes(shapeIndex, shapeCount)
    val dismissState = remember(row.id) {
        SwipeToDismissBoxState(
            initialValue = SwipeToDismissBoxValue.Settled,
            positionalThreshold = { distance -> distance * DISMISS_THRESHOLD_RATIO },
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shapes.shape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        enableDismissFromStartToEnd = false,
        onDismiss = { onDelete() },
    ) {
        SettingsListItem(
            title = stringResource(R.string.lesson_time_range, row.startTime, row.endTime),
            supporting = lessonTimeSupportingText(row, previous),
            onClick = onClick,
            shapeIndex = shapeIndex,
            shapeCount = shapeCount,
        )
    }
}

/**
 * 副标题：第几节；和上一节之间空档超过 [LONG_GAP_MINUTES] 分钟时再补一句间隔，
 * 正常课间不写，免得每行都多一句废话。
 */
@Composable
private fun lessonTimeSupportingText(
    row: LessonTimeRow,
    previous: LessonTimeRow?,
): String {
    val previousEnd = previous?.spanMinutes?.last
    val start = row.spanMinutes?.first
    val gap = if (previousEnd != null && start != null) {
        LessonTimeRules.gapMinutes(previousEnd, start)
    } else {
        null
    }
    return if (gap != null && gap > LONG_GAP_MINUTES) {
        stringResource(R.string.lesson_time_period_with_gap, row.period, gap)
    } else {
        stringResource(R.string.lesson_time_period, row.period)
    }
}

@Composable
private fun lessonTimeErrorText(error: LessonTimeError): String = when (error) {
    LessonTimeError.InvalidTime -> stringResource(R.string.error_lesson_time_invalid)
    LessonTimeError.EndNotAfterStart -> stringResource(R.string.error_end_time_after_start)
    LessonTimeError.Overlap -> stringResource(R.string.error_time_overlap_lesson_time)
}

/**
 * 带倒计时的撤销提示。
 *
 * Compose 的 Snackbar 只有「4 秒 / 10 秒 / 一直显示」三档，不会画倒计时；
 * 这里用 Indefinite 自己跑一条进度条，进度走完再 dismiss，用户能看到还剩多少时间。
 */
@Composable
private fun UndoSnackbar(
    data: SnackbarData,
    onAction: () -> Unit,
    onTimeout: () -> Unit,
) {
    val progress = remember { Animatable(1f) }
    // data 每次 showSnackbar 都是新实例，连续删两节也会重新开始倒计时。
    LaunchedEffect(data) {
        progress.snapTo(1f)
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = UNDO_TIMEOUT_MILLIS,
                easing = LinearEasing,
            ),
        )
        onTimeout()
    }

    // 进度条画在 Snackbar 的底边上、叠在它上面，不参与排版：
    // 塞进内容里会把文字往上顶，还不让提示条变高。
    Box(
        modifier = Modifier
            // 官方 Snackbar 自己不处理边距：内容一接管就丢了默认 16dp，这里补回来。
            // 底部让开导航栏的事交给 SnackbarHost 的 navigationBarsPadding()。
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(SnackbarDefaults.shape),
    ) {
        Snackbar(
            // 底色用 surface，和页面内容同一色系：文字 onSurface、操作项和进度条用 primary。
            // 试过 secondaryContainer，整条偏灰绿，观感不如直接跟页面同色系。
            // 默认的 inverseSurface 在深色模式下是浅底配浅字，读不清。
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionContentColor = MaterialTheme.colorScheme.primary,
            action = {
                data.visuals.actionLabel?.let { label ->
                    TextButton(
                        onClick = onAction,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(label)
                    }
                }
            },
        ) {
            Text(data.visuals.message)
        }
        LinearProgressIndicator(
            progress = { progress.value },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent,
            // 新规范会在轨道末端画一个「停止点」，2dp 的细线上就是一颗突兀的小圆点。
            drawStopIndicator = {},
        )
    }
}
