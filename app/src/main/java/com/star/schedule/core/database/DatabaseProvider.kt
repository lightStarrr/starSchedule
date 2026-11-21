package com.star.schedule.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object DatabaseProvider {
    private val lock = Any()
    private val initValidationLock = Mutex()
    @Volatile
    private var currentTimetableValidated = false
    lateinit var db: AppDatabase
        private set

    fun isInitialized(): Boolean = ::db.isInitialized

    fun init(context: Context) {
        // 使用同步锁确保线程安全；仅构建惰性 Room 实例，绝不在主线程阻塞打开磁盘或跑迁移。
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
                        AppDatabase.MIGRATION_9_10,
                        AppDatabase.MIGRATION_10_11,
                        AppDatabase.MIGRATION_11_12,
                    )
                    .build()

                android.util.Log.d("DatabaseProvider", "Database initialized successfully")
            } else {
                android.util.Log.d("DatabaseProvider", "Database already initialized")
            }
        }
    }

    /**
     * 在后台线程校验一次当前课表指向，不再自动建「默认课表」。
     * 读取方一律按「当前课表可能不存在」处理（PREF_CURRENT_TIMETABLE 为空字符串）。
     */
    suspend fun ensureValidCurrentTimetable() {
        if (currentTimetableValidated || !::db.isInitialized) return
        initValidationLock.withLock {
            if (currentTimetableValidated || !::db.isInitialized) return
            withContext(Dispatchers.IO) {
                db.scheduleDao().ensureValidCurrentTimetable()
            }
            currentTimetableValidated = true
        }
    }

    fun dao(): ScheduleDao = db.scheduleDao()

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE timetable ADD COLUMN showFuture INTEGER NOT NULL DEFAULT 0")
        }
    }
}
