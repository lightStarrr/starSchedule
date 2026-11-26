package com.star.schedule.core.model

import com.star.schedule.core.database.entity.CourseWithSessions

/**
 * 展平后的只读排课记录：将课程主表（[courseId]）与具体上课时间（[sessionId]）合并，
 * 供课表网格渲染、提醒调度、桌面小组件与 WakeUp 导出使用。
 */
data class ScheduledCourse(
    val sessionId: Long = 0L,
    val courseId: Long = 0L,
    val timetableId: Long = 0L,
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val dayOfWeek: Int,
    val periods: List<Int>,
    val weeks: List<Int>,
) {
    /** 兼容旧调用方对单条排课唯一标识的读取，保持与原 course.id 语义等价。 */
    val id: Long get() = sessionId
}

fun CourseWithSessions.toScheduledCourses(): List<ScheduledCourse> =
    sessions.map { session ->
        ScheduledCourse(
            sessionId = session.id,
            courseId = course.id,
            timetableId = course.timetableId,
            name = course.name,
            teacher = session.teacher,
            location = session.location,
            dayOfWeek = session.dayOfWeek,
            periods = session.periods,
            weeks = session.weeks,
        )
    }

fun List<CourseWithSessions>.toScheduledCourses(): List<ScheduledCourse> =
    flatMap { it.toScheduledCourses() }
        .sortedWith(
            compareBy<ScheduledCourse>(
                { it.dayOfWeek },
                { it.periods.minOrNull() ?: Int.MAX_VALUE },
                { it.sessionId },
            ),
        )
