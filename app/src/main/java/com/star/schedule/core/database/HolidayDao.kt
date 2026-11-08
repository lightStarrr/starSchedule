package com.star.schedule.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HolidayDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertHolidays(holidays: List<HolidayEntity>)

    @Query("DELETE FROM holiday WHERE date BETWEEN :startDate AND :endDate")
    abstract suspend fun deleteHolidaysBetween(startDate: String, endDate: String)

    /**
     * 整年替换：假期安排由国务院逐年发布，拿到某年的新数据后该年整段重写，
     * 年份之间互不交叉（跨年假期会拆在各自年份的文件里）。
     */
    @Transaction
    open suspend fun replaceHolidaysForYear(year: Int, holidays: List<HolidayEntity>) {
        deleteHolidaysBetween("$year-01-01", "$year-12-31")
        insertHolidays(holidays)
    }

    @Query("SELECT * FROM holiday WHERE date = :date LIMIT 1")
    abstract suspend fun getHolidayOn(date: String): HolidayEntity?

    @Query("SELECT * FROM holiday WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    abstract fun getHolidaysFlow(startDate: String, endDate: String): Flow<List<HolidayEntity>>

    @Query("SELECT * FROM holiday WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    abstract suspend fun getHolidaysOnce(startDate: String, endDate: String): List<HolidayEntity>

    @Query("SELECT COUNT(*) FROM holiday")
    abstract suspend fun getHolidayCount(): Int
}
