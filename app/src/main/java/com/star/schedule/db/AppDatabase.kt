// AppDatabase.kt
package com.star.schedule.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PreferenceEntity::class,
        TimetableEntity::class,
        LessonTimeEntity::class,
        CourseEntity::class,
        ReminderEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class) // 注册 TypeConverter
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        // 从版本3迁移到版本4，添加rowHeight字段
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加rowHeight列，默认值为60
                database.execSQL("ALTER TABLE timetable ADD COLUMN rowHeight INTEGER NOT NULL DEFAULT 60")
            }
        }
    }
}
