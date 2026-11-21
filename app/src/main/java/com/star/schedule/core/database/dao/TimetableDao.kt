package com.star.schedule.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.star.schedule.core.database.entity.TimetableEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TimetableDao {
    @Query("SELECT * FROM timetable ORDER BY id ASC")
    abstract fun getAllTimetables(): Flow<List<TimetableEntity>>

    @Query("SELECT * FROM timetable WHERE id = :id LIMIT 1")
    abstract fun getTimetableFlow(id: Long): Flow<TimetableEntity?>

    @Insert
    abstract suspend fun insertTimetable(timetable: TimetableEntity): Long

    @Update
    abstract suspend fun updateTimetable(timetable: TimetableEntity)

    @Delete
    abstract suspend fun deleteTimetable(timetable: TimetableEntity)

    @Query("SELECT * FROM timetable ORDER BY id ASC")
    abstract suspend fun getAllTimetablesOnce(): List<TimetableEntity>
}
