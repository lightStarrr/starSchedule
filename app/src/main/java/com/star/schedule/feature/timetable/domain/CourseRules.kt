package com.star.schedule.feature.timetable.domain

import com.star.schedule.core.database.entity.CourseEntity
import com.star.schedule.core.database.entity.CourseSessionEntity
import com.star.schedule.core.database.entity.CourseWithSessions
import com.star.schedule.core.database.entity.LessonTimeEntity

/**
 * 课程管理的纯逻辑：周次/节次摘要、课程主从分组、冲突检测、表单校验与拖动连选计算。
 *
 * 不依赖 Android、Compose 和 Room，单元测试可以直接覆盖。
 */
object CourseRules {

    const val COURSE_NAME_MAX_LENGTH = 50
    const val COURSE_TEACHER_MAX_LENGTH = 50
    const val COURSE_LOCATION_MAX_LENGTH = 100
    const val MIN_WEEK = 1
    const val DEFAULT_VISIBLE_WEEKS = 20
    const val MAX_WEEK = 30
    val DAY_OF_WEEK_RANGE = 1..7

    enum class WeekParity { ALL, ODD, EVEN }

    data class WeekRangeSummary(
        val rangeText: String,
        val parity: WeekParity,
        val count: Int,
    )

    data class CourseSessionItem(
        val course: CourseEntity,
        val session: CourseSessionEntity,
        val sortedPeriods: List<Int>,
        val sortedWeeks: List<Int>,
        val timeRange: Pair<String, String>?,
        val conflictsWithNames: List<String>,
    )

    data class CourseGroup(
        val course: CourseEntity,
        val sessions: List<CourseSessionItem>,
        val defaultTeacher: String,
        val defaultLocation: String,
        val defaultWeeks: List<Int>,
    ) {
        val name: String get() = course.name
    }

    data class CourseConflict(
        val sessionId: Long,
        val courseName: String,
        val overlapPeriods: List<Int>,
        val overlapWeeks: List<Int>,
    )

    enum class CourseValidationError {
        EmptyName,
        NameTooLong,
        TeacherTooLong,
        LocationTooLong,
        EmptyPeriods,
        InvalidPeriod,
        EmptyWeeks,
    }

    enum class CourseNameError {
        Empty,
        TooLong,
        Duplicate,
    }

    /** 将整数集合去重排序并压缩为连续区间文本，例如 `[1, 2, 5, 6, 7]` -> `"1-2, 5-7"`。 */
    fun formatCompactRanges(numbers: Collection<Int>): String {
        val sorted = numbers.distinct().sorted()
        if (sorted.isEmpty()) return ""

        val ranges = mutableListOf<String>()
        var start = sorted.first()
        var end = start
        for (i in 1 until sorted.size) {
            val current = sorted[i]
            if (current == end + 1) {
                end = current
            } else {
                ranges += if (start == end) start.toString() else "$start-$end"
                start = current
                end = current
            }
        }
        ranges += if (start == end) start.toString() else "$start-$end"
        return ranges.joinToString(", ")
    }

    /**
     * 格式化周次摘要：连续单周或双周（至少 2 周）折叠为起止范围 + 单双周标记，
     * 其余情况按连续区间压缩。
     */
    fun formatWeeksSummary(weeks: Collection<Int>): WeekRangeSummary? {
        val sorted = weeks.asSequence()
            .filter { it in MIN_WEEK..MAX_WEEK }
            .distinct()
            .sorted()
            .toList()
        if (sorted.isEmpty()) return null

        val isConstantStepTwo = sorted.size >= 2 &&
            sorted.zipWithNext().all { (a, b) -> b - a == 2 }
        if (isConstantStepTwo) {
            val parity = when {
                sorted.all { it % 2 != 0 } -> WeekParity.ODD
                sorted.all { it % 2 == 0 } -> WeekParity.EVEN
                else -> WeekParity.ALL
            }
            if (parity != WeekParity.ALL) {
                return WeekRangeSummary(
                    rangeText = "${sorted.first()}-${sorted.last()}",
                    parity = parity,
                    count = sorted.size,
                )
            }
        }

        return WeekRangeSummary(
            rangeText = formatCompactRanges(sorted),
            parity = WeekParity.ALL,
            count = sorted.size,
        )
    }

    /**
     * 解析连续节次对应的起止时间。仅当节次非空、连续且首尾节次均有时间配置时返回。
     */
    fun resolvePeriodTimeRange(
        periods: List<Int>,
        lessonTimes: List<LessonTimeEntity>,
    ): Pair<String, String>? {
        val sorted = periods.distinct().sorted()
        if (sorted.isEmpty()) return null
        val isContiguous = sorted.zipWithNext().all { (a, b) -> b == a + 1 }
        if (!isContiguous) return null

        val byPeriod = lessonTimes.associateBy { it.period }
        val start = byPeriod[sorted.first()] ?: return null
        val end = byPeriod[sorted.last()] ?: return null
        return start.startTime to end.endTime
    }

    /**
     * 先基于全量课程时段计算每条 [CourseSessionEntity] 的冲突情况，再按搜索关键词过滤并返回主从分组列表。
     * 保留 0 时段空课程（排在有排课的课程之后）。
     */
    fun buildCourseGroups(
        coursesWithSessions: List<CourseWithSessions>,
        lessonTimes: List<LessonTimeEntity>,
        query: String = "",
    ): List<CourseGroup> {
        if (coursesWithSessions.isEmpty()) return emptyList()

        data class FlatEntry(
            val course: CourseEntity,
            val session: CourseSessionEntity,
            val sortedPeriods: List<Int>,
            val sortedWeeks: List<Int>,
            val periodSet: Set<Int>,
            val weekSet: Set<Int>,
        )

        val flattened = coursesWithSessions.flatMap { item ->
            item.sessions.map { session ->
                val sortedPeriods = session.periods.distinct().sorted()
                val sortedWeeks = session.weeks.distinct().sorted()
                FlatEntry(
                    course = item.course,
                    session = session,
                    sortedPeriods = sortedPeriods,
                    sortedWeeks = sortedWeeks,
                    periodSet = sortedPeriods.toSet(),
                    weekSet = sortedWeeks.toSet(),
                )
            }
        }

        val conflictsByFlatIndex = flattened.indices.map { i ->
            val current = flattened[i]
            if (current.periodSet.isEmpty() || current.weekSet.isEmpty()) {
                emptyList()
            } else {
                buildList {
                    for (j in flattened.indices) {
                        if (i == j) continue
                        val other = flattened[j]
                        if (other.session.dayOfWeek != current.session.dayOfWeek) continue
                        if (current.periodSet.any { it in other.periodSet } &&
                            current.weekSet.any { it in other.weekSet }
                        ) {
                            val label = other.course.name.trim().ifEmpty { other.course.name }
                            if (label.isNotEmpty()) add(label)
                        }
                    }
                }.distinct()
            }
        }

        val flatIndexBySessionId = flattened.indices.associateBy { flattened[it].session.id }
        val trimmedQuery = query.trim()

        val sessionComparator = compareBy<CourseSessionItem>(
            { it.session.dayOfWeek },
            { it.sortedPeriods.firstOrNull() ?: Int.MAX_VALUE },
            { it.sortedWeeks.firstOrNull() ?: Int.MAX_VALUE },
            { it.session.id },
        )

        val groups = coursesWithSessions.mapNotNull { item ->
            val nameMatches = trimmedQuery.isEmpty() ||
                item.course.name.contains(trimmedQuery, ignoreCase = true)

            val allSessionItems = item.sessions.map { session ->
                val flatIndex = flatIndexBySessionId[session.id]
                val sortedPeriods = session.periods.distinct().sorted()
                val sortedWeeks = session.weeks.distinct().sorted()
                CourseSessionItem(
                    course = item.course,
                    session = session,
                    sortedPeriods = sortedPeriods,
                    sortedWeeks = sortedWeeks,
                    timeRange = resolvePeriodTimeRange(sortedPeriods, lessonTimes),
                    conflictsWithNames = flatIndex?.let { conflictsByFlatIndex[it] }.orEmpty(),
                )
            }.sortedWith(sessionComparator)

            val visibleSessions = if (nameMatches) {
                allSessionItems
            } else {
                allSessionItems.filter { sessionItem ->
                    sessionItem.session.teacher.contains(trimmedQuery, ignoreCase = true) ||
                        sessionItem.session.location.contains(trimmedQuery, ignoreCase = true)
                }
            }

            if (trimmedQuery.isNotEmpty() && !nameMatches && visibleSessions.isEmpty()) {
                return@mapNotNull null
            }

            CourseGroup(
                course = item.course,
                sessions = visibleSessions,
                defaultTeacher = mostFrequentNonBlank(allSessionItems.map { it.session.teacher }),
                defaultLocation = mostFrequentNonBlank(allSessionItems.map { it.session.location }),
                defaultWeeks = mostFrequentNonEmptyList(allSessionItems.map { it.sortedWeeks }),
            )
        }

        return groups.sortedWith(
            compareBy<CourseGroup>(
                { it.sessions.isEmpty() },
                { it.sessions.firstOrNull()?.session?.dayOfWeek ?: Int.MAX_VALUE },
                { it.sessions.firstOrNull()?.sortedPeriods?.firstOrNull() ?: Int.MAX_VALUE },
                { it.course.name },
                { it.course.id },
            ),
        )
    }

    /** 查找当前编辑草稿与已有排课时段之间的时间冲突（同星期、节次有交集且周次有交集）。 */
    fun findConflicts(
        editingSessionId: Long?,
        dayOfWeek: Int,
        periods: Collection<Int>,
        weeks: Collection<Int>,
        existingCourses: List<CourseWithSessions>,
    ): List<CourseConflict> {
        if (dayOfWeek !in DAY_OF_WEEK_RANGE) return emptyList()
        val periodSet = periods.toSet()
        val weekSet = weeks.toSet()
        if (periodSet.isEmpty() || weekSet.isEmpty()) return emptyList()

        return existingCourses.flatMap { item ->
            item.sessions.mapNotNull { existing ->
                if (editingSessionId != null && existing.id == editingSessionId) return@mapNotNull null
                if (existing.dayOfWeek != dayOfWeek) return@mapNotNull null

                val overlapPeriods = existing.periods.filter { it in periodSet }.distinct().sorted()
                if (overlapPeriods.isEmpty()) return@mapNotNull null

                val overlapWeeks = existing.weeks.filter { it in weekSet }.distinct().sorted()
                if (overlapWeeks.isEmpty()) return@mapNotNull null

                CourseConflict(
                    sessionId = existing.id,
                    courseName = item.course.name,
                    overlapPeriods = overlapPeriods,
                    overlapWeeks = overlapWeeks,
                )
            }
        }
    }

    /** 校验编辑中的课程草稿，返回首个阻断保存的错误；合法返回 null。 */
    fun validateCourseDraft(
        name: String,
        teacher: String,
        location: String,
        periods: Collection<Int>,
        weeks: Collection<Int>,
        availablePeriods: Set<Int>,
    ): CourseValidationError? {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return CourseValidationError.EmptyName
        if (trimmedName.length > COURSE_NAME_MAX_LENGTH) return CourseValidationError.NameTooLong
        if (teacher.trim().length > COURSE_TEACHER_MAX_LENGTH) return CourseValidationError.TeacherTooLong
        if (location.trim().length > COURSE_LOCATION_MAX_LENGTH) return CourseValidationError.LocationTooLong
        if (periods.isEmpty()) return CourseValidationError.EmptyPeriods
        if (periods.any { it !in availablePeriods }) return CourseValidationError.InvalidPeriod
        if (weeks.none { it in MIN_WEEK..MAX_WEEK }) return CourseValidationError.EmptyWeeks
        return null
    }

    /** 校验课程重命名输入：非空、长度上限且不与当前课表内其他课程重名。 */
    fun validateCourseName(
        name: String,
        existingNames: Collection<String>,
        currentName: String? = null,
    ): CourseNameError? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return CourseNameError.Empty
        if (trimmed.length > COURSE_NAME_MAX_LENGTH) return CourseNameError.TooLong
        val currentTrimmed = currentName?.trim()
        val otherNames = existingNames.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != currentTrimmed }
            .toSet()
        if (trimmed in otherNames) return CourseNameError.Duplicate
        return null
    }

    /**
     * 计算按住拖动连选后的目标选区：
     * 保持区间外的 [baseSelection] 不变，并将 [anchor] 到 [current] 在 [allowedValues] 中的闭区间
     * 统一设为选中（[selectMode] = true）或取消选中（[selectMode] = false）。
     */
    fun computeRangeDragSelection(
        baseSelection: Set<Int>,
        anchor: Int,
        current: Int,
        selectMode: Boolean,
        allowedValues: List<Int>,
    ): Set<Int> {
        val anchorIndex = allowedValues.indexOf(anchor)
        val currentIndex = allowedValues.indexOf(current)
        if (anchorIndex < 0 || currentIndex < 0) return baseSelection

        val start = minOf(anchorIndex, currentIndex)
        val end = maxOf(anchorIndex, currentIndex)
        val rangeValues = allowedValues.subList(start, end + 1).toSet()
        return if (selectMode) baseSelection + rangeValues else baseSelection - rangeValues
    }

    private fun mostFrequentNonBlank(values: List<String>): String {
        val trimmed = values.map { it.trim() }.filter { it.isNotEmpty() }
        if (trimmed.isEmpty()) return ""
        val counts = linkedMapOf<String, Int>()
        for (value in trimmed) {
            counts[value] = (counts[value] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }?.key ?: trimmed.first()
    }

    private fun mostFrequentNonEmptyList(lists: List<List<Int>>): List<Int> {
        val nonEmpty = lists.filter { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return emptyList()
        val counts = linkedMapOf<List<Int>, Int>()
        for (list in nonEmpty) {
            counts[list] = (counts[list] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }?.key ?: nonEmpty.first()
    }
}
