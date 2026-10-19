package com.star.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.timetable.presentation.NewTimetableRoute
import com.star.schedule.platform.systembar.installEdgeToEdge

/**
 * 新建课表入口。
 *
 * 单独开 Activity 而不是弹 BottomSheet：四种方式里有文本输入、文件选择和网络请求，
 * sheet 在键盘、系统文件选择器和多步流程下都不稳。
 */
class NewTimetableActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installEdgeToEdge()

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                NewTimetableRoute(
                    onBack = ::finish,
                    // TODO(功能): 三条流程尚未实现，先只提供入口列表。
                    onManualCreate = {},
                    onWakeUpImport = {},
                    onFileImport = {},
                    onAiImport = {},
                )
            }
        }
    }
}
