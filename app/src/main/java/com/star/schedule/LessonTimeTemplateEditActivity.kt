package com.star.schedule

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.star.schedule.core.database.DatabaseProvider
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.schedule.data.RoomScheduleRepository
import com.star.schedule.feature.timetable.presentation.LessonTimeTemplateEditRoute
import com.star.schedule.feature.timetable.presentation.LessonTimeTemplateEditViewModel
import com.star.schedule.feature.timetable.presentation.LessonTimeTemplateEditViewModelFactory
import com.star.schedule.platform.systembar.installEdgeToEdge

/**
 * 编辑单个节次时间模板：改名字和节次，点保存才写库。
 */
class LessonTimeTemplateEditActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DatabaseProvider.init(this)
        installEdgeToEdge()

        val templateId = intent.getLongExtra(EXTRA_TEMPLATE_ID, -1L)
        if (templateId < 0) {
            finish()
            return
        }

        val repository = RoomScheduleRepository(DatabaseProvider.dao())
        val viewModel = ViewModelProvider(
            this,
            LessonTimeTemplateEditViewModelFactory(repository, templateId),
        )[LessonTimeTemplateEditViewModel::class.java]

        setContent {
            StarScheduleTheme(dynamicColor = true) {
                LessonTimeTemplateEditRoute(
                    viewModel = viewModel,
                    onClose = ::finish,
                )
            }
        }
    }

    companion object {
        private const val EXTRA_TEMPLATE_ID = "extra_template_id"

        fun intent(context: Context, templateId: Long): Intent =
            Intent(context, LessonTimeTemplateEditActivity::class.java)
                .putExtra(EXTRA_TEMPLATE_ID, templateId)
    }
}
