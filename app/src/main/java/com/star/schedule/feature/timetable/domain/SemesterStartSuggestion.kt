package com.star.schedule.feature.timetable.domain

import com.star.schedule.feature.holiday.domain.HolidayDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

/**
 * 手动创建课表时的开学日期推荐。
 *
 * 秋季开学有惯例（9 月 1 日），春季跟着春节走、公历每年漂移，没法写死。
 * 这里用节假日数据源推算：国内高校普遍在春节法定假结束后的一两周内开课，
 * 取「春节假最后一天之后的第一个周一」，落到课表周次上最多差一周，
 * 作为默认值交给用户在日期选择器里微调。
 */
object SemesterStartSuggestion {

    /**
     * 推荐开学日期：在「今年春/秋 + 明年春/秋」四个候选里取离 [today] 最近的。
     *
     * 年底创建时明年春季会自然胜出，年中创建时即将到来的秋季胜出。
     * [holidaysOfYear] 返回某年的节假日数据（可为空列表，春季推算有兜底）。
     */
    fun suggest(
        today: LocalDate,
        holidaysOfYear: (Int) -> List<HolidayDay>,
    ): LocalDate {
        val year = today.year
        return listOf(
            springStart(year, holidaysOfYear(year)),
            fallStart(year),
            springStart(year + 1, holidaysOfYear(year + 1)),
            fallStart(year + 1),
        ).minBy { abs(ChronoUnit.DAYS.between(today, it)) }
    }

    /** 春季开学日：春节法定假最后一天之后的第一个周一。 */
    fun springStart(year: Int, holidays: List<HolidayDay>): LocalDate {
        val lastSpringFestivalOffDay = holidays
            .filter { it.isOffDay && it.name.contains(SPRING_FESTIVAL) }
            .maxByOrNull { it.date }
            ?.date

        return lastSpringFestivalOffDay?.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            ?: LocalDate.of(year, 2, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY))
    }

    private fun fallStart(year: Int): LocalDate = LocalDate.of(year, 9, 1)

    private const val SPRING_FESTIVAL = "春节"
}
