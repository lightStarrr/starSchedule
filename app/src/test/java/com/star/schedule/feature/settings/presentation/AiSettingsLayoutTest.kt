package com.star.schedule.feature.settings.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class AiSettingsLayoutTest {
    @Test
    fun `only provider stays when cloud is not selected`() {
        assertEquals(
            listOf(AiSettingsRow.Provider),
            aiSettingsRows(cloudSelected = false),
        )
    }

    @Test
    fun `quota joins the provider group when cloud is selected`() {
        assertEquals(
            listOf(AiSettingsRow.Provider, AiSettingsRow.Quota),
            aiSettingsRows(cloudSelected = true),
        )
    }

    @Test
    fun `single row spans the whole group`() {
        val positions = aiSettingsRows(cloudSelected = false).positions()

        assertEquals(1, positions.size)
        assertEquals(0, positions.single().index)
        assertEquals(1, positions.single().count)
    }

    @Test
    fun `two rows share one segmented group`() {
        val positions = aiSettingsRows(cloudSelected = true).positions()

        assertEquals(listOf(0, 1), positions.map { it.index })
        assertEquals(listOf(2, 2), positions.map { it.count })
    }
}
