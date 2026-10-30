package com.star.schedule.feature.timetable.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.core.designsystem.color.successColors
import com.star.schedule.core.designsystem.component.CircleIconButton
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
import com.star.schedule.feature.timetable.presentation.components.DiscardTemplateChangesDialog
import com.star.schedule.feature.timetable.presentation.components.LessonTimeEditorSheet
import com.star.schedule.feature.timetable.presentation.components.RenameTemplateDialog
import com.star.schedule.feature.timetable.presentation.components.SaveTemplateChoiceDialog
import com.star.schedule.feature.timetable.presentation.components.lessonTimeSupportingText
import kotlin.math.abs

private val LIST_HORIZONTAL_PADDING = 16.dp

@Composable
fun LessonTimeTemplateEditRoute(
    viewModel: LessonTimeTemplateEditViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 保存成功或确认放弃后由 ViewModel 置 finish，页面只负责关掉自己。
    LaunchedEffect(state.finish) {
        if (state.finish) onClose()
    }
    // 系统返回和顶栏返回走同一条路：有未保存改动时先问一句。
    BackHandler { viewModel.requestBack() }

    LessonTimeTemplateEditScreen(
        state = state,
        onBack = viewModel::requestBack,
        onSave = viewModel::requestSave,
        onOverwrite = viewModel::save,
        onSaveAsNew = viewModel::saveAsNew,
        onDismissSaveChoice = viewModel::dismissSaveChoice,
        onAdd = viewModel::startAdd,
        onEditRow = viewModel::startEdit,
        onDeleteRow = viewModel::delete,
        onUndoDelete = viewModel::undoDelete,
        onDismissUndo = viewModel::dismissUndo,
        onSelectField = viewModel::selectField,
        onSetTime = viewModel::setTime,
        onDismissEditor = viewModel::dismissEditor,
        onSaveEditor = viewModel::saveEditor,
        onStartRename = viewModel::startRename,
        onRenameChange = viewModel::updateRenameName,
        onConfirmRename = viewModel::confirmRename,
        onDismissRename = viewModel::dismissRename,
        onDismissDiscard = viewModel::dismissDiscard,
        onConfirmDiscard = viewModel::confirmDiscard,
        onMessageShown = viewModel::onMessageShown,
    )
}

/**
 * 编辑模板：改名字 + 改节次，改动先落在草稿里，点顶栏的勾才写库。
 *
 * 列表交互和节次时间页完全相同（点行改时间、左滑删除、删完可撤销），
 * 差别只在保存时机：这一页是「改完一起保存」，所以返回前会拦一次未保存的改动。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LessonTimeTemplateEditScreen(
    state: LessonTimeTemplateEditUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOverwrite: () -> Unit,
    onSaveAsNew: () -> Unit,
    onDismissSaveChoice: () -> Unit,
    onAdd: () -> Unit,
    onEditRow: (LessonTimeRow) -> Unit,
    onDeleteRow: (LessonTimeRow) -> Unit,
    onUndoDelete: (Long) -> Unit,
    onDismissUndo: (Long) -> Unit,
    onSelectField: (LessonTimeField) -> Unit,
    onSetTime: (LessonTimeField, Int) -> Unit,
    onDismissEditor: () -> Unit,
    onSaveEditor: () -> Unit,
    onStartRename: () -> Unit,
    onRenameChange: (String) -> Unit,
    onConfirmRename: () -> Unit,
    onDismissRename: () -> Unit,
    onDismissDiscard: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onMessageShown: (Long) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navigationBarInset = navigationBarBottomInset()
    val snackbarHostState = remember { SnackbarHostState() }
    val swipeState = rememberElasticSwipeState()
    val success = successColors()
    val undoLabel = stringResource(R.string.action_undo)
    val undo = state.undo
    val undoMessage = undo?.let { stringResource(R.string.lesson_time_deleted, it.period) }
    val message = state.message
    val messageText = message?.let { stringResource(it.type.textRes) }

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
                title = stringResource(R.string.title_edit_lesson_time_template),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    CircleIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.title_add_template_period),
                        onClick = onAdd,
                    )
                    // 保存用 success 绿，和「已完成」的语义一致；点开还要选覆盖还是新建。
                    CircleIconButton(
                        icon = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.action_save),
                        onClick = onSave,
                        containerColor = success.container,
                        contentColor = success.content,
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

                state.isMissing -> {
                    EmptyTimetableState(
                        state = TimetableEmptyState.NO_LESSON_TIMES,
                        modifier = Modifier.fillMaxSize(),
                        titleResId = R.string.lesson_time_template_missing,
                        action = {
                            Button(onClick = onBack) {
                                Text(stringResource(R.string.action_back))
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
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        contentPadding = PaddingValues(
                            top = PageContentTopSpacing,
                            bottom = 24.dp + navigationBarInset,
                        ),
                    ) {
                        item(key = "name") {
                            SettingsListItem(
                                title = stringResource(R.string.label_template_name),
                                supporting = state.name,
                                onClick = onStartRename,
                                shapeIndex = 0,
                                shapeCount = 1,
                                modifier = Modifier.padding(horizontal = LIST_HORIZONTAL_PADDING),
                            )
                        }

                        // 名称和节次是两组：中间空一拍，读起来才知道下面是节次列表。
                        item(key = "gap") { Spacer(Modifier.height(8.dp)) }

                        if (state.rows.isEmpty()) {
                            item(key = "empty") {
                                SettingsListItem(
                                    title = stringResource(R.string.lesson_time_template_periods_empty),
                                    supporting = stringResource(R.string.lesson_time_template_periods_empty_hint),
                                    shapeIndex = 0,
                                    shapeCount = 1,
                                    modifier = Modifier.padding(horizontal = LIST_HORIZONTAL_PADDING),
                                )
                            }
                        }

                        itemsIndexed(
                            items = state.rows,
                            key = { _, row -> row.id },
                        ) { index, row ->
                            ElasticSwipeRow(
                                index = index,
                                state = swipeState,
                                onDelete = { onDeleteRow(row) },
                                modifier = Modifier
                                    .padding(horizontal = LIST_HORIZONTAL_PADDING)
                                    .animateItem(),
                            ) { info ->
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
                                    supporting = lessonTimeSupportingText(
                                        row = row,
                                        previous = state.rows.getOrNull(index - 1),
                                    ),
                                    onClick = { onEditRow(row) },
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
            onConfirm = onSaveEditor,
            errorText = editor.error?.let { error -> lessonTimeErrorText(error) },
            confirmEnabled = !editor.isSaving,
        )
    }

    val rename = state.rename
    if (rename != null) {
        RenameTemplateDialog(
            state = rename,
            onNameChange = onRenameChange,
            onConfirm = onConfirmRename,
            onDismiss = onDismissRename,
        )
    }

    if (state.saveChoice) {
        SaveTemplateChoiceDialog(
            templateName = state.name,
            canOverwrite = state.isDirty,
            isSaving = state.isSaving,
            onOverwrite = onOverwrite,
            onSaveAsNew = onSaveAsNew,
            onDismiss = onDismissSaveChoice,
        )
    }

    if (state.discardConfirm) {
        DiscardTemplateChangesDialog(
            onDiscard = onConfirmDiscard,
            onKeepEditing = onDismissDiscard,
        )
    }
}

@Composable
private fun lessonTimeErrorText(error: LessonTimeError): String = when (error) {
    LessonTimeError.InvalidTime -> stringResource(R.string.error_lesson_time_invalid)
    LessonTimeError.EndNotAfterStart -> stringResource(R.string.error_end_time_after_start)
    LessonTimeError.Overlap -> stringResource(R.string.error_time_overlap_lesson_time)
}

private val LessonTimeTemplateEditMessageType.textRes: Int
    get() = when (this) {
        LessonTimeTemplateEditMessageType.SaveFailed -> R.string.toast_template_edit_save_failed
    }
