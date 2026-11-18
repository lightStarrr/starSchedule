package com.star.schedule.feature.emoji.data

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 一组可选表情（对应资产里的 `@<id>` 段）。 */
data class EmojiGroup(
    val id: String,
    val emojis: List<String>,
)

/**
 * 解析 assets/emoji/emoji-groups-15.1.txt：
 * `@id` 起一个新组，其余非空非注释行是表情字符串；保持文件原有顺序。
 */
internal fun parseEmojiGroups(text: String): List<EmojiGroup> {
    val groups = mutableListOf<EmojiGroup>()
    var id: String? = null
    var emojis = mutableListOf<String>()

    fun flush() {
        id?.let { groups += EmojiGroup(it, emojis.toList()) }
    }

    for (raw in text.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        if (line.startsWith("#")) continue
        if (line.startsWith("@")) {
            flush()
            id = line.substring(1)
            emojis = mutableListOf()
        } else {
            emojis += line
        }
    }
    flush()
    return groups
}

/**
 * 表情目录：读资产 → 先剥掉变体选择符（VS），再用 Noto Emoji Typeface 做
 * [Paint.hasGlyph] 过滤，字体画不出来的表情直接丢掉（避免选择器里出现点下去是
 * 空白的项）。结果按进程缓存——字体不变结果就不变。
 *
 * 为什么要剥 VS：Noto Emoji 是单色 glyf 字体、没有 cmap14 变体表；
 * Minikin 认为「码点+FE0F」这样的序列字体画不出来，于是整簇回退成系统彩色
 * 表情。剥掉 FE0F 后用字体里对应的基本字形，正好是单色线稿。
 */
object EmojiCatalog {
    const val ASSET_PATH = "emoji/emoji-groups-15.1.txt"

    /** Unicode 变体选择符 U+FE00–U+FE0F（emoji 用 FE0F/FE0E）。 */
    internal fun stripVariationSelectors(emoji: String): String =
        emoji.filterNot { it.code in 0xFE00..0xFE0F }

    private var cached: List<EmojiGroup>? = null

    suspend fun load(
        context: Context,
        typeface: Typeface,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): List<EmojiGroup> = withContext(ioDispatcher) {
        cached ?: run {
            val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            val paint = Paint().apply { this.typeface = typeface }
            parseEmojiGroups(text)
                .map { group ->
                    group.copy(
                        emojis = group.emojis
                            .map(::stripVariationSelectors)
                            .distinct()
                            .filter(paint::hasGlyph),
                    )
                }
                .filter { it.emojis.isNotEmpty() }
                .also { cached = it }
        }
    }
}
