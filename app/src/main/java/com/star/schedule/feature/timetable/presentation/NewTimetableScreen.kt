package com.star.schedule.feature.timetable.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.CompactPageTopBar
import com.star.schedule.core.designsystem.navigationBarBottomInset
import com.star.schedule.feature.settings.presentation.SettingsGroup
import com.star.schedule.feature.settings.presentation.SettingsListItem
import com.star.schedule.feature.settings.presentation.SettingsSectionTitle

@Composable
fun NewTimetableRoute(
    onBack: () -> Unit,
    onManualCreate: () -> Unit,
    onWakeUpImport: () -> Unit,
    onFileImport: () -> Unit,
    onQiangzhiImport: () -> Unit,
    onAiImport: () -> Unit,
) {
    NewTimetableScreen(
        onBack = onBack,
        onManualCreate = onManualCreate,
        onWakeUpImport = onWakeUpImport,
        onFileImport = onFileImport,
        onQiangzhiImport = onQiangzhiImport,
        onAiImport = onAiImport,
    )
}

/**
 * 新建课表的入口：先定这张课表怎么来，再进入各自的流程。
 *
 * 四种方式的产物都是同一条 `TimetableEntity`，所以这里只做一次分流；
 * 名称、开学日期等最终确认交给各流程自己收集，避免在这一层重复出现表单。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewTimetableScreen(
    onBack: () -> Unit,
    onManualCreate: () -> Unit,
    onWakeUpImport: () -> Unit,
    onFileImport: () -> Unit,
    onQiangzhiImport: () -> Unit,
    onAiImport: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val navigationBarInset = navigationBarBottomInset()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CompactPageTopBar(
                title = stringResource(R.string.new_timetable_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 24.dp + navigationBarInset),
        ) {
            SettingsGroup {
                SettingsListItem(
                    title = stringResource(R.string.new_timetable_manual_title),
                    supporting = stringResource(R.string.new_timetable_manual_support),
                    onClick = onManualCreate,
                    shapeIndex = 0,
                    shapeCount = 1,
                    leading = { Icon(Icons.Rounded.EditNote, contentDescription = null) },
                )
            }

            SettingsSectionTitle(R.string.new_timetable_section_import)
            SettingsGroup {
                SettingsListItem(
                    title = stringResource(R.string.new_timetable_wakeup_title),
                    supporting = stringResource(R.string.new_timetable_wakeup_support),
                    onClick = onWakeUpImport,
                    shapeIndex = 0,
                    shapeCount = 3,
                    leading = { Icon(Icons.Rounded.ContentPaste, contentDescription = null) },
                )
                SettingsListItem(
                    title = stringResource(R.string.new_timetable_file_title),
                    supporting = stringResource(R.string.new_timetable_file_support),
                    onClick = onFileImport,
                    shapeIndex = 1,
                    shapeCount = 3,
                    leading = { Icon(Icons.Rounded.Description, contentDescription = null) },
                )
                SettingsListItem(
                    title = stringResource(R.string.new_timetable_qiangzhi_title),
                    supporting = stringResource(R.string.new_timetable_qiangzhi_support),
                    onClick = onQiangzhiImport,
                    shapeIndex = 2,
                    shapeCount = 4,
                    leading = { Icon(Icons.Rounded.School, contentDescription = null) },
                )
                SettingsListItem(
                    title = stringResource(R.string.new_timetable_ai_title),
                    supporting = stringResource(R.string.new_timetable_ai_support),
                    onClick = onAiImport,
                    shapeIndex = 3,
                    shapeCount = 4,
                    leading = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                )
            }
        }
    }
}
