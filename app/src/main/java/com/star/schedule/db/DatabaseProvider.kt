package com.star.schedule.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
                .addMigrations(MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
                .build()

            CoroutineScope(Dispatchers.IO).launch {
                db.scheduleDao().initializeDefaultTimetable()
            }
        }
    }

    fun dao(): ScheduleDao = db.scheduleDao()

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE timetable ADD COLUMN showFuture INTEGER NOT NULL DEFAULT 0")
        }
    }
}
