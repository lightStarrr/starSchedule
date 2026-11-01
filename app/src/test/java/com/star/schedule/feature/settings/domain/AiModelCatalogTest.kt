package com.star.schedule.feature.settings.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiModelCatalogTest {
    private val catalog = AiModelCatalog(
        models = listOf(
            AiModel(alias = "deepseek-chat", displayName = "DeepSeek Chat"),
            AiModel(alias = "deepseek-reasoner", displayName = "DeepSeek Reasoner"),
        ),
    )

    @Test
    fun `finds model by alias`() {
        assertEquals("DeepSeek Reasoner", catalog.find("deepseek-reasoner")?.displayName)
    }

    @Test
    fun `blank alias means follow server default`() {
        assertNull(catalog.find(null))
        assertNull(catalog.find(""))
    }

    @Test
    fun `unknown alias resolves to nothing`() {
        assertNull(catalog.find("retired-model"))
    }
}
