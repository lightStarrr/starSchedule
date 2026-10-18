package com.star.schedule.feature.settings.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSettingsLayoutTest {
    @Test
    fun `capsule options are omitted when live capsule is unavailable`() {
        val rows = reminderDetailRows(liveCapsuleAvailable = false)

        assertEquals(listOf(ReminderDetailRow.OnlyFirst, ReminderDetailRow.Icon), rows)
    }

    @Test
    fun `detail rows receive contiguous segmented positions`() {
        val rows = reminderDetailRows(liveCapsuleAvailable = true).detailPositions()

        assertEquals(4, rows.size)
        assertEquals(0, rows.first().index)
        assertEquals(3, rows.last().index)
        assertTrue(rows.all { it.count == rows.size })
    }
}
