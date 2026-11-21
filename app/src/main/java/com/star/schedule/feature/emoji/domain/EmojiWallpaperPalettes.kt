@file:SuppressLint("RestrictedApi")

package com.star.schedule.feature.emoji.domain

import android.annotation.SuppressLint
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeTonalSpot

/**
 * 表情壁纸的一组配色：背景色 + 表情色 + 生成它的种子。
 * [seedArgb] 同时用作编辑页界面主题的种子，UI 跟所选配色变。
 */
@kotlinx.serialization.Serializable
data class EmojiWallpaperColors(
    val backgroundArgb: Int,
    val emojiArgb: Int,
    val seedArgb: Int,
)

/**
 * 配色列表：种子 = [systemSeedArgb]（系统动态色主色，可空）+ 10 个固定色相。
 * 每个种子出两套（均取自浅色 TonalSpot）：
 * - 色调款：浅底深字（primary tone 90 / tone 70）
 * - 浓郁款：深底浅字（primary tone 40 / tertiary tone 85）
 * 返回顺序按种子两两排布：[色调0, 浓郁0, 色调1, 浓郁1, …]，
 * 正好对上两行横向滚动网格的列序——第一行全是色调款、第二行全是浓郁款。
 */
fun emojiWallpaperPalettes(systemSeedArgb: Int? = null): List<EmojiWallpaperColors> {
    val seeds = buildList {
        systemSeedArgb?.let(::add)
        repeat(10) { add(Hct.from(it * 36.0, 48.0, 50.0).toInt()) }
    }
    return seeds.flatMap { seed ->
        val scheme = SchemeTonalSpot(Hct.fromInt(seed), false, 0.0)
        listOf(
            EmojiWallpaperColors(
                backgroundArgb = scheme.primaryPalette.tone(90),
                emojiArgb = scheme.primaryPalette.tone(70),
                seedArgb = seed,
            ),
            EmojiWallpaperColors(
                backgroundArgb = scheme.primaryPalette.tone(40),
                emojiArgb = scheme.tertiaryPalette.tone(85),
                seedArgb = seed,
            ),
        )
    }
}
