package com.star.schedule.feature.settings.data

import android.content.Context
import com.star.schedule.core.common.Constants
import com.star.schedule.core.database.ScheduleDao
import com.star.schedule.feature.settings.domain.SettingsRepository
import com.star.schedule.feature.settings.domain.TimetablePreview
import com.star.schedule.feature.settings.domain.TimetablePreviewBlock
import com.star.schedule.feature.settings.domain.TimetableSummary
import com.star.schedule.notification.UnifiedNotificationManager
import com.star.schedule.wakeup.WakeUpSupport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class RoomSettingsRepository(
    context: Context,
    private val dao: ScheduleDao,
    private val notificationManager: UnifiedNotificationManager,
) : SettingsRepository {
    private val appContext = context.applicationContext
    private val iconStore = LiveNotificationIconStore(context)
    override fun observeTimetables(): Flow<List<TimetableSummary>> =
        dao.getAllTimetables().map { timetables ->
            timetables.map { timetable ->
                TimetableSummary(
                    id = timetable.id,
                    name = timetable.name,
                    reminderTime = timetable.reminderTime,
                )
            }
        }

    override fun observeTimetablePreviews(): Flow<List<TimetablePreview>> =
        combine(dao.getAllTimetables(), dao.getAllCoursesFlow()) { timetables, courses ->
            val byTimetable = courses.groupBy { it.timetableId }
            timetables.map { timetable ->
                val courses = byTimetable[timetable.id].orEmpty()
                // 预览只画「第一个有课的周」：把所有周次的课摞在一起会糊成一片，也看不出这是一张课表。
                val firstWeek = courses.mapNotNull { course -> course.weeks.minOrNull() }.minOrNull()
                val previewCourses = if (firstWeek == null) {
                    emptyList()
                } else {
                    courses.filter { firstWeek in it.weeks }
                }
                val blocks = previewCourses.flatMap { course ->
                    contiguousSpans(course.periods).map { span ->
                        TimetablePreviewBlock(
                            dayOfWeek = course.dayOfWeek,
                            startPeriod = span.first(),
                            periodCount = span.size
                        )
                    }
                }
                TimetablePreview(
                    id = timetable.id,
                    name = timetable.name,
                    hasWeekendCourses = blocks.any { it.dayOfWeek > 5 },
                    rowHeight = timetable.rowHeight,
                    startDate = timetable.startDate,
                    blocks = blocks
                )
            }
        }

    override suspend fun setRowHeight(timetableId: Long, rowHeight: Int) {
        val timetable = dao.getTimetableFlow(timetableId).first() ?: return
        dao.updateTimetable(timetable.copy(rowHeight = rowHeight))
    }

    /** 预览里连着的节次要画成一整块，所以先按连续区间分组。 */
    private fun contiguousSpans(periods: List<Int>): List<List<Int>> {
        val sorted = periods.distinct().sorted()
        if (sorted.isEmpty()) return emptyList()
        val spans = mutableListOf<MutableList<Int>>()
        sorted.forEach { period ->
            val current = spans.lastOrNull()
            if (current == null || period != current.last() + 1) {
                spans += mutableListOf(period)
            } else {
                current += period
            }
        }
        return spans
    }

    override fun observePreference(key: String): Flow<String?> =
        dao.getPreferenceFlow(key)

    override suspend fun setPreference(key: String, value: String) {
        dao.setPreference(key, value)
    }

    override suspend fun selectTimetable(timetableId: Long) {
        // Switching the active timetable must not leave reminders scheduled for
        // a timetable that is no longer being viewed.
        disableReminders()
        dao.setPreference(Constants.PREF_CURRENT_TIMETABLE, timetableId.toString())
    }

    override suspend fun renameTimetable(timetableId: Long, name: String) {
        val timetable = dao.getAllTimetablesOnce().firstOrNull { it.id == timetableId } ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == timetable.name) return
        dao.updateTimetableWithReminders(timetable.copy(name = trimmed))
    }

    override suspend fun deleteTimetable(timetableId: Long) {
        val timetable = dao.getAllTimetablesOnce().firstOrNull { it.id == timetableId } ?: return
        dao.deleteTimetableWithReminders(timetable)
        // 删掉的正好是当前课表时，把当前指向挪到剩下的一份；没有剩下的就置空。
        dao.ensureValidCurrentTimetable()
    }

    override fun isLiveCapsuleCustomizationAvailable(): Boolean =
        notificationManager.isLiveCapsuleCustomizationAvailable()

    override fun isWakeUpSimulationAvailable(): Boolean = WakeUpSupport.isColorOs()

    override fun isWakeUpProxyInstalled(): Boolean =
        WakeUpSupport.isProxyInstalled(appContext)

    override suspend fun importLiveCapsuleIcon(uriString: String): Result<Unit> {
        val path = iconStore.importFromUri(uriString).getOrElse { error ->
            return Result.failure(error)
        }
        dao.setPreference(Constants.PREF_LIVE_CAPSULE_ICON_PATH, path)
        return Result.success(Unit)
    }

    override suspend fun clearLiveCapsuleIcon() {
        val path = dao.getPreferenceFlow(Constants.PREF_LIVE_CAPSULE_ICON_PATH).first()
        iconStore.delete(path)
        dao.setPreference(Constants.PREF_LIVE_CAPSULE_ICON_PATH, "")
    }

    override fun isReminderEnabledForTimetable(timetableId: Long): Boolean =
        notificationManager.isReminderEnabledForTimetableSync(timetableId)

    override suspend fun enableRemindersForTimetable(timetableId: Long) {
        notificationManager.enableRemindersForTimetable(timetableId)
    }

    override suspend fun disableReminders() {
        notificationManager.disableReminders()
    }

    override fun sendTestNotification() {
        notificationManager.sendTestNotification()
    }

    override fun scheduleTestReminder() {
        notificationManager.scheduleTestReminder()
    }
}
