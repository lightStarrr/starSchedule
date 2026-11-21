package com.star.schedule

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.emoji.presentation.WallpaperViewModel
import com.star.schedule.feature.emoji.presentation.WallpaperViewModelFactory
import com.star.schedule.feature.wallpaper.presentation.WallpaperRoute
import com.star.schedule.platform.systembar.installEdgeToEdge

/**
 * 壁纸页：作用于打开时指定的课表；没带 id 就没有目标。
 */
class WallpaperActivity : ComponentActivity() {
    private var timetableId: Long = -1L

    // 预览页「应用」成功后这里也结束，直接回到课表设置页看新壁纸卡片。
    private val previewLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) finish()
    }

    // 表情壁纸编辑页同样走预览页应用：RESULT_OK 一致收尾。
    private val emojiLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) finish()
    }

    private val pickPhoto = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let {
            previewLauncher.launch(
                WallpaperPreviewActivity.intent(this, timetableId, it.toString()),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val timetableId = intent.getLongExtra(EXTRA_TIMETABLE_ID, -1L).takeIf { it > 0 }
        if (timetableId == null) {
            finish()
            return
        }
        this.timetableId = timetableId

        installEdgeToEdge()

        val viewModel = ViewModelProvider(
            this,
            WallpaperViewModelFactory(
                (application as ScheduleApplication)
                    .dataSourceDependencies
                    .optionalResources,
            ),
        )[WallpaperViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                WallpaperRoute(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onPickPhoto = {
                        pickPhoto.launch(
                            PickVisualMediaRequest.Builder()
                                .setMediaType(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                )
                                .build(),
                        )
                    },
                    onPreview = { design ->
                        previewLauncher.launch(WallpaperPreviewActivity.intent(this, timetableId, design))
                    },
                    onMakeEmojiWallpaper = {
                        emojiLauncher.launch(
                            EmojiWallpaperActivity.intent(this, timetableId),
                        )
                    },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TIMETABLE_ID = "extra_timetable_id"

        fun intent(context: Context, timetableId: Long): Intent =
            Intent(context, WallpaperActivity::class.java)
                .putExtra(EXTRA_TIMETABLE_ID, timetableId)
    }
}
