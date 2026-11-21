package com.star.schedule.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.star.schedule.core.database.entity.ReminderEntity

@Dao
abstract class ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertReminder(reminder: ReminderEntity)

    @Query("SELECT * FROM reminder")
    abstract suspend fun getAllReminders(): List<ReminderEntity>

    @Query("DELETE FROM reminder WHERE requestCode = :requestCode")
    abstract suspend fun deleteReminder(requestCode: Int)

    @Query("DELETE FROM reminder")
    abstract suspend fun deleteAllReminders()
}
