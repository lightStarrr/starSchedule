package com.star.schedule.feature.timetable.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import com.star.schedule.feature.timetable.domain.LessonTimeApplyPreview

/**
 * 「应用模板」确认框，同时充当预览：覆盖哪张课表、模板几节、哪些课会因此没有时间。
 *
 * 应用只替换课表节次、不动课程，所以越界的课不会消失也不会被裁剪——
 * 但它们的节次在课表里找不到时间，这里必须提前说清楚，最多列三条。
 */
@Composable
fun ApplyTemplateDialog(
    preview: LessonTimeApplyPreview,
    isApplying: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val separator = stringResource(R.string.list_separator)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_apply_template)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        R.string.text_apply_template_summary,
                        preview.timetableName,
                        preview.periodCount,
                    ),
                )
                if (preview.hasMissing) {
                    Text(
                        text = stringResource(
                            R.string.text_apply_template_missing_notice,
                            preview.missing.size,
                            preview.periodCount,
                        ),
                        // 不是错误，但后果是「这些课没有时间」，用 error 色保证不会看漏。
                        color = MaterialTheme.colorScheme.error,
                    )
                    preview.missing.take(MISSING_COURSES_SHOWN).forEach { missing ->
                        Text(
                            text = stringResource(
                                R.string.text_apply_template_course_periods,
                                missing.courseName,
                                missing.periods.joinToString(separator),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (preview.missing.size > MISSING_COURSES_SHOWN) {
                        Text(
                            text = stringResource(
                                R.string.text_apply_template_more,
                                preview.missing.size - MISSING_COURSES_SHOWN,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isApplying) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isApplying) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** 弹窗里最多列三门课，再多就折成「…等 N 门」。 */
private const val MISSING_COURSES_SHOWN = 3
