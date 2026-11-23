package com.star.schedule.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.star.schedule.core.database.entity.PreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PreferenceDao {
    @Query("SELECT value FROM preference WHERE prefKey = :prefKey LIMIT 1")
    abstract fun getPreferenceFlow(prefKey: String): Flow<String?>

    @Query(
        """
        SELECT p.value FROM preference AS p
        INNER JOIN timetable AS t ON p.prefKey = :prefix || t.id
        WHERE p.value != ''
        """,
    )
    abstract fun observeValuesForTimetables(prefix: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPreference(preference: PreferenceEntity)

    @Transaction
    open suspend fun setPreference(key: String, value: String) {
        insertPreference(PreferenceEntity(key, value))
    }
}
