package com.star.schedule.db

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DatabaseProvider {
    lateinit var db: AppDatabase
        private set

    fun isInitialized(): Boolean = ::db.isInitialized

    fun init(context: Context) {
        if (!::db.isInitialized) {
            db = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "schedule.db"
            )
                .fallbackToDestructiveMigration(false) // ⚡️ 如果没正式发布，推荐加这个
                .build()

            CoroutineScope(Dispatchers.IO).launch {
                db.scheduleDao().initializeDefaultTimetable()
            }
        }
    }

    fun dao(): ScheduleDao = db.scheduleDao()
}
