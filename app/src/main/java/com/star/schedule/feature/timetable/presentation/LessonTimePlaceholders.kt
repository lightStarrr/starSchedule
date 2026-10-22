package com.star.schedule.feature.timetable.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.CircleIconButton
import com.star.schedule.core.designsystem.component.PageTopBar

/**
 * 节次时间相关的两个页面目前都是空的：只有顶栏，内容留给后续实现。
 *
 * 先把入口和页面骨架立住，免得点了看不出反应；这里刻意不放说明文案和占位插图。
 */
@Composable
fun LessonTimeSettingsRoute(
    onBack: () -> Unit,
    onOpenTemplates: () -> Unit,
) {
    LessonTimePlaceholderPage(
        title = stringResource(R.string.timetable_settings_lesson_times),
        onBack = onBack,
        actions = {
            // 模板是全局资产、和本课表的节次不是一个作用域，所以只留一个纯图标入口。
            CircleIconButton(
                icon = Icons.Rounded.Bookmarks,
                contentDescription = stringResource(R.string.title_course_time_templates),
                onClick = onOpenTemplates,
            )
        },
    )
}

@Composable
fun LessonTimeTemplateRoute(onBack: () -> Unit) {
    LessonTimePlaceholderPage(
        title = stringResource(R.string.title_course_time_templates),
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonTimePlaceholderPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageTopBar(
                title = title,
                onBack = onBack,
                scrollBehavior = scrollBehavior,
                actions = actions,
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
