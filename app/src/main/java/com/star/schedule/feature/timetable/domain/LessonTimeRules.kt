package com.star.schedule.feature.timetable.domain

import java.util.Locale

/**
 * 节次时间的纯逻辑：时间文本与分钟数互转、判断两段时间是否真的重叠。
 *
 * 不依赖 Android、Compose 和 Room，单元测试可以直接覆盖。
 * 统一用「一天内的分钟数」比较：字符串 "08:45" 没法直接比大小。
 */
object LessonTimeRules {

    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

    private val timePattern = Regex("""^(\d{1,2}):(\d{2})$""")

    /** 把 "08:00" 解析成分钟数；格式不对或超出 00:00~23:59 时返回 null。 */
    fun parseMinutes(text: String): Int? {
        val match = timePattern.matchEntire(text.trim()) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * MINUTES_PER_HOUR + minute
    }

    /** 分钟数转回 "HH:mm"；固定两位，时间列和编辑浮层都按这个宽度排版。 */
    fun formatMinutes(minutes: Int): String {
        val clamped = minutes.coerceIn(0, MINUTES_PER_DAY - 1)
        return String.format(
            Locale.ROOT,
            "%02d:%02d",
            clamped / MINUTES_PER_HOUR,
            clamped % MINUTES_PER_HOUR,
        )
    }

    /**
     * 判断 [startMinutes] 到 [endMinutes] 是否与已有区间重叠。
     *
     * 首尾相接（上一节 08:45 结束、下一节 08:45 开始）是正常排课，不算冲突。
     */
    fun overlaps(
        startMinutes: Int,
        endMinutes: Int,
        others: List<IntRange>,
    ): Boolean = others.any { span -> startMinutes < span.last && span.first < endMinutes }

    /** 两节之间的空档分钟数，用来提示异常的大间隔。 */
    fun gapMinutes(previousEnd: Int, nextStart: Int): Int = nextStart - previousEnd
}
