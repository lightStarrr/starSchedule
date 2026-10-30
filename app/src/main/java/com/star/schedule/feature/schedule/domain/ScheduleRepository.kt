package com.star.schedule.feature.schedule.domain

import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.DayNoteEntity
import com.star.schedule.core.database.LessonTimeEntity
import com.star.schedule.core.database.LessonTimeTemplateEntity
import com.star.schedule.core.database.LessonTimeTemplateItemEntity
import com.star.schedule.core.database.TimetableEntity
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    fun observeCurrentTimetableId(): Flow<Long?>

    fun observeTimetable(timetableId: Long): Flow<TimetableEntity?>

    fun observeCourses(timetableId: Long): Flow<List<CourseEntity>>

    fun observeLessonTimes(timetableId: Long): Flow<List<LessonTimeEntity>>

    /** 新增一节，节次按开始时间重新排号。 */
    suspend fun insertLessonTime(lessonTime: LessonTimeEntity): Long

    /** 改一节的时间，节次同样按开始时间重新排号。 */
    suspend fun updateLessonTime(lessonTime: LessonTimeEntity)

    /** 删一节，剩下的节次重新排号；课程引用的节次号不会跟着改。 */
    suspend fun deleteLessonTime(lessonTime: LessonTimeEntity)

    /** 保存前查重：同名模板已存在时返回它的 id，用来决定要不要先问「覆盖」。 */
    suspend fun getLessonTimeTemplateIdByName(name: String): Long?

    /**
     * 把指定课表的节次时间整份存成全局模板。
     *
     * [overwrite] 为 false 且同名已存在时 DAO 会抛 [IllegalStateException]（`TEMPLATE_EXISTS`），
     * 调用方应当先用 [getLessonTimeTemplateIdByName] 判断，不要靠捕获异常来走正常流程。
     */
    suspend fun saveLessonTimeTemplateFromTimetable(
        timetableId: Long,
        name: String,
        overwrite: Boolean = false,
    ): Long

    /** 模板列表，按更新时间倒序。 */
    fun observeLessonTimeTemplates(): Flow<List<LessonTimeTemplateEntity>>

    /** 所有模板的节次，用来算列表里的「共几节、几点到几点」。 */
    fun observeLessonTimeTemplateItems(): Flow<List<LessonTimeTemplateItemEntity>>

    suspend fun getLessonTimeTemplateByIdOnce(templateId: Long): LessonTimeTemplateEntity?

    suspend fun getLessonTimeTemplateItemsOnce(templateId: Long): List<LessonTimeTemplateItemEntity>

    /**
     * 用一组节次直接建模板（「另存为新模板」走这里）。
     *
     * 和 [saveLessonTimeTemplateFromTimetable] 的区别：那份是从课表读，这份由调用方给内容。
     */
    suspend fun saveLessonTimeTemplateFromItems(
        templateName: String,
        lessonTimes: List<LessonTimeTemplateItemEntity>,
        overwrite: Boolean = false,
    ): Long

    /** 编辑模板：名称和节次整份替换，节次会按开始时间重新编号 1..n。 */
    suspend fun replaceLessonTimeTemplate(
        template: LessonTimeTemplateEntity,
        items: List<LessonTimeTemplateItemEntity>,
    )

    /** 删除模板，它的节次由外键级联删掉。 */
    suspend fun deleteLessonTimeTemplate(template: LessonTimeTemplateEntity)

    /** 撤销删除：按原样把模板和节次插回去。 */
    suspend fun restoreLessonTimeTemplate(
        template: LessonTimeTemplateEntity,
        items: List<LessonTimeTemplateItemEntity>,
    )

    fun observeDayNotes(timetableId: Long): Flow<List<DayNoteEntity>>

    suspend fun upsertDayNote(note: DayNoteEntity): Long

    suspend fun deleteDayNote(timetableId: Long, date: String)
}
