package com.star.schedule.feature.timetable.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.core.database.entity.CourseEntity
import com.star.schedule.core.database.entity.CourseSessionEntity
import com.star.schedule.core.designsystem.component.AppMenu
import com.star.schedule.core.designsystem.component.CircleIconButton
import com.star.schedule.core.designsystem.component.CountdownSnackbar
import com.star.schedule.core.designsystem.component.ElasticSwipeRow
import com.star.schedule.core.designsystem.component.MenuEntry
import com.star.schedule.core.designsystem.component.PageContentTopSpacing
import com.star.schedule.core.designsystem.component.PageTopBar
import com.star.schedule.core.designsystem.component.SettingChevron
import com.star.schedule.core.designsystem.component.UNDO_SNACKBAR_TIMEOUT_MILLIS
import com.star.schedule.core.designsystem.component.morphedSegmentedShapes
import com.star.schedule.core.designsystem.component.rememberElasticSwipeState
import com.star.schedule.core.designsystem.navigationBarBottomInset
import com.star.schedule.feature.schedule.presentation.EmptyTimetableState
import com.star.schedule.feature.schedule.presentation.TimetableEmptyState
import com.star.schedule.feature.settings.presentation.settingsItemColors
import com.star.schedule.feature.timetable.domain.CourseRules
import com.star.schedule.feature.timetable.domain.CourseRules.WeekParity
import com.star.schedule.feature.timetable.presentation.components.DeleteWholeCourseConfirmDialog
import com.star.schedule.feature.timetable.presentation.components.RenameCourseDialog

private val LIST_HORIZONTAL_PADDING = 16.dp

@Composable
fun CourseSettingsRoute(
    viewModel: CourseSettingsViewModel,
    onBack: () -> Unit,
    onOpenLessonTimes: () -> Unit,
    onAddCourse: () -> Unit,
    onAddSession: (CourseRules.CourseGroup) -> Unit,
    onEditSession: (courseId: Long, sessionId: Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CourseSettingsScreen(
        state = state,
        onBack = onBack,
        onOpenLessonTimes = onOpenLessonTimes,
        onAddCourse = onAddCourse,
        onAddSession = onAddSession,
        onEditSession = onEditSession,
        onToggleSearch = viewModel::toggleSearch,
        onSearchQueryChange = viewModel::updateSearchQuery,
        onClearSearchQuery = viewModel::clearSearchQuery,
        onDeleteSession = viewModel::deleteSession,
        onStartRenameCourse = viewModel::startRenameCourse,
        onRenameCourseNameChange = viewModel::updateRenameCourseName,
        onConfirmRenameCourse = viewModel::confirmRenameCourse,
        onDismissRenameCourse = viewModel::dismissRenameCourse,
        onRequestDeleteCourse = viewModel::requestDeleteCourse,
        onConfirmDeleteCourse = viewModel::confirmDeleteCourse,
        onDismissDeleteCourse = viewModel::dismissDeleteCourse,
        onUndoDelete = viewModel::undoDelete,
        onDismissUndo = viewModel::dismissUndo,
    )
}

/**
 * 课程设置页：按课程名聚合显示主表课程及其上课时段，支持组头重命名/删整门课、单时段左滑撤销删除。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CourseSettingsScreen(
    state: CourseSettingsUiState,
    onBack: () -> Unit,
    onOpenLessonTimes: () -> Unit,
    onAddCourse: () -> Unit,
    onAddSession: (CourseRules.CourseGroup) -> Unit,
    onEditSession: (courseId: Long, sessionId: Long) -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearchQuery: () -> Unit,
    onDeleteSession: (CourseEntity, CourseSessionEntity) -> Unit,
    onStartRenameCourse: (CourseEntity) -> Unit,
    onRenameCourseNameChange: (String) -> Unit,
    onConfirmRenameCourse: () -> Unit,
    onDismissRenameCourse: () -> Unit,
    onRequestDeleteCourse: (CourseRules.CourseGroup) -> Unit,
    onConfirmDeleteCourse: () -> Unit,
    onDismissDeleteCourse: () -> Unit,
    onUndoDelete: (Long) -> Unit,
    onDismissUndo: (Long) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navigationBarInset = navigationBarBottomInset()
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)
    val undo = state.undo
    val undoMessage = when (undo) {
        is CourseUndoState.DeletedSession ->
            stringResource(R.string.course_session_deleted, undo.courseName)
        is CourseUndoState.DeletedCourse ->
            stringResource(R.string.course_whole_deleted, undo.courseName)
        null -> null
    }

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
                    timeoutMillis = UNDO_SNACKBAR_TIMEOUT_MILLIS,
                )
            }
        },
        topBar = {
            PageTopBar(
                title = stringResource(R.string.timetable_settings_courses),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = {
                    if (!state.isLoading && state.hasLessonTimes) {
                        if (state.totalCourseCount > 0) {
                            CircleIconButton(
                                icon = if (state.searchActive) {
                                    Icons.Rounded.SearchOff
                                } else {
                                    Icons.Rounded.Search
                                },
                                contentDescription = stringResource(R.string.content_desc_search_courses),
                                onClick = onToggleSearch,
                                containerColor = if (state.searchActive) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                                },
                                contentColor = if (state.searchActive) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        CircleIconButton(
                            icon = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.courses_settings_add),
                            onClick = onAddCourse,
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

                state.totalCourseCount == 0 -> {
                    EmptyTimetableState(
                        state = TimetableEmptyState.NO_COURSES,
                        modifier = Modifier.fillMaxSize(),
                        titleResId = R.string.courses_settings_empty_hint,
                        action = {
                            Button(
                                onClick = onAddCourse,
                                shapes = ButtonDefaults.shapes(),
                            ) {
                                Text(stringResource(R.string.courses_settings_add))
                            }
                        },
                    )
                }

                else -> {
                    val layoutDirection = LocalLayoutDirection.current
                    val swipeStates = state.groups.associate { group ->
                        group.course.id to key(group.course.id) { rememberElasticSwipeState() }
                    }
                    val searchFocusRequester = remember { FocusRequester() }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = innerPadding.calculateStartPadding(layoutDirection),
                                end = innerPadding.calculateEndPadding(layoutDirection),
                                top = innerPadding.calculateTopPadding(),
                            )
                            .consumeWindowInsets(innerPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AnimatedVisibility(
                            visible = state.searchActive,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            LaunchedEffect(Unit) {
                                searchFocusRequester.requestFocus()
                            }
                            OutlinedTextField(
                                value = state.searchQuery,
                                onValueChange = onSearchQueryChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 720.dp)
                                    .padding(
                                        start = LIST_HORIZONTAL_PADDING,
                                        end = LIST_HORIZONTAL_PADDING,
                                        top = PageContentTopSpacing,
                                        bottom = 8.dp,
                                    )
                                    .focusRequester(searchFocusRequester),
                                placeholder = { Text(stringResource(R.string.courses_search_hint)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                    )
                                },
                                trailingIcon = {
                                    if (state.searchQuery.isNotEmpty()) {
                                        IconButton(onClick = onClearSearchQuery) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(
                                                    R.string.content_desc_clear_search,
                                                ),
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(28.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceBright,
                                ),
                            )
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 720.dp)
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                            contentPadding = PaddingValues(
                                top = if (state.searchActive) 4.dp else PageContentTopSpacing,
                                bottom = innerPadding.calculateBottomPadding() + 24.dp + navigationBarInset,
                            ),
                        ) {
                            if (state.groups.isEmpty()) {
                                item(key = "search_empty", contentType = "searchEmpty") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp, vertical = 48.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Text(
                                            text = stringResource(R.string.courses_search_empty),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                        )
                                        FilledTonalButton(
                                            onClick = onClearSearchQuery,
                                            shapes = ButtonDefaults.shapes(),
                                        ) {
                                            Text(stringResource(R.string.courses_search_clear))
                                        }
                                    }
                                }
                            } else {
                                state.groups.forEachIndexed { groupIndex, group ->
                                    item(
                                        key = "header_${group.course.id}",
                                        contentType = "courseGroupHeader",
                                    ) {
                                        CourseGroupHeader(
                                            name = group.name,
                                            sessionCount = group.sessions.size,
                                            topPadding = if (groupIndex == 0) 0.dp else 20.dp,
                                            onAddSession = { onAddSession(group) },
                                            onRenameCourse = { onStartRenameCourse(group.course) },
                                            onDeleteCourse = { onRequestDeleteCourse(group) },
                                        )
                                    }

                                    if (group.sessions.isEmpty()) {
                                        item(
                                            key = "empty_${group.course.id}",
                                            contentType = "courseEmptySessionRow",
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(horizontal = LIST_HORIZONTAL_PADDING),
                                            ) {
                                                EmptyCourseSessionsItem(
                                                    onClick = { onAddSession(group) },
                                                )
                                            }
                                        }
                                    } else {
                                        val groupSwipeState = swipeStates.getValue(group.course.id)
                                        itemsIndexed(
                                            items = group.sessions,
                                            key = { _, session -> session.session.id },
                                            contentType = { _, _ -> "courseSessionRow" },
                                        ) { index, sessionItem ->
                                            ElasticSwipeRow(
                                                index = index,
                                                state = groupSwipeState,
                                                onDelete = {
                                                    onDeleteSession(
                                                        sessionItem.course,
                                                        sessionItem.session,
                                                    )
                                                },
                                                modifier = Modifier
                                                    .padding(horizontal = LIST_HORIZONTAL_PADDING)
                                                    .animateItem(fadeOutSpec = null),
                                            ) { info ->
                                                val morph = info.morph
                                                val shapes = if (morph > 0.001f && info.morphSide != 0) {
                                                    morphedSegmentedShapes(
                                                        shapeIndex = index,
                                                        shapeCount = group.sessions.size,
                                                        topMorph = if (info.morphSide < 0) morph else 0f,
                                                        bottomMorph = if (info.morphSide > 0) morph else 0f,
                                                    )
                                                } else {
                                                    null
                                                }
                                                CourseSessionListItem(
                                                    sessionItem = sessionItem,
                                                    shapeIndex = index,
                                                    shapeCount = group.sessions.size,
                                                    shapes = shapes,
                                                    onClick = {
                                                        onEditSession(
                                                            sessionItem.course.id,
                                                            sessionItem.session.id,
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val renaming = state.renamingCourse
    if (renaming != null) {
        RenameCourseDialog(
            initialName = renaming.name,
            error = renaming.error,
            isSaving = renaming.isSaving,
            onNameChange = onRenameCourseNameChange,
            onConfirm = onConfirmRenameCourse,
            onDismiss = onDismissRenameCourse,
        )
    }

    val deleting = state.deletingCourse
    if (deleting != null) {
        DeleteWholeCourseConfirmDialog(
            courseName = deleting.course.name,
            sessionCount = deleting.sessions.size,
            onConfirm = onConfirmDeleteCourse,
            onDismiss = onDismissDeleteCourse,
        )
    }
}

@Composable
private fun CourseGroupHeader(
    name: String,
    sessionCount: Int,
    topPadding: Dp,
    onAddSession: () -> Unit,
    onRenameCourse: () -> Unit,
    onDeleteCourse: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = topPadding, end = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.courses_group_session_count, sessionCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(
            onClick = onAddSession,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.defaultMinSize(minHeight = 32.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.courses_group_add_session),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.courses_group_more),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AppMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                groups = listOf(
                    listOf(
                        MenuEntry(
                            label = stringResource(R.string.courses_group_rename),
                            leadingIcon = Icons.Rounded.Edit,
                            onClick = {
                                menuExpanded = false
                                onRenameCourse()
                            },
                        ),
                        MenuEntry(
                            label = stringResource(R.string.courses_group_delete),
                            leadingIcon = Icons.Rounded.DeleteOutline,
                            onClick = {
                                menuExpanded = false
                                onDeleteCourse()
                            },
                        ),
                    ),
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyCourseSessionsItem(
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(0, 1),
        colors = settingsItemColors(),
        verticalAlignment = Alignment.CenterVertically,
        content = {
            Text(
                text = stringResource(R.string.courses_group_empty_sessions_title),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        supportingContent = {
            Text(stringResource(R.string.courses_group_empty_sessions_support))
        },
        trailingContent = { SettingChevron() },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CourseSessionListItem(
    sessionItem: CourseRules.CourseSessionItem,
    shapeIndex: Int,
    shapeCount: Int,
    shapes: ListItemShapes?,
    onClick: () -> Unit,
) {
    val session = sessionItem.session
    val resolvedShapes = shapes ?: ListItemDefaults.segmentedShapes(shapeIndex, shapeCount)
    val weekdayBadge = weekdayLabel(session.dayOfWeek)
    val periodsText = CourseRules.formatCompactRanges(sessionItem.sortedPeriods)
    val titleText = sessionItem.timeRange?.let { (start, end) ->
        stringResource(R.string.course_periods_with_time, periodsText, start, end)
    } ?: stringResource(R.string.course_periods_short, periodsText)

    val weeksSummary = CourseRules.formatWeeksSummary(sessionItem.sortedWeeks)
    val weeksText = when (weeksSummary?.parity) {
        WeekParity.ODD -> stringResource(R.string.course_weeks_odd, weeksSummary.rangeText)
        WeekParity.EVEN -> stringResource(R.string.course_weeks_even, weeksSummary.rangeText)
        WeekParity.ALL -> stringResource(R.string.course_weeks_range, weeksSummary.rangeText)
        null -> null
    }
    val detailParts = buildList {
        if (!weeksText.isNullOrBlank()) add(weeksText)
        val location = session.location.trim()
        if (location.isNotEmpty()) add(location)
        val teacher = session.teacher.trim()
        if (teacher.isNotEmpty()) add(teacher)
    }

    SegmentedListItem(
        onClick = onClick,
        shapes = resolvedShapes,
        colors = settingsItemColors(),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 44.dp, minHeight = 36.dp)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = weekdayBadge,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        },
        content = {
            Text(
                text = titleText,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = if (detailParts.isNotEmpty()) {
            {
                Text(
                    text = detailParts.joinToString(" · "),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            null
        },
        trailingContent = if (sessionItem.conflictsWithNames.isNotEmpty()) {
            {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Text(
                        text = stringResource(R.string.course_conflict_badge),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun weekdayLabel(dayOfWeek: Int): String {
    val short = when (dayOfWeek) {
        1 -> stringResource(R.string.weekday_short_monday)
        2 -> stringResource(R.string.weekday_short_tuesday)
        3 -> stringResource(R.string.weekday_short_wednesday)
        4 -> stringResource(R.string.weekday_short_thursday)
        5 -> stringResource(R.string.weekday_short_friday)
        6 -> stringResource(R.string.weekday_short_saturday)
        7 -> stringResource(R.string.weekday_short_sunday)
        else -> "?"
    }
    return stringResource(R.string.weekday_column_label, short)
}
