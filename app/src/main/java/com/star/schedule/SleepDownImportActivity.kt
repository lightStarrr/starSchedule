package com.star.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.importing.sharecode.presentation.ShareCodeImportRoute
import com.star.schedule.feature.importing.sharecode.presentation.ShareCodeImportViewModel
import com.star.schedule.feature.importing.sharecode.presentation.ShareCodeImportViewModelFactory
import com.star.schedule.feature.importing.sleepdown.data.createSleepDownImportUseCase
import com.star.schedule.feature.importing.sleepdown.domain.extractSleepDownCode
import com.star.schedule.feature.importing.sleepdown.presentation.sleepDownImportErrorOf
import com.star.schedule.feature.importing.sleepdown.presentation.sleepDownShareCodeBrand
import com.star.schedule.feature.settings.data.RoomSettingsRepository
import com.star.schedule.feature.timetable.data.RoomTimetableRepository
import com.star.schedule.notification.UnifiedNotificationManager
import com.star.schedule.platform.systembar.installEdgeToEdge

/**
 * 从 SleepDown 短口令导入课表。
 *
 * 单独开 Activity 而不是弹层：要粘贴长文本、要等网络、要处理键盘。
 */
class SleepDownImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatabaseProvider.init(this)
        installEdgeToEdge()

        val db = DatabaseProvider.db
        val notificationManager = UnifiedNotificationManager(this)
        val importUseCase = createSleepDownImportUseCase(
            repository = RoomTimetableRepository(db, notificationManager),
            defaultTimetableName = getString(R.string.sleepdown_default_timetable_name),
        )
        val settingsRepository = RoomSettingsRepository(this, db, notificationManager)
        val viewModel = ViewModelProvider(
            this,
            ShareCodeImportViewModelFactory(
                importer = importUseCase,
                extractCode = ::extractSleepDownCode,
                errorOf = ::sleepDownImportErrorOf,
                selectTimetable = settingsRepository::selectTimetable,
            ),
        )[ShareCodeImportViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                ShareCodeImportRoute(
                    viewModel = viewModel,
                    brand = sleepDownShareCodeBrand(),
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
