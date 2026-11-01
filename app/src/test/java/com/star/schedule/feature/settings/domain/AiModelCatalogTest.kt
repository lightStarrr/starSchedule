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

    @Test
    fun `default model follows server alias`() {
        val withDefault = catalog.copy(defaultAlias = "deepseek-reasoner")

        assertEquals("DeepSeek Reasoner", withDefault.defaultModel?.displayName)
    }

    @Test
    fun `blank default alias falls back to the first model`() {
        val withBlank = catalog.copy(defaultAlias = "")

        assertEquals("DeepSeek Chat", withBlank.defaultModel?.displayName)
    }

    @Test
    fun `default alias pointing at a removed model falls back to the first model`() {
        val stale = catalog.copy(defaultAlias = "retired-model")

        assertEquals("DeepSeek Chat", stale.defaultModel?.displayName)
    }

    @Test
    fun `empty catalog has no default model`() {
        assertNull(AiModelCatalog().defaultModel)
    }
}
