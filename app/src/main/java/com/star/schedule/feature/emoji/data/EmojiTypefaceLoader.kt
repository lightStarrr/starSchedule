package com.star.schedule.feature.emoji.data

import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import java.io.File

/**
 * Noto Emoji 字体加载：同一文件只建一个 Typeface（进程内缓存）。
 *
 * 用 [Typeface.CustomFallbackBuilder] 把这个文件放在回退链最前；
 * 注意 Android 并没有公开 API 关掉默认系统回退（不调用
 * `setSystemFallback` 时默认就是 Typeface.DEFAULT），所以
 * 「字体画不出来的序列不回退成系统彩色表情」要靠
 * [EmojiCatalog] 一侧剥掉变体选择符来保证。
 */
object EmojiTypefaceLoader {

    /** 绘制时的可变字重：画布绘制只认 fontVariationSettings。 */
    const val ARG_WEIGHT: String = "'wght' 700"

    private val cache = HashMap<String, Typeface>()

    @Synchronized
    fun load(file: File): Typeface = cache.getOrPut(file.absolutePath) {
        Typeface.CustomFallbackBuilder(
            FontFamily.Builder(Font.Builder(file).setFontVariationSettings(ARG_WEIGHT).setWeight(700).build()).build(),
        ).build()
    }
}
