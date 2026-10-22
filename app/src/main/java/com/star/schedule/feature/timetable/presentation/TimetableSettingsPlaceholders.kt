package com.star.schedule.feature.timetable.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.PageTopBar

/**
 * 课表设置里的两个二级页面目前是空的：只有顶栏，内容留给后续实现。
 *
 * 先把入口和页面骨架立住，免得点了看不出反应；这里刻意不放任何说明文案和占位插图。
 */
@Composable
fun CourseSettingsRoute(onBack: () -> Unit) {
    SettingsPlaceholderPage(
        title = stringResource(R.string.timetable_settings_courses),
        onBack = onBack,
    )
}

@Composable
fun LessonTimeSettingsRoute(onBack: () -> Unit) {
    SettingsPlaceholderPage(
        title = stringResource(R.string.timetable_settings_lesson_times),
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPlaceholderPage(
    title: String,
    onBack: () -> Unit,
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
