package com.star.schedule.core.database

import android.content.ContentValues
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @Test
    fun migrate11To12DropsAutoUpdateJsonAndPreservesTimetableAndChildRows() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "migration-11-12-test.db"
        context.deleteDatabase(dbName)

        try {
            val helper = FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(dbName)
                    .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createV11Schema(db)
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    })
                    .build(),
            )
            helper.writableDatabase.use { db ->
                db.insert(
                    "timetable",
                    0,
                    ContentValues().apply {
                        put("id", 7L)
                        put("name", "2026 秋季学期")
                        put("showWeekend", 1)
                        put("startDate", "2026-09-01")
                        put("showFuture", 1)
                        put("rowHeight", 68)
                        put("reminderTime", 20)
                        put("autoUpdateJson", """{"type":"qiangzhi","password":"legacy"}""")
                    },
                )
                db.insert(
                    "lesson_time",
                    0,
                    ContentValues().apply {
                        put("id", 11L)
                        put("timetableId", 7L)
                        put("period", 1)
                        put("startTime", "08:00")
                        put("endTime", "08:45")
                    },
                )
                db.insert(
                    "course",
                    0,
                    ContentValues().apply {
                        put("id", 21L)
                        put("timetableId", 7L)
                        put("name", "高等数学")
                        put("teacher", "张老师")
                        put("location", "教一101")
                        put("dayOfWeek", 1)
                        put("periods", "1,2")
                        put("weeks", "1,2,3")
                    },
                )
                db.insert(
                    "day_note",
                    0,
                    ContentValues().apply {
                        put("id", 31L)
                        put("timetableId", 7L)
                        put("date", "2026-09-01")
                        put("content", "开学第一课")
                    },
                )
            }
            helper.close()

            val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .addMigrations(AppDatabase.MIGRATION_11_12)
                .build()
            try {
                val dao = migratedDb.scheduleDao()
                val timetables = dao.getAllTimetablesOnce()
                assertEquals(1, timetables.size)
                assertEquals(
                    TimetableEntity(
                        id = 7L,
                        name = "2026 秋季学期",
                        showWeekend = true,
                        startDate = "2026-09-01",
                        showFuture = true,
                        rowHeight = 68,
                        reminderTime = 20,
                    ),
                    timetables.single(),
                )
                assertEquals(1, dao.getLessonTimesOnce(7L).size)
                assertEquals(1, dao.getCoursesFlow(7L).first().size)
                assertEquals(1, dao.getDayNotesFlow(7L).first().size)

                val columns = mutableSetOf<String>()
                migratedDb.openHelper.readableDatabase.query("PRAGMA table_info(`timetable`)").use { cursor ->
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        columns += cursor.getString(nameIndex)
                    }
                }
                assertFalse("autoUpdateJson 应已从 timetable 表移除", "autoUpdateJson" in columns)

                // 删除课表时外键级联仍应生效。
                dao.deleteTimetable(timetables.single())
                assertTrue(dao.getLessonTimesOnce(7L).isEmpty())
                assertTrue(dao.getCoursesFlow(7L).first().isEmpty())
                assertTrue(dao.getDayNotesFlow(7L).first().isEmpty())
            } finally {
                migratedDb.close()
            }
        } finally {
            context.deleteDatabase(dbName)
        }
    }

    private fun createV11Schema(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `preference` (`prefKey` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`prefKey`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `timetable` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `showWeekend` INTEGER NOT NULL, `startDate` TEXT NOT NULL, `showFuture` INTEGER NOT NULL, `rowHeight` INTEGER NOT NULL, `reminderTime` INTEGER NOT NULL, `autoUpdateJson` TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `lesson_time` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timetableId` INTEGER NOT NULL, `period` INTEGER NOT NULL, `startTime` TEXT NOT NULL, `endTime` TEXT NOT NULL, FOREIGN KEY(`timetableId`) REFERENCES `timetable`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lesson_time_timetableId` ON `lesson_time` (`timetableId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `lesson_time_template` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lesson_time_template_name` ON `lesson_time_template` (`name`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `lesson_time_template_item` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `templateId` INTEGER NOT NULL, `period` INTEGER NOT NULL, `startTime` TEXT NOT NULL, `endTime` TEXT NOT NULL, FOREIGN KEY(`templateId`) REFERENCES `lesson_time_template`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_lesson_time_template_item_templateId` ON `lesson_time_template_item` (`templateId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_lesson_time_template_item_templateId_period` ON `lesson_time_template_item` (`templateId`, `period`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `course` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timetableId` INTEGER NOT NULL, `name` TEXT NOT NULL, `teacher` TEXT NOT NULL, `location` TEXT NOT NULL, `dayOfWeek` INTEGER NOT NULL, `periods` TEXT NOT NULL, `weeks` TEXT NOT NULL, FOREIGN KEY(`timetableId`) REFERENCES `timetable`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_course_timetableId` ON `course` (`timetableId`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `reminder` (`requestCode` INTEGER NOT NULL, `courseId` INTEGER NOT NULL, `date` TEXT NOT NULL, `period` INTEGER NOT NULL, PRIMARY KEY(`requestCode`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `day_note` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timetableId` INTEGER NOT NULL, `date` TEXT NOT NULL, `content` TEXT NOT NULL, FOREIGN KEY(`timetableId`) REFERENCES `timetable`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_day_note_timetableId_date` ON `day_note` (`timetableId`, `date`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `holiday` (`date` TEXT NOT NULL, `name` TEXT NOT NULL, `isOffDay` INTEGER NOT NULL, PRIMARY KEY(`date`))")
    }
}
