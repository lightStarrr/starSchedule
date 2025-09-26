// AppDatabase.kt
package com.star.schedule.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PreferenceEntity::class,
        TimetableEntity::class,
        LessonTimeEntity::class,
        CourseEntity::class,
        ReminderEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class) // 注册 TypeConverter
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
}
