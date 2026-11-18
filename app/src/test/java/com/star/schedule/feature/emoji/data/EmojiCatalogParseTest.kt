package com.star.schedule.feature.emoji.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EmojiCatalogParseTest {

    @Test
    fun `parses groups headers comments and emoji lines`() {
        val text = """
            # comment line
            @smileys
            😀
            😁

            @food
            🍎
            # trailing comment
            🍇
        """.trimIndent()

        val groups = parseEmojiGroups(text)

        assertEquals(listOf("smileys", "food"), groups.map { it.id })
        assertEquals(listOf("😀", "😁"), groups[0].emojis)
        assertEquals(listOf("🍎", "🍇"), groups[1].emojis)
    }

    @Test
    fun `emoji before the first header are ignored`() {
        val groups = parseEmojiGroups("😀\n@smileys\n😁")
        assertEquals(1, groups.size)
        assertEquals(listOf("😁"), groups[0].emojis)
    }

    @Test
    fun `empty input yields no groups`() {
        assertEquals(emptyList<EmojiGroup>(), parseEmojiGroups(""))
    }

    @Test
    fun `variation selectors are stripped so monochrome glyphs render`() {
        // ⛷️ = U+26F7 + FE0F；1️⃣ = 31 + FE0F + 20E3；纯 ZWJ 序列不受影响。
        assertEquals("⛷", EmojiCatalog.stripVariationSelectors("⛷️"))
        assertEquals("1⃣", EmojiCatalog.stripVariationSelectors("1️⃣"))
        assertEquals("⛹\u200D♂", EmojiCatalog.stripVariationSelectors("⛹️\u200D♂️"))
        assertEquals("😀", EmojiCatalog.stripVariationSelectors("😀"))
    }
}
