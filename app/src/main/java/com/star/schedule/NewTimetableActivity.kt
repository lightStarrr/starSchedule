package com.star.schedule

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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

    /** 导入成功后课表已经落库，这一页没有继续存在的意义，跟着退出。 */
    private val wakeUpImportLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installEdgeToEdge()

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                NewTimetableRoute(
                    onBack = ::finish,
                    // TODO(功能): 手动创建、AI 识别、网页导入、文件导入尚未实现，先只提供入口列表。
                    onManualCreate = {},
                    onRecognizeScreenshotOrText = {},
                    onImportFromWebsite = {},
                    onWakeUpImport = {
                        wakeUpImportLauncher.launch(Intent(this, WakeUpImportActivity::class.java))
                    },
                    onFileImport = {},
                )
            }
        }
    }
}
