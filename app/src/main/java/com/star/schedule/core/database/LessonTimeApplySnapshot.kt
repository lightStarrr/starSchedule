package com.star.schedule.core.database

/**
 * 一次「应用模板」的可撤销快照。
 *
 * 应用只删写 `lesson_time`、不动课程，所以快照里只需要应用前那一份节次；
 * 课程用到的节次超出模板时只是「看不到时间」，由界面提前告知，不在这里补偿。
 */
data class LessonTimeApplySnapshot(
    val timetableId: Long,
    val lessonTimesBefore: List<LessonTimeEntity>,
)
