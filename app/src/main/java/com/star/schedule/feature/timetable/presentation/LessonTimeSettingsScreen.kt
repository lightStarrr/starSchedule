package com.star.schedule.feature.timetable.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.CircleIconButton
import com.star.schedule.core.designsystem.component.PageTopBar
import com.star.schedule.feature.timetable.presentation.components.LessonTimeEditorSheet
import com.star.schedule.feature.timetable.presentation.components.LessonTimeField
import java.util.Locale

/** 新建一节时的默认时长，和常见 45 分钟一节课对齐。 */
private const val DEFAULT_START_MINUTES = 8 * 60
private const val DEFAULT_END_MINUTES = DEFAULT_START_MINUTES + 45

/**
 * 节次时间。
 *
 * 列表还没做，页面内容留空；顶栏右侧是「模板」和「新增」两个纯图标按钮，
 * 新增打开之前保留的编辑浮层——浮层里能选开始/结束时间，但保存还没有接数据库。
 */
@Composable
fun LessonTimeSettingsRoute(
    onBack: () -> Unit,
    onOpenTemplates: () -> Unit,
) {
    var editorVisible by rememberSaveable { mutableStateOf(false) }
    var startTime by rememberSaveable { mutableStateOf(formatTime(DEFAULT_START_MINUTES)) }
    var endTime by rememberSaveable { mutableStateOf(formatTime(DEFAULT_END_MINUTES)) }
    var editingField by rememberSaveable { mutableStateOf(LessonTimeField.Start) }

    LessonTimeSettingsScreen(
        onBack = onBack,
        onOpenTemplates = onOpenTemplates,
        onAdd = {
            // 每次打开都从默认时间起步：保存还没接，不要把上一次的草稿带进来。
            startTime = formatTime(DEFAULT_START_MINUTES)
            endTime = formatTime(DEFAULT_END_MINUTES)
            editingField = LessonTimeField.Start
            editorVisible = true
        },
    )

    if (editorVisible) {
        LessonTimeEditorSheet(
            title = stringResource(R.string.title_add_lesson_time),
            startTime = startTime,
            endTime = endTime,
            activeField = editingField,
            onSelectField = { editingField = it },
            onTimeChange = { field, hour, minute ->
                val text = formatTime(hour * 60 + minute)
                when (field) {
                    LessonTimeField.Start -> startTime = text
                    LessonTimeField.End -> endTime = text
                }
            },
            onDismiss = { editorVisible = false },
            onConfirm = { editorVisible = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonTimeSettingsScreen(
    onBack: () -> Unit,
    onOpenTemplates: () -> Unit,
    onAdd: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

private fun formatTime(minutes: Int): String = String.format(
    Locale.ROOT,
    "%02d:%02d",
    minutes / 60,
    minutes % 60,
)
