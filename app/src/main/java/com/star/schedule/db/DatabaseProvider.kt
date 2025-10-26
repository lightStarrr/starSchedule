package com.star.schedule.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

object DatabaseProvider {
    private val lock = Any()
    lateinit var db: AppDatabase
        private set

    fun isInitialized(): Boolean = ::db.isInitialized

    fun init(context: Context) {
        // 使用同步锁确保线程安全
        synchronized(lock) {
            if (!::db.isInitialized) {
                android.util.Log.d("DatabaseProvider", "Initializing database...")
                
                db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "schedule.db"
                )
                    .addMigrations(MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6)
                    .build()

                // 确保在初始化后就有“当前课表”，避免其他读取逻辑拿到空值
                runBlocking {
                    withContext(Dispatchers.IO) {
                        db.scheduleDao().initializeDefaultTimetable()
                    }
                }
                
                android.util.Log.d("DatabaseProvider", "Database initialized successfully")
            } else {
                android.util.Log.d("DatabaseProvider", "Database already initialized")
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
