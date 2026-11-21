package com.star.schedule.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.star.schedule.core.database.entity.DayNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DayNoteDao {
    @Query("SELECT * FROM day_note WHERE timetableId = :timetableId ORDER BY date ASC")
    abstract fun getDayNotesFlow(timetableId: Long): Flow<List<DayNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertDayNote(note: DayNoteEntity): Long

    @Query("DELETE FROM day_note WHERE timetableId = :timetableId AND date = :date")
    abstract suspend fun deleteDayNote(timetableId: Long, date: String)
}
