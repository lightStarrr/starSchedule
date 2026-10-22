package com.star.schedule.feature.timetable.domain

import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.LessonTimeEntity
import java.util.Locale
import kotlin.random.Random

/** 行高页在课表还没排课时代替真实数据的示例课表。 */
data class RowHeightPreviewTimetable(
    val lessonTimes: List<LessonTimeEntity>,
    val courses: List<CourseEntity>,
)

/**
 * 行高页的示例数据：课表里还没有节次时间时，页面只剩一排空白表头，滑杆怎么拖都看不出变化。
 *
 * 这里造一份「只有形状没有文字」的假课表：固定 8 节的时间表 + 随机散布的课程块。
 * 课程名称和地点都是一个空格——格子按真实密度排出来，但不会有人误以为是真课程。
 *
 * 随机只在页面打开时做一次（调用方自己持有结果），拖动滑杆不能让格子重新洗牌。
 */
object RowHeightPreviewSample {

    const val PERIOD_COUNT = 8

    /** 第一节 08:00 开始，每节 45 分钟、课间 5 分钟，和常见作息一致。 */
    private const val FIRST_START_MINUTES = 8 * 60
    private const val PERIOD_MINUTES = 45
    private const val BREAK_MINUTES = 5

    /** 一块课连续占两节，够看出行高变化，又不会把整列填满。 */
    private const val BLOCK_PERIODS = 2
    private const val PLACE_CHANCE_PERCENT = 35
    private const val DAYS = 5

    /** 占位文案：一个空格，Text 仍会排版但不显示任何字形。 */
    private const val BLANK_TEXT = " "

    /** 示例数据不落库，时间表的 id 和课表 id 都按 0 处理。 */
    private const val SAMPLE_TIMETABLE_ID = 0L
    private const val SAMPLE_WEEK = 1

    fun create(random: Random = Random.Default): RowHeightPreviewTimetable =
        RowHeightPreviewTimetable(
            lessonTimes = lessonTimes(),
            courses = courses(random),
        )

    fun lessonTimes(): List<LessonTimeEntity> = (0 until PERIOD_COUNT).map { index ->
        val start = FIRST_START_MINUTES + index * (PERIOD_MINUTES + BREAK_MINUTES)
        LessonTimeEntity(
            timetableId = SAMPLE_TIMETABLE_ID,
            period = index + 1,
            startTime = formatTime(start),
            endTime = formatTime(start + PERIOD_MINUTES),
        )
    }

    /**
     * 每天从第一节往后走：随机决定这一节要不要放一块两节的课，放了就跳过这块，
     * 再随机空出 0~1 节，保证同一天内不会重叠。
     */
    fun courses(random: Random): List<CourseEntity> = buildList {
        for (day in 1..DAYS) {
            var period = 1
            while (period + BLOCK_PERIODS - 1 <= PERIOD_COUNT) {
                if (random.nextInt(100) < PLACE_CHANCE_PERCENT) {
                    add(
                        CourseEntity(
                            timetableId = SAMPLE_TIMETABLE_ID,
                            name = BLANK_TEXT,
                            teacher = "",
                            location = BLANK_TEXT,
                            dayOfWeek = day,
                            periods = (period until period + BLOCK_PERIODS).toList(),
                            weeks = listOf(SAMPLE_WEEK),
                        ),
                    )
                    period += BLOCK_PERIODS + random.nextInt(2)
                } else {
                    period += 1
                }
            }
        }
    }.ifEmpty {
        // 概率上几乎不会发生，但真出现「一块都没有」时页面又变成空白了，兜一块在周一。
        listOf(
            CourseEntity(
                timetableId = SAMPLE_TIMETABLE_ID,
                name = BLANK_TEXT,
                teacher = "",
                location = BLANK_TEXT,
                dayOfWeek = 1,
                periods = (1..BLOCK_PERIODS).toList(),
                weeks = listOf(SAMPLE_WEEK),
            ),
        )
    }

    private fun formatTime(minutes: Int): String = String.format(
        Locale.ROOT,
        "%02d:%02d",
        minutes / 60,
        minutes % 60,
    )
}
