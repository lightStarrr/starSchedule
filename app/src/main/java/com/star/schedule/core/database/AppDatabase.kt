package com.star.schedule.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.star.schedule.core.database.converter.Converters
import com.star.schedule.core.database.dao.CourseDao
import com.star.schedule.core.database.dao.DayNoteDao
import com.star.schedule.core.database.dao.HolidayDao
import com.star.schedule.core.database.dao.LessonTimeDao
import com.star.schedule.core.database.dao.LessonTimeTemplateDao
import com.star.schedule.core.database.dao.PreferenceDao
import com.star.schedule.core.database.dao.ReminderDao
import com.star.schedule.core.database.dao.TimetableDao
import com.star.schedule.core.database.entity.CourseEntity
import com.star.schedule.core.database.entity.DayNoteEntity
import com.star.schedule.core.database.entity.HolidayEntity
import com.star.schedule.core.database.entity.LessonTimeEntity
import com.star.schedule.core.database.entity.LessonTimeTemplateEntity
import com.star.schedule.core.database.entity.LessonTimeTemplateItemEntity
import com.star.schedule.core.database.entity.PreferenceEntity
import com.star.schedule.core.database.entity.ReminderEntity
import com.star.schedule.core.database.entity.TimetableEntity

@Database(
    entities = [
        PreferenceEntity::class,
        TimetableEntity::class,
        LessonTimeEntity::class,
        LessonTimeTemplateEntity::class,
        LessonTimeTemplateItemEntity::class,
        CourseEntity::class,
        ReminderEntity::class,
        DayNoteEntity::class,
        HolidayEntity::class,
    ],
    version = 12,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun preferenceDao(): PreferenceDao
    abstract fun timetableDao(): TimetableDao
    abstract fun lessonTimeDao(): LessonTimeDao
    abstract fun lessonTimeTemplateDao(): LessonTimeTemplateDao
    abstract fun courseDao(): CourseDao
    abstract fun reminderDao(): ReminderDao
    abstract fun dayNoteDao(): DayNoteDao
    abstract fun holidayDao(): HolidayDao

    companion object {
        // 从版本3迁移到版本4，添加rowHeight字段
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE timetable ADD COLUMN rowHeight INTEGER NOT NULL DEFAULT 60")
            }
        }

        // 从版本4迁移到版本5，添加reminderTime字段
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE timetable ADD COLUMN reminderTime INTEGER NOT NULL DEFAULT 15")
            }
        }

        // 从版本5迁移到版本6，添加teacher字段
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE course ADD COLUMN teacher TEXT NOT NULL DEFAULT ''")
            }
        }

        // 从版本6迁移到版本7，新增日便签表
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS day_note (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timetableId INTEGER NOT NULL,
                        date TEXT NOT NULL,
                        content TEXT NOT NULL,
                        FOREIGN KEY(timetableId) REFERENCES timetable(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_day_note_timetableId_date ON day_note(timetableId, date)")
            }
        }

        // 从版本7迁移到版本8，新增课程时间模板表
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lesson_time_template (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_lesson_time_template_name ON lesson_time_template(name)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lesson_time_template_item (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        templateId INTEGER NOT NULL,
                        period INTEGER NOT NULL,
                        startTime TEXT NOT NULL,
                        endTime TEXT NOT NULL,
                        FOREIGN KEY(templateId) REFERENCES lesson_time_template(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lesson_time_template_item_templateId ON lesson_time_template_item(templateId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_lesson_time_template_item_templateId_period ON lesson_time_template_item(templateId, period)")
            }
        }

        // 从版本8迁移到版本9：在课程表中新增自动更新配置字段（JSON）
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE timetable ADD COLUMN autoUpdateJson TEXT")
            }
        }

        // 从版本9迁移到版本10：历史占位迁移
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        // 从版本10迁移到版本11：新增法定节假日缓存表
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS holiday (
                        date TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        isOffDay INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        // 从版本11迁移到版本12：移除废弃的自动更新配置列 autoUpdateJson。
        // minSdk=33 的系统 SQLite 尚未支持 ALTER TABLE DROP COLUMN，采用重建表方式保留原有数据与外键目标。
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `_new_timetable` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `showWeekend` INTEGER NOT NULL,
                        `startDate` TEXT NOT NULL,
                        `showFuture` INTEGER NOT NULL,
                        `rowHeight` INTEGER NOT NULL,
                        `reminderTime` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `_new_timetable` (`id`, `name`, `showWeekend`, `startDate`, `showFuture`, `rowHeight`, `reminderTime`)
                    SELECT `id`, `name`, `showWeekend`, `startDate`, `showFuture`, `rowHeight`, `reminderTime` FROM `timetable`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `timetable`")
                db.execSQL("ALTER TABLE `_new_timetable` RENAME TO `timetable`")
            }
        }
    }
}
