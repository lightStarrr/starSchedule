package com.star.schedule

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.feature.schedule.data.RoomScheduleRepository
import com.star.schedule.feature.wallpaper.data.RoomWallpaperRepository
import com.star.schedule.feature.wallpaper.presentation.WallpaperPreviewRoute
import com.star.schedule.feature.wallpaper.presentation.WallpaperPreviewViewModel
import com.star.schedule.feature.wallpaper.presentation.WallpaperPreviewViewModelFactory
import com.star.schedule.platform.systembar.installEdgeToEdge
import com.star.schedule.feature.wallpaper.domain.WallpaperDesign
import kotlinx.serialization.json.Json

/**
 * 壁纸预览页：作用于打开时指定的课表与照片；缺任一参数就没有目标。
 */
class WallpaperPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val timetableId = intent.getLongExtra(EXTRA_TIMETABLE_ID, -1L).takeIf { it > 0 }
        val imageUri = intent.getStringExtra(EXTRA_IMAGE_URI)
        val designJson = intent.getStringExtra(EXTRA_DESIGN)
        val design = designJson?.let {
            runCatching { Json.decodeFromString(WallpaperDesign.serializer(), it) }.getOrNull()
        }
        if (timetableId == null || (designJson != null && design?.isValid() != true) ||
            (imageUri == null && designJson == null && !intent.getBooleanExtra(EXTRA_DEFAULT, false))
        ) {
            finish()
            return
        }

        DatabaseProvider.init(this)
        installEdgeToEdge()

        val viewModel = ViewModelProvider(
            this,
            WallpaperPreviewViewModelFactory(
                timetableId = timetableId,
                imageUriString = imageUri,
                scheduleRepository = RoomScheduleRepository(DatabaseProvider.db),
                wallpaperRepository = RoomWallpaperRepository(this, DatabaseProvider.preferenceDao()),
                initialDesign = design,
            ),
        )[WallpaperPreviewViewModel::class.java]

        setContent {
            WallpaperPreviewRoute(
                viewModel = viewModel,
                onBack = ::finish,
                onApplied = {
                    setResult(Activity.RESULT_OK)
                    finish()
                },
            )
        }
    }

    companion object {
        private const val EXTRA_TIMETABLE_ID = "extra_timetable_id"
        private const val EXTRA_IMAGE_URI = "extra_image_uri"
        private const val EXTRA_DESIGN = "extra_design"
        private const val EXTRA_DEFAULT = "extra_default"

        fun intent(context: Context, timetableId: Long, design: WallpaperDesign?): Intent =
            Intent(context, WallpaperPreviewActivity::class.java)
                .putExtra(EXTRA_TIMETABLE_ID, timetableId)
                .putExtra(EXTRA_DEFAULT, design == null)
                .apply {
                    design?.let {
                        putExtra(EXTRA_DESIGN, Json.encodeToString(WallpaperDesign.serializer(), it))
                    }
                }

        fun intent(context: Context, timetableId: Long, imageUriString: String): Intent =
            Intent(context, WallpaperPreviewActivity::class.java)
                .putExtra(EXTRA_TIMETABLE_ID, timetableId)
                .putExtra(EXTRA_IMAGE_URI, imageUriString)
                // 照片来自系统选择器，权限按 Uri 授予目标 Activity。
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
