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

        // EXTRA 缺省为 NEW_TEMPLATE_ID（-1）：不带 id 打开就是新建模式。
        val templateId = intent.getLongExtra(EXTRA_TEMPLATE_ID, NEW_TEMPLATE_ID)

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
        private const val NEW_TEMPLATE_ID = -1L

        fun intent(context: Context, templateId: Long): Intent =
            Intent(context, LessonTimeTemplateEditActivity::class.java)
                .putExtra(EXTRA_TEMPLATE_ID, templateId)

        fun newTemplateIntent(context: Context): Intent =
            Intent(context, LessonTimeTemplateEditActivity::class.java)
    }
}
