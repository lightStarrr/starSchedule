package com.star.schedule.feature.holiday.domain

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** 一天的节假日信息：[isOffDay] 为 false 时是调休上班日（通常是周末）。 */
data class HolidayDay(
    val date: LocalDate,
    val name: String,
    val isOffDay: Boolean,
)

/** 节假日本地缓存读写边界，供数据源写入、业务层查询。 */
interface HolidayStore {

    /** 整年替换该年的节假日数据。 */
    suspend fun replaceYear(year: Int, days: List<HolidayDay>)

    suspend fun holidayOn(date: LocalDate): HolidayDay?

    suspend fun holidaysOfYear(year: Int): List<HolidayDay>

    fun observeRange(start: LocalDate, end: LocalDate): Flow<List<HolidayDay>>

    /** 缓存是否为空，用于判断要不要灌内置快照。 */
    suspend fun isEmpty(): Boolean
}
