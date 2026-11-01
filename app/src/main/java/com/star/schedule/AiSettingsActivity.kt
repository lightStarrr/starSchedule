package com.star.schedule

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.settings.data.RemoteAiUsageRepository
import com.star.schedule.feature.settings.data.RoomSettingsRepository
import com.star.schedule.feature.settings.presentation.AiSettingsRoute
import com.star.schedule.feature.settings.presentation.AiSettingsViewModel
import com.star.schedule.feature.settings.presentation.AiSettingsViewModelFactory
import com.star.schedule.notification.UnifiedNotificationManager
import com.star.schedule.platform.systembar.installEdgeToEdge

class AiSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatabaseProvider.init(this)
        installEdgeToEdge()

        // 只看隐私开关与当前服务商两个偏好，复用设置模块的仓库即可，不必为 AI 再造一层
        val settingsRepository = RoomSettingsRepository(
            context = this,
            dao = DatabaseProvider.dao(),
            notificationManager = UnifiedNotificationManager(this),
        )
        val viewModel = ViewModelProvider(
            this,
            AiSettingsViewModelFactory(
                repository = settingsRepository,
                usageRepository = RemoteAiUsageRepository(this),
            ),
        )[AiSettingsViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                AiSettingsRoute(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onNewProvider = {
                        startActivity(Intent(this, NewAiProviderActivity::class.java))
                    },
                )
            }
        }
    }
}
