package com.star.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.importing.wakeup.data.createWakeUpImportUseCase
import com.star.schedule.feature.importing.wakeup.presentation.WakeUpImportRoute
import com.star.schedule.feature.importing.wakeup.presentation.WakeUpImportViewModel
import com.star.schedule.feature.importing.wakeup.presentation.WakeUpImportViewModelFactory
import com.star.schedule.feature.settings.data.RoomSettingsRepository
import com.star.schedule.feature.timetable.data.RoomTimetableRepository
import com.star.schedule.notification.UnifiedNotificationManager
import com.star.schedule.platform.systembar.installEdgeToEdge

/**
 * 从 WakeUp 分享口令导入课表。
 *
 * 单独开 Activity 而不是弹层：要粘贴长文本、要等网络、要处理键盘。
 */
class WakeUpImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatabaseProvider.init(this)
        installEdgeToEdge()

        val dao = DatabaseProvider.dao()
        val notificationManager = UnifiedNotificationManager(this)
        val importUseCase = createWakeUpImportUseCase(
            repository = RoomTimetableRepository(dao, notificationManager),
            defaultTimetableName = getString(R.string.wakeup_default_timetable_name),
        )
        val settingsRepository = RoomSettingsRepository(this, dao, notificationManager)
        val viewModel = ViewModelProvider(
            this,
            WakeUpImportViewModelFactory(importUseCase, settingsRepository::selectTimetable),
        )[WakeUpImportViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                WakeUpImportRoute(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onImported = {
                        // 导入成功后入口页也该退掉：此时用户要看的是新课表，不是"选择导入方式"。
                        setResult(RESULT_OK)
                        finish()
                    },
                )
            }
        }
    }
}
