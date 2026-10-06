package com.star.schedule.feature.settings.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsListLayoutTest {
    @Test
    fun `reminder details are hidden until reminders are enabled`() {
        val rows = notificationRows(
            reminderEnabled = false,
            wakeUpAvailable = false,
            liveCapsuleAvailable = true,
        )

        assertEquals(listOf(SettingsRow.Reminder, SettingsRow.InstantTest, SettingsRow.DelayedTest), rows)
    }

    @Test
    fun `visible rows receive contiguous segmented positions`() {
        val rows = notificationRows(
            reminderEnabled = true,
            wakeUpAvailable = true,
            liveCapsuleAvailable = true,
        ).positions()

        assertEquals(8, rows.size)
        assertEquals(0, rows.first().index)
        assertEquals(7, rows.last().index)
        assertTrue(rows.all { it.count == rows.size })
    }

    @Test
    fun `startup hint visibility follows preference`() {
        assertTrue(showStartupHint(closed = false))
        assertFalse(showStartupHint(closed = true))
    }
}
