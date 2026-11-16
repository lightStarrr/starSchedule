package com.star.schedule.feature.settings.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsListLayoutTest {
    @Test
    fun `reminder row stays visible when reminders are off`() {
        val rows = notificationRows(wakeUpAvailable = false)

        assertEquals(
            listOf(SettingsRow.Reminder),
            rows,
        )
    }

    @Test
    fun `visible rows receive contiguous segmented positions`() {
        val rows = notificationRows(wakeUpAvailable = true).positions()

        assertEquals(2, rows.size)
        assertEquals(0, rows.first().index)
        assertEquals(1, rows.last().index)
        assertTrue(rows.all { it.count == rows.size })
    }
}
