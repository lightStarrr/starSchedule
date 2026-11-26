package com.star.schedule.feature.timetable.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonSize
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.core.designsystem.color.successColors
import com.star.schedule.core.designsystem.component.CircleIconButton
import com.star.schedule.core.designsystem.component.CompactPageTopBar
import com.star.schedule.core.designsystem.component.CountdownSnackbar
import com.star.schedule.core.designsystem.component.MESSAGE_SNACKBAR_TIMEOUT_MILLIS
import com.star.schedule.core.designsystem.component.PageContentTopSpacing
import com.star.schedule.core.designsystem.navigationBarBottomInset
import com.star.schedule.feature.schedule.presentation.EmptyTimetableState
import com.star.schedule.feature.schedule.presentation.TimetableEmptyState
import com.star.schedule.feature.settings.presentation.SettingsListItem
import com.star.schedule.feature.settings.presentation.settingsItemColors
import com.star.schedule.feature.timetable.domain.CourseRules
import com.star.schedule.feature.timetable.domain.CourseRules.CourseConflict
import com.star.schedule.feature.timetable.domain.CourseRules.CourseValidationError
import com.star.schedule.feature.timetable.presentation.components.CourseDragSelectGrid
import com.star.schedule.feature.timetable.presentation.components.CourseDragSelectGridItem
import com.star.schedule.feature.timetable.presentation.components.CourseWeekdayBar
import com.star.schedule.feature.timetable.presentation.components.DeleteCourseConfirmDialog
import com.star.schedule.feature.timetable.presentation.components.DiscardCourseChangesDialog

@Composable
fun CourseEditRoute(
    viewModel: CourseEditViewModel,
    onClose: () -> Unit,
    onOpenLessonTimes: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.finish) {
        if (state.finish) onClose()
    }
    BackHandler { viewModel.requestBack() }

    CourseEditScreen(
        state = state,
        onBack = viewModel::requestBack,
        onOpenLessonTimes = onOpenLessonTimes,
        onNameChange = viewModel::updateName,
        onTeacherChange = viewModel::updateTeacher,
        onLocationChange = viewModel::updateLocation,
        onSelectCourse = viewModel::selectCourse,
        onSelectDay = viewModel::selectDayOfWeek,
        onPeriodsChange = viewModel::updateSelectedPeriods,
        onWeeksChange = viewModel::updateSelectedWeeks,
        onSelectAllWeeks = viewModel::selectAllWeeks,
        onSelectOddWeeks = viewModel::selectOddWeeks,
        onSelectEvenWeeks = viewModel::selectEvenWeeks,
        onClearWeeks = viewModel::clearWeeks,
        onToggleExtendedWeeks = viewModel::toggleExtendedWeeks,
        onSave = { viewModel.save(addAnother = false) },
        onRequestDelete = viewModel::requestDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onDismissDelete = viewModel::dismissDelete,
        onConfirmDiscard = viewModel::confirmDiscard,
        onDismissDiscard = viewModel::dismissDiscard,
        onMessageShown = viewModel::onMessageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CourseEditScreen(
    state: CourseEditUiState,
    onBack: () -> Unit,
    onOpenLessonTimes: () -> Unit,
    onNameChange: (String) -> Unit,
    onTeacherChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onSelectCourse: (CourseNameSuggestion) -> Unit,
    onSelectDay: (Int) -> Unit,
    onPeriodsChange: (Set<Int>) -> Unit,
    onWeeksChange: (Set<Int>) -> Unit,
    onSelectAllWeeks: () -> Unit,
    onSelectOddWeeks: () -> Unit,
    onSelectEvenWeeks: () -> Unit,
    onClearWeeks: () -> Unit,
    onToggleExtendedWeeks: () -> Unit,
    onSave: () -> Unit,
    onRequestDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDiscard: () -> Unit,
    onDismissDiscard: () -> Unit,
    onMessageShown: (Long) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val navigationBarInset = navigationBarBottomInset()
    val snackbarHostState = remember { SnackbarHostState() }
    val success = successColors()
    val message = state.message
    val messageText = message?.let { stringResource(it.type.textRes) }

    LaunchedEffect(message?.token) {
        if (message == null || messageText == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = messageText,
            duration = SnackbarDuration.Indefinite,
        )
        onMessageShown(message.token)
    }

    val titleRes = when {
        !state.isNewSession -> R.string.title_edit_session
        state.showCourseSelectorMode -> R.string.title_add_session
        else -> R.string.title_add_course
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .imePadding()
                    .navigationBarsPadding(),
            ) { data ->
                CountdownSnackbar(
                    data = data,
                    timeoutMillis = MESSAGE_SNACKBAR_TIMEOUT_MILLIS,
                )
            }
        },
        topBar = {
            CompactPageTopBar(
                title = stringResource(titleRes),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    if (!state.isLoading && !state.isMissing && state.hasLessonTimes) {
                        CircleIconButton(
                            icon = Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.action_save),
                            onClick = onSave,
                            enabled = state.canSave,
                            containerColor = success.container,
                            contentColor = success.content,
                        )
                    }
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
                        state = TimetableEmptyState.NO_COURSES,
                        modifier = Modifier.fillMaxSize(),
                        titleResId = if (state.missingIsCourse) {
                            R.string.course_edit_missing
                        } else {
                            R.string.course_session_missing
                        },
                        action = {
                            Button(
                                onClick = onBack,
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(stringResource(R.string.action_back))
                            }
                        },
                    )
                }

                !state.hasLessonTimes -> {
                    EmptyTimetableState(
                        state = TimetableEmptyState.NO_LESSON_TIMES,
                        modifier = Modifier.fillMaxSize(),
                        titleResId = R.string.lesson_time_empty_hint,
                        action = {
                            Button(
                                onClick = onOpenLessonTimes,
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(stringResource(R.string.courses_settings_go_lesson_times))
                            }
                        },
                    )
                }

                else -> {
                    CourseEditFormContent(
                        state = state,
                        contentTopPadding = innerPadding.calculateTopPadding() + PageContentTopSpacing,
                        bottomInset = navigationBarInset,
                        onNameChange = onNameChange,
                        onTeacherChange = onTeacherChange,
                        onLocationChange = onLocationChange,
                        onSelectCourse = onSelectCourse,
                        onSelectDay = onSelectDay,
                        onPeriodsChange = onPeriodsChange,
                        onWeeksChange = onWeeksChange,
                        onSelectAllWeeks = onSelectAllWeeks,
                        onSelectOddWeeks = onSelectOddWeeks,
                        onSelectEvenWeeks = onSelectEvenWeeks,
                        onClearWeeks = onClearWeeks,
                        onToggleExtendedWeeks = onToggleExtendedWeeks,
                        onRequestDelete = onRequestDelete,
                    )
                }
            }
        }
    }

    if (state.showDiscardConfirm) {
        DiscardCourseChangesDialog(
            onDiscard = onConfirmDiscard,
            onKeepEditing = onDismissDiscard,
        )
    }

    if (state.showDeleteConfirm) {
        DeleteCourseConfirmDialog(
            courseName = state.name.ifBlank { stringResource(R.string.label_course_name) },
            isDeleting = state.isDeleting,
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDelete,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CourseEditFormContent(
    state: CourseEditUiState,
    contentTopPadding: Dp,
    bottomInset: Dp,
    onNameChange: (String) -> Unit,
    onTeacherChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onSelectCourse: (CourseNameSuggestion) -> Unit,
    onSelectDay: (Int) -> Unit,
    onPeriodsChange: (Set<Int>) -> Unit,
    onWeeksChange: (Set<Int>) -> Unit,
    onSelectAllWeeks: () -> Unit,
    onSelectOddWeeks: () -> Unit,
    onSelectEvenWeeks: () -> Unit,
    onClearWeeks: () -> Unit,
    onToggleExtendedWeeks: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val error = state.validationError
    val periodGridItems = remember(state.lessonTimes) {
        state.lessonTimes.map { time ->
            time.period to time.startTime
        }
    }.map { (period, startTime) ->
        CourseDragSelectGridItem(
            value = period,
            label = stringResource(R.string.course_period_cell_label, period),
            supporting = startTime,
        )
    }
    val maxWeek = if (state.showExtendedWeeks) {
        CourseRules.MAX_WEEK
    } else {
        CourseRules.DEFAULT_VISIBLE_WEEKS
    }
    val weekGridItems = remember(maxWeek) {
        (CourseRules.MIN_WEEK..maxWeek).map { week ->
            CourseDragSelectGridItem(
                value = week,
                label = week.toString(),
            )
        }
    }

    val cardShape = ListItemDefaults.segmentedShapes(0, 1).shape

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(scrollState)
                .imePadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = contentTopPadding,
                    bottom = 28.dp + bottomInset,
                ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 基本信息：采用 M3E 分段列表，第 1 项为所属课程/课程名，第 2 项为地点与教师输入框
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EditSectionHeader(title = stringResource(R.string.course_section_basic_info))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    if (state.showCourseSelectorMode) {
                        SettingsListItem(
                            title = stringResource(R.string.course_edit_belongs_to),
                            supporting = state.name,
                            shapeIndex = 0,
                            shapeCount = 2,
                        )
                    } else {
                        SegmentedListItem(
                            shapes = ListItemDefaults.segmentedShapes(0, 2),
                            colors = settingsItemColors(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                top = 10.dp,
                                end = 16.dp,
                                bottom = 16.dp,
                            ),
                            content = {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    OutlinedTextField(
                                        value = state.name,
                                        onValueChange = onNameChange,
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(stringResource(R.string.label_course_name)) },
                                        placeholder = { Text(stringResource(R.string.course_name_placeholder)) },
                                        singleLine = true,
                                        isError = error == CourseValidationError.EmptyName ||
                                            error == CourseValidationError.NameTooLong,
                                        supportingText = if (error == CourseValidationError.EmptyName ||
                                            error == CourseValidationError.NameTooLong
                                        ) {
                                            { Text(stringResource(error.textRes)) }
                                        } else {
                                            null
                                        },
                                    )

                                    if (state.availableCourses.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            state.availableCourses.forEach { suggestion ->
                                                SuggestionChip(
                                                    onClick = { onSelectCourse(suggestion) },
                                                    label = {
                                                        Text(
                                                            text = suggestion.name,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                        )
                    }

                    SegmentedListItem(
                        shapes = ListItemDefaults.segmentedShapes(1, 2),
                        colors = settingsItemColors(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 10.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                        ),
                        content = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                OutlinedTextField(
                                    value = state.location,
                                    onValueChange = onLocationChange,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(R.string.label_location_input)) },
                                    placeholder = { Text(stringResource(R.string.course_location_placeholder)) },
                                    singleLine = true,
                                    isError = error == CourseValidationError.LocationTooLong,
                                    supportingText = if (error == CourseValidationError.LocationTooLong) {
                                        { Text(stringResource(error.textRes)) }
                                    } else {
                                        null
                                    },
                                )

                                OutlinedTextField(
                                    value = state.teacher,
                                    onValueChange = onTeacherChange,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(R.string.course_teacher_label)) },
                                    placeholder = { Text(stringResource(R.string.course_teacher_placeholder)) },
                                    singleLine = true,
                                    isError = error == CourseValidationError.TeacherTooLong,
                                    supportingText = if (error == CourseValidationError.TeacherTooLong) {
                                        { Text(stringResource(error.textRes)) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        },
                    )
                }
            }

            // 上课星期（ToggleButton(Small) 自带 4dp 触控上下留白，用 4dp 间距对齐视觉 8dp）
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EditSectionHeader(title = stringResource(R.string.course_section_weekday))
                CourseWeekdayBar(
                    selectedDayOfWeek = state.dayOfWeek,
                    onSelectDay = onSelectDay,
                )
            }

            // 上课节次（支持点按与长按拖动连选）
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val periodsError = error == CourseValidationError.EmptyPeriods ||
                    error == CourseValidationError.InvalidPeriod
                EditSectionHeader(title = stringResource(R.string.course_section_periods))
                Surface(
                    shape = cardShape,
                    color = MaterialTheme.colorScheme.surfaceBright,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CourseDragSelectGrid(
                            items = periodGridItems,
                            selected = state.selectedPeriods,
                            columns = 4,
                            onSelectionChange = onPeriodsChange,
                        )
                        Text(
                            text = if (periodsError) {
                                stringResource(error.textRes)
                            } else {
                                stringResource(R.string.course_drag_select_hint)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (periodsError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }

            // 上课周次（支持快捷选择 + 点按与长按拖动连选，补偿内部按钮留白以对齐上下 16dp 与内间距 12dp）
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val weeksError = error == CourseValidationError.EmptyWeeks
                EditSectionHeader(title = stringResource(R.string.course_section_weeks))
                Surface(
                    shape = cardShape,
                    color = MaterialTheme.colorScheme.surfaceBright,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 10.dp),
                    ) {
                        WeekQuickActionRow(
                            selectedWeeks = state.selectedWeeks,
                            visibleMaxWeek = maxWeek,
                            onSelectAll = onSelectAllWeeks,
                            onSelectOdd = onSelectOddWeeks,
                            onSelectEven = onSelectEvenWeeks,
                            onClear = onClearWeeks,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CourseDragSelectGrid(
                            items = weekGridItems,
                            selected = state.selectedWeeks,
                            columns = 5,
                            onSelectionChange = onWeeksChange,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = if (weeksError) {
                                    stringResource(error.textRes)
                                } else {
                                    stringResource(R.string.course_drag_select_hint)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (weeksError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(
                                        role = Role.Button,
                                        onClick = onToggleExtendedWeeks,
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(
                                        if (state.showExtendedWeeks) {
                                            R.string.course_weeks_collapse
                                        } else {
                                            R.string.course_weeks_expand
                                        },
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = if (state.showExtendedWeeks) {
                                        Icons.Rounded.ExpandLess
                                    } else {
                                        Icons.Rounded.ExpandMore
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            // 排课冲突提醒（不强行拦截保存）
            AnimatedVisibility(
                visible = state.conflicts.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                CourseConflictBanner(
                    conflicts = state.conflicts,
                    shape = cardShape,
                )
            }

            // 底部仅在编辑已有时段时提供删除操作；保存统一放在右上角
            if (!state.isNewSession) {
                FilledTonalButton(
                    onClick = onRequestDelete,
                    enabled = !state.isSaving && !state.isDeleting,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_delete))
                }
            }
        }
    }
}

@Composable
private fun EditSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WeekQuickActionRow(
    selectedWeeks: Set<Int>,
    visibleMaxWeek: Int,
    onSelectAll: () -> Unit,
    onSelectOdd: () -> Unit,
    onSelectEven: () -> Unit,
    onClear: () -> Unit,
) {
    val allSet = remember(visibleMaxWeek) { (CourseRules.MIN_WEEK..visibleMaxWeek).toSet() }
    val oddSet = remember(visibleMaxWeek) {
        (CourseRules.MIN_WEEK..visibleMaxWeek).filter { it % 2 != 0 }.toSet()
    }
    val evenSet = remember(visibleMaxWeek) {
        (CourseRules.MIN_WEEK..visibleMaxWeek).filter { it % 2 == 0 }.toSet()
    }

    val items = listOf(
        Triple(stringResource(R.string.course_weeks_quick_all), selectedWeeks == allSet, onSelectAll),
        Triple(stringResource(R.string.course_weeks_quick_odd), selectedWeeks == oddSet, onSelectOdd),
        Triple(stringResource(R.string.course_weeks_quick_even), selectedWeeks == evenSet, onSelectEven),
        Triple(stringResource(R.string.course_weeks_quick_clear), selectedWeeks.isEmpty(), onClear),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        items.forEachIndexed { index, (label, checked, action) ->
            val shapes = when (index) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                items.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = checked,
                onCheckedChange = { action() },
                buttonSize = ToggleButtonSize.Small,
                shapes = shapes,
                colors = ToggleButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = label,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CourseConflictBanner(
    conflicts: List<CourseConflict>,
    shape: Shape,
) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                modifier = Modifier.padding(top = 2.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.course_edit_conflict_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                conflicts.take(3).forEach { conflict ->
                    val periodsText = CourseRules.formatCompactRanges(conflict.overlapPeriods)
                    val weeksText = CourseRules.formatCompactRanges(conflict.overlapWeeks)
                    Text(
                        text = stringResource(
                            R.string.course_edit_conflict_item,
                            conflict.courseName,
                            periodsText,
                            weeksText,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private val CourseValidationError.textRes: Int
    get() = when (this) {
        CourseValidationError.EmptyName -> R.string.error_course_name_required
        CourseValidationError.NameTooLong -> R.string.error_course_name_too_long
        CourseValidationError.TeacherTooLong -> R.string.error_teacher_too_long
        CourseValidationError.LocationTooLong -> R.string.error_location_too_long
        CourseValidationError.EmptyPeriods -> R.string.error_periods_empty
        CourseValidationError.InvalidPeriod -> R.string.error_periods_invalid_selection
        CourseValidationError.EmptyWeeks -> R.string.error_weeks_empty
    }

private val CourseEditMessageType.textRes: Int
    get() = when (this) {
        CourseEditMessageType.SavedAddNext -> R.string.course_edit_saved_add_next
        CourseEditMessageType.SaveFailed -> R.string.toast_template_edit_save_failed
        CourseEditMessageType.DeleteFailed -> R.string.error_unknown
    }
