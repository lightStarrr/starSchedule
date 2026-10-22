package com.star.schedule.feature.timetable.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    LaunchedEffect(deletedMessage) {
        if (deletedMessage == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedMessage,
            actionLabel = undoLabel,
        )
        if (result == SnackbarResult.ActionPerformed) onUndoDelete() else onDismissUndo()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
