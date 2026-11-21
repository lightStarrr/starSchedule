package com.star.schedule.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.feature.emoji.data.EmojiPickerRecentsRepository
import com.star.schedule.feature.emoji.presentation.EmojiWallpaperRoute
import com.star.schedule.feature.emoji.presentation.EmojiWallpaperViewModel
import com.star.schedule.feature.emoji.presentation.EmojiWallpaperViewModelFactory
import com.star.schedule.platform.systembar.installEdgeToEdge
import com.star.schedule.feature.wallpaper.domain.WallpaperDesign

/**
 * 表情壁纸编辑页：作用于打开时指定的课表。
 * 编辑后的绘制参数交给课表预览页，应用后仍按窗口实时绘制。
 */
class EmojiWallpaperActivity : ComponentActivity() {
    // 预览页应用成功后返回课表设置。
    private val previewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val timetableId = intent.getLongExtra(EXTRA_TIMETABLE_ID, -1L).takeIf { it > 0 }
        if (timetableId == null) {
            finish()
            return
        }

        installEdgeToEdge()

        val dependencies = (application as ScheduleApplication).dataSourceDependencies
        val viewModel = ViewModelProvider(
            this,
            EmojiWallpaperViewModelFactory(
                appContext = applicationContext,
                optionalResources = dependencies.optionalResources,
                recentsRepository = EmojiPickerRecentsRepository(DatabaseProvider.preferenceDao()),
                systemSeedArgb = dynamicLightColorScheme(this).primary.toArgb(),
            ),
        )[EmojiWallpaperViewModel::class.java]

        setContent {
            EmojiWallpaperRoute(
                viewModel = viewModel,
                onBack = ::finish,
                onPrepared = { spec ->
                    previewLauncher.launch(
                        WallpaperPreviewActivity.intent(
                            this,
                            timetableId,
                            WallpaperDesign(emoji = spec),
                        ),
                    )
                },
            )
        }
    }

    companion object {
        private const val EXTRA_TIMETABLE_ID = "extra_timetable_id"

        fun intent(context: Context, timetableId: Long): Intent =
            Intent(context, EmojiWallpaperActivity::class.java)
                .putExtra(EXTRA_TIMETABLE_ID, timetableId)
    }
}
