package com.star.schedule.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDatabaseMigration11To12Test {

    @Test
    fun migration11To12RebuildsTimetableWithoutAutoUpdateJson() {
        assertEquals(11, AppDatabase.MIGRATION_11_12.startVersion)
        assertEquals(12, AppDatabase.MIGRATION_11_12.endVersion)

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

        AppDatabase.MIGRATION_11_12.migrate(fakeDb)

        assertEquals(4, executedSql.size)
        assertTrue(executedSql[0].contains("CREATE TABLE IF NOT EXISTS `_new_timetable`"))
        assertFalse(executedSql[0].contains("autoUpdateJson"))
        assertTrue(executedSql[1].contains("INSERT INTO `_new_timetable`"))
        assertTrue(executedSql[1].contains("FROM `timetable`"))
        assertEquals("DROP TABLE `timetable`", executedSql[2])
        assertEquals("ALTER TABLE `_new_timetable` RENAME TO `timetable`", executedSql[3])
    }
}
