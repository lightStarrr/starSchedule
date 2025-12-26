// AppDatabase.kt
package com.star.schedule.db

import com.star.schedule.Constants
import com.star.schedule.autoupdate.LidaJwAutoUpdateConfig
import com.star.schedule.autoupdate.QiangzhiJwAutoUpdateConfig
import com.star.schedule.autoupdate.TimetableAutoUpdateJson
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
        LessonTimeTemplateEntity::class,
        LessonTimeTemplateItemEntity::class,
        CourseEntity::class,
        ReminderEntity::class,
        DayNoteEntity::class
    ],
    version = 10,
    exportSchema = false
)
@TypeConverters(Converters::class) // 注册 TypeConverter
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        // 从版本3迁移到版本4，添加rowHeight字段
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 添加rowHeight列，默认值为60
                db.execSQL("ALTER TABLE timetable ADD COLUMN rowHeight INTEGER NOT NULL DEFAULT 60")
            }
        }

        // 从版本4迁移到版本5，添加reminderTime字段
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 添加reminderTime列，默认值为15
                db.execSQL("ALTER TABLE timetable ADD COLUMN reminderTime INTEGER NOT NULL DEFAULT 15")
            }
        }

        // 从版本5迁移到版本6，添加teacher字段
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 添加teacher列，默认值为空字符串
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
                    """.trimIndent()
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
                    """.trimIndent()
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
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lesson_time_template_item_templateId ON lesson_time_template_item(templateId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_lesson_time_template_item_templateId_period ON lesson_time_template_item(templateId, period)")
            }
        }

        // 从版本8迁移到版本9：在课程表中新增自动更新配置字段（JSON）
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE timetable ADD COLUMN autoUpdateJson TEXT")

                // 兼容旧版本：把偏好里保存的立达教务账号密码迁移到对应课程表的 autoUpdateJson
                val account = db.query(
                    "SELECT value FROM preference WHERE prefKey = ? LIMIT 1",
                    arrayOf(Constants.PREF_LIDA_JW_ACCOUNT)
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else ""
                }
                val password = db.query(
                    "SELECT value FROM preference WHERE prefKey = ? LIMIT 1",
                    arrayOf(Constants.PREF_LIDA_JW_PASSWORD)
                ).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else ""
                }

                if (account.isNotBlank() && password.isNotBlank()) {
                    val configJson = TimetableAutoUpdateJson.encode(
                        QiangzhiJwAutoUpdateConfig(
                            baseUrl = "http://jw.lidapoly.edu.cn/shldzyjsxy_jsxsd",
                            account = account,
                            password = password
                        )
                    )
                    db.execSQL(
                        "UPDATE timetable SET autoUpdateJson = ? WHERE name LIKE ?",
                        arrayOf(configJson, "上海立达学院%")
                    )
                }
            }
        }

        // 从版本9迁移到版本10：修复旧版本写入的 JSON 缺少 type 字段的问题
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.query("SELECT id, autoUpdateJson FROM timetable WHERE autoUpdateJson IS NOT NULL").use { cursor ->
                    val idIndex = cursor.getColumnIndex("id")
                    val jsonIndex = cursor.getColumnIndex("autoUpdateJson")
                    while (cursor.moveToNext()) {
                        val timetableId = cursor.getLong(idIndex)
                        val rawJson = cursor.getString(jsonIndex).orEmpty()
                        if (rawJson.isBlank()) continue
                        if (!TimetableAutoUpdateJson.getType(rawJson).isNullOrBlank()) continue

                        val config = TimetableAutoUpdateJson.decodeAs<LidaJwAutoUpdateConfig>(rawJson) ?: continue
                        val fixedJson = TimetableAutoUpdateJson.encode(
                            QiangzhiJwAutoUpdateConfig(
                                baseUrl = "http://jw.lidapoly.edu.cn/shldzyjsxy_jsxsd",
                                account = config.account,
                                password = config.password
                            )
                        )
                        db.execSQL(
                            "UPDATE timetable SET autoUpdateJson = ? WHERE id = ?",
                            arrayOf<Any>(fixedJson, timetableId)
                        )
                    }
                }
            }
        }
    }
}
