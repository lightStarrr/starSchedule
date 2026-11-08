package com.star.schedule.feature.holiday.data

import com.star.schedule.core.database.HolidayDao
import com.star.schedule.core.database.HolidayEntity
import com.star.schedule.feature.holiday.domain.HolidayDay
import com.star.schedule.feature.holiday.domain.HolidayStore
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomHolidayStore(
    private val dao: HolidayDao,
) : HolidayStore {

    override suspend fun replaceYear(year: Int, days: List<HolidayDay>) {
        dao.replaceHolidaysForYear(
            year,
            days.map { day ->
                HolidayEntity(
                    date = day.date.toString(),
                    name = day.name,
                    isOffDay = day.isOffDay,
                )
            },
        )
    }

    override suspend fun holidayOn(date: LocalDate): HolidayDay? =
        dao.getHolidayOn(date.toString())?.toModel()

    override suspend fun holidaysOfYear(year: Int): List<HolidayDay> =
        dao.getHolidaysOnce("$year-01-01", "$year-12-31").map { it.toModel() }

    override fun observeRange(start: LocalDate, end: LocalDate): Flow<List<HolidayDay>> =
        dao.getHolidaysFlow(start.toString(), end.toString())
            .map { entities -> entities.map { it.toModel() } }

    override suspend fun isEmpty(): Boolean = dao.getHolidayCount() == 0

    private fun HolidayEntity.toModel() = HolidayDay(
        date = LocalDate.parse(date),
        name = name,
        isOffDay = isOffDay,
    )
}
