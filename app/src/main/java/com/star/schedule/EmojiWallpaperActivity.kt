package com.star.schedule

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.feature.emoji.data.EmojiPickerRecentsRepository
import com.star.schedule.feature.emoji.presentation.EmojiWallpaperRoute
import com.star.schedule.feature.emoji.presentation.EmojiWallpaperViewModel
import com.star.schedule.feature.emoji.presentation.EmojiWallpaperViewModelFactory
import com.star.schedule.platform.systembar.installEdgeToEdge
import java.io.File

/**
 * 表情壁纸编辑页：作用于打开时指定的课表。
 * 编辑完导出的静态 PNG 交给照片预览页走统一的「应用」流程。
 */
class EmojiWallpaperActivity : ComponentActivity() {

    private var pendingExportFile: File? = null

    // 预览页「应用」成功后这里也结束；无论结果如何都要删掉导出的临时 PNG。
    private val previewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        pendingExportFile?.delete()
        pendingExportFile = null
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
                recentsRepository = EmojiPickerRecentsRepository(DatabaseProvider.dao()),
                systemSeedArgb = dynamicLightColorScheme(this).primary.toArgb(),
            ),
        )[EmojiWallpaperViewModel::class.java]

        setContent {
            EmojiWallpaperRoute(
                viewModel = viewModel,
                onBack = ::finish,
                onExported = { file ->
                    pendingExportFile = file
                    previewLauncher.launch(
                        WallpaperPreviewActivity.intent(
                            this,
                            timetableId,
                            file.toUri().toString(),
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
