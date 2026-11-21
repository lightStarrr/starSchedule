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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPreference(preference: PreferenceEntity)

    @Transaction
    open suspend fun setPreference(key: String, value: String) {
        insertPreference(PreferenceEntity(key, value))
    }
}
