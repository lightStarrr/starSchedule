package com.star.schedule.feature.timetable.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.CircleIconButton
import com.star.schedule.core.designsystem.component.CountdownSnackbar
import com.star.schedule.core.designsystem.component.MESSAGE_SNACKBAR_TIMEOUT_MILLIS
import com.star.schedule.core.designsystem.component.PageContentTopSpacing
import com.star.schedule.core.designsystem.component.UNDO_SNACKBAR_TIMEOUT_MILLIS
import com.star.schedule.core.designsystem.component.ElasticSwipeRow
import com.star.schedule.core.designsystem.component.PageTopBar
import com.star.schedule.core.designsystem.component.morphedSegmentedShapes
import com.star.schedule.core.designsystem.component.rememberElasticSwipeState
import com.star.schedule.core.designsystem.navigationBarBottomInset
import com.star.schedule.feature.schedule.presentation.EmptyTimetableState
import com.star.schedule.feature.schedule.presentation.TimetableEmptyState
import com.star.schedule.feature.settings.presentation.SettingsListItem
import com.star.schedule.feature.timetable.domain.LessonTimeRules
import com.star.schedule.feature.timetable.presentation.components.LessonTimeEditorSheet
import com.star.schedule.feature.timetable.presentation.components.lessonTimeSupportingText
import com.star.schedule.feature.timetable.presentation.components.OverwriteLessonTimeTemplateDialog
import com.star.schedule.feature.timetable.presentation.components.SaveLessonTimeTemplateDialog
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt


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
        onStartSaveTemplate = viewModel::startSaveTemplate,
        onTemplateNameChange = viewModel::updateTemplateName,
        onConfirmSaveTemplate = viewModel::confirmSaveTemplate,
        onDismissSaveTemplate = viewModel::dismissSaveTemplate,
        onConfirmOverwriteTemplate = viewModel::confirmOverwriteTemplate,
        onDismissOverwriteTemplate = viewModel::dismissOverwriteTemplate,
        onMessageShown = viewModel::onMessageShown,
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
    onUndoDelete: (Long) -> Unit,
    onDismissUndo: (Long) -> Unit,
    onSelectField: (LessonTimeField) -> Unit,
    onSetTime: (LessonTimeField, Int) -> Unit,
    onDismissEditor: () -> Unit,
    onSave: () -> Unit,
    onStartSaveTemplate: () -> Unit,
    onTemplateNameChange: (String) -> Unit,
    onConfirmSaveTemplate: () -> Unit,
    onDismissSaveTemplate: () -> Unit,
    onConfirmOverwriteTemplate: () -> Unit,
    onDismissOverwriteTemplate: () -> Unit,
    onMessageShown: (Long) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navigationBarInset = navigationBarBottomInset()
    val snackbarHostState = remember { SnackbarHostState() }
    // 滑动删除的共享状态：被拖的那行把进度写进去，相邻行据此被带着走一点。
    val swipeState = rememberElasticSwipeState()
    val undoLabel = stringResource(R.string.action_undo)
    val undo = state.undo
    val undoMessage = undo?.let { stringResource(R.string.lesson_time_deleted, it.period) }
    val message = state.message
    val messageText = message?.let { stringResource(it.type.textRes) }

    // 提示只负责展示和撤销：删除已经落库，撤销就是把那条原样插回去。
    // 用 Indefinite + 自己的倒计时，而不是 Duration.Short 的 4 秒：撤销要留出看得见的时间。
    //
    // key 必须是 token，不能是文案：删掉第 3 节后剩下的会被 AutoSort 重新编号，
    // 列表里紧接着的第 3 节再被删掉时文案和上一次一模一样，用文案做 key 则 effect 不重启——
    // 既不重开倒计时，旧协程走完时还会把这次删除的撤销数据清掉。
    // key 变化会取消旧协程：SnackbarHostState.showSnackbar 的 finally 会清空
    // currentSnackbarData 并释放内部 FIFO mutex，新提示立刻接上；被取消的协程也走不到
    // 下面的回调，不会误清新状态。
    LaunchedEffect(undo?.token) {
        if (undo == null || undoMessage == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = undoMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Indefinite,
        )
        // 带上去的是这次 effect 的 token：effect 不随重组更新闭包，读 state 会拿到过期值。
        if (result == SnackbarResult.ActionPerformed) {
            onUndoDelete(undo.token)
        } else {
            onDismissUndo(undo.token)
        }
    }

    // 保存模板之类的一次性提示：同样用 Indefinite，由通用提示条的倒计时负责收起，
    // 这样「已保存模板」和「撤销删除」是同一套视觉，不用两套计时逻辑。
    // 和撤销提示共用队列，同一时间只会显示一条。
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
            // 位置由 Scaffold 决定：没有 bottomBar/FAB 时它会把 snackbar 抬到
            // contentWindowInsets.bottom 之上。这一页为了列表能滑到导航栏下面把 inset 清零了，
            // 所以这里自己把导航栏 inset 加回去，别让提示压在手势条后面。
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            ) { data ->
                // 撤销要留出看得见的时间；纯结果提示（已保存模板等）看得清就够了。
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
                title = stringResource(R.string.timetable_settings_lesson_times),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    // 保存是「把当前这份存成模板」，模板页本身还是全局资产的入口，两个都留。
                    CircleIconButton(
                        icon = Icons.Rounded.BookmarkAdd,
                        contentDescription = stringResource(R.string.content_desc_save_lesson_time_template),
                        onClick = onStartSaveTemplate,
                    )
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
                                    title = stringResource(
                                        R.string.lesson_time_range,
                                        row.startTime,
                                        row.endTime,
                                    ),
                                    supporting = lessonTimeSupportingText(row, state.rows.getOrNull(index - 1)),
                                    onClick = { onEdit(row) },
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

    val saveTemplate = state.saveTemplate
    if (saveTemplate != null) {
        if (saveTemplate.confirmingOverwrite) {
            OverwriteLessonTimeTemplateDialog(
                name = saveTemplate.name,
                isSaving = saveTemplate.isSaving,
                onConfirm = onConfirmOverwriteTemplate,
                onDismiss = onDismissOverwriteTemplate,
            )
        } else {
            SaveLessonTimeTemplateDialog(
                state = saveTemplate,
                onNameChange = onTemplateNameChange,
                onConfirm = onConfirmSaveTemplate,
                onDismiss = onDismissSaveTemplate,
            )
        }
    }
}

/** 一次性提示的文案。提示类型是业务状态，字符串资源只在这一层出现。 */
private val LessonTimeMessageType.textRes: Int
    get() = when (this) {
        LessonTimeMessageType.TemplateSaved -> R.string.toast_template_saved
        LessonTimeMessageType.TemplateOverwritten -> R.string.toast_template_overwritten
        LessonTimeMessageType.NothingToSave -> R.string.toast_no_lessons_to_save_template
    }

@Composable
private fun lessonTimeErrorText(error: LessonTimeError): String = when (error) {
    LessonTimeError.InvalidTime -> stringResource(R.string.error_lesson_time_invalid)
    LessonTimeError.EndNotAfterStart -> stringResource(R.string.error_end_time_after_start)
    LessonTimeError.Overlap -> stringResource(R.string.error_time_overlap_lesson_time)
}
