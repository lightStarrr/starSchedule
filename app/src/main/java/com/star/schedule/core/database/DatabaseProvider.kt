package com.star.schedule.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.star.schedule.R
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
                    .addMigrations(
                        MIGRATION_2_3,
                        AppDatabase.MIGRATION_3_4,
                        AppDatabase.MIGRATION_4_5,
                        AppDatabase.MIGRATION_5_6,
                        AppDatabase.MIGRATION_6_7,
                        AppDatabase.MIGRATION_7_8,
                        AppDatabase.MIGRATION_8_9,
                        AppDatabase.MIGRATION_9_10
                    )
                    .build()

                // 不再自动建「默认课表」：首次启动就是空的，由用户自己导入或新建。
                // 读取方一律按「当前课表可能不存在」处理（PREF_CURRENT_TIMETABLE 为空字符串）。
                runBlocking {
                    withContext(Dispatchers.IO) {
                        db.scheduleDao().ensureValidCurrentTimetable()
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
