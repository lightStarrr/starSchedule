package com.star.schedule.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseMigration12To13Test {

    @Test
    fun migration12To13SplitsCourseIntoCourseAndCourseSession() {
        assertEquals(12, AppDatabase.MIGRATION_12_13.startVersion)
        assertEquals(13, AppDatabase.MIGRATION_12_13.endVersion)

        val executedSql = mutableListOf<String>()
        val fakeDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql += args[0] as String
                Unit
            } else {
                error("Unexpected SupportSQLiteDatabase call: ${method.name}")
            }
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_12_13.migrate(fakeDb)

        assertEquals(9, executedSql.size)
        assertEquals("ALTER TABLE `course` RENAME TO `_legacy_course`", executedSql[0])
        assertEquals("DROP INDEX IF EXISTS `index_course_timetableId`", executedSql[1])
        assertTrue(executedSql[2].contains("CREATE TABLE IF NOT EXISTS `course`"))
        assertTrue(executedSql[3].contains("CREATE INDEX IF NOT EXISTS `index_course_timetableId`"))
        assertTrue(executedSql[4].contains("GROUP BY `timetableId`, TRIM(`name`)"))
        assertTrue(executedSql[5].contains("CREATE TABLE IF NOT EXISTS `course_session`"))
        assertTrue(executedSql[6].contains("CREATE INDEX IF NOT EXISTS `index_course_session_courseId`"))
        assertTrue(executedSql[7].contains("INSERT INTO `course_session`"))
        assertTrue(executedSql[7].contains("SELECT lc.`id`, c.`id`, lc.`teacher`, lc.`location`"))
        assertEquals("DROP TABLE `_legacy_course`", executedSql[8])
    }
}
