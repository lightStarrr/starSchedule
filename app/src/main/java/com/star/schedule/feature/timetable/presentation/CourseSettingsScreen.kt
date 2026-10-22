package com.star.schedule.feature.timetable.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.PageTopBar
import com.star.schedule.feature.schedule.presentation.EmptyTimetableState
import com.star.schedule.feature.schedule.presentation.TimetableEmptyState

@Composable
fun CourseSettingsRoute(
    viewModel: CourseSettingsViewModel,
    onBack: () -> Unit,
    onOpenLessonTimes: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CourseSettingsScreen(
        state = state,
        onBack = onBack,
        onOpenLessonTimes = onOpenLessonTimes,
    )
}

/**
 * 课程设置。
 *
 * 课程增删改还没做，页面本身保持空白；但课表里没有节次时间时不能什么都不显示——
 * 课程要排在节次上，这时给出和主页同一套提示，并就地给一个去设置节次时间的按钮。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CourseSettingsScreen(
    state: CourseSettingsUiState,
    onBack: () -> Unit,
    onOpenLessonTimes: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageTopBar(
                title = stringResource(R.string.timetable_settings_courses),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        // 提示按整个屏幕居中，不按「顶栏之下剩下的那块」居中：
        // Scaffold 的 innerPadding 会把可视区域压到顶栏以下，照它居中会整体偏下半个顶栏高度。
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
                            Button(onClick = onOpenLessonTimes) {
                                Text(stringResource(R.string.courses_settings_go_lesson_times))
                            }
                        },
                    )
                }

                // 有节次时间时页面内容还没做，保持空白。
            }
        }
    }
}
