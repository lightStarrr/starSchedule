package com.star.schedule.feature.emoji.presentation

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.star.schedule.R
import com.star.schedule.core.designsystem.theme.StarScheduleTheme
import com.star.schedule.feature.emoji.data.EmojiTypefaceLoader
import com.star.schedule.feature.emoji.domain.EMOJI_LAYOUT_SEED
import com.star.schedule.feature.emoji.domain.EMOJI_SCALE_INTERVALS
import com.star.schedule.feature.emoji.domain.EmojiPattern
import com.star.schedule.feature.emoji.domain.EmojiWallpaperColors
import com.star.schedule.feature.emoji.domain.EmojiWallpaperSpec
import com.star.schedule.feature.emoji.domain.emojiWallpaperLayout
import com.star.schedule.feature.emoji.domain.quantizedEmojiScale
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 在真实 Android 字体/Canvas 上输出完整档位，供同机官方截图逐档比对。 */
class EmojiWallpaperRenderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun patternControlsVisitEveryStopAndKeepTheSelectedPattern() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val font = File(context.filesDir, "optional-resources/noto-emoji/NotoEmoji-3.002.ttf")
        val state = mutableStateOf(EmojiWallpaperUiState.Ready(
            emojis = listOf("🐢", "✌"),
            pattern = EmojiPattern.STACK,
            scale = 0f,
            paletteIndex = 0,
            palettes = listOf(EmojiWallpaperColors(0xffc5e9c8.toInt(), 0xff448b50.toInt(), 0xff448b50.toInt())),
            seed = EMOJI_LAYOUT_SEED,
            groups = emptyList(),
            typeface = EmojiTypefaceLoader.load(font),
            pickerOpen = false,
            pickerGroupId = EMOJI_GROUP_RECENT,
            recents = emptyList(),
        ))
        compose.setContent {
            StarScheduleTheme {
                PatternTabContent(
                    state.value,
                    onSelectPattern = { state.value = state.value.copy(pattern = it) },
                    onScaleChange = { state.value = state.value.copy(scale = quantizedEmojiScale(it)) },
                )
            }
        }
        val increase = context.getString(R.string.emoji_wallpaper_scale_increase)
        val decrease = context.getString(R.string.emoji_wallpaper_scale_decrease)
        compose.onNodeWithContentDescription(decrease).assertIsNotEnabled()
        for (step in 1..24) {
            compose.onNodeWithContentDescription(increase).performClick()
            compose.runOnIdle { assertEquals(step / 24f, state.value.scale, .0001f) }
        }
        compose.onNodeWithContentDescription(increase).assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.emoji_pattern_prism))
            .performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(EmojiPattern.PRISM, state.value.pattern)
            assertEquals(1f, state.value.scale, .0001f)
        }
        for (step in 23 downTo 0) {
            compose.onNodeWithContentDescription(decrease).performClick()
            compose.runOnIdle { assertEquals(step / 24f, state.value.scale, .0001f) }
        }
        compose.onNodeWithContentDescription(decrease).assertIsNotEnabled()
    }

    @Test
    fun renderEveryPatternAndStep() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val font = File(context.filesDir, "optional-resources/noto-emoji/NotoEmoji-3.002.ttf")
        assertTrue("请先在数据源页安装 Noto Emoji 字体", font.isFile)
        val typeface = EmojiTypefaceLoader.load(font)
        val output = File(context.filesDir, "emoji-render-verification").apply { mkdirs() }
        for (pattern in EmojiPattern.entries) {
            for (step in 0..EMOJI_SCALE_INTERVALS) {
                val spec = EmojiWallpaperSpec(
                    emojis = listOf("🐢", "✌"),
                    pattern = pattern,
                    scale = step.toFloat() / EMOJI_SCALE_INTERVALS,
                    colors = EmojiWallpaperColors(0xff000000.toInt(), 0xffffffff.toInt(), 0xff42864e.toInt()),
                    seed = EMOJI_LAYOUT_SEED,
                )
                val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
                try {
                    val layout = emojiWallpaperLayout(pattern, spec.emojis.size, spec.scale, spec.seed, 1080f, 2400f)
                    drawEmojiWallpaper(Canvas(bitmap), 1080, 2400, spec, layout, typeface)
                    File(output, "${pattern.name.lowercase()}-${step.toString().padStart(2, '0')}.png")
                        .outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally {
                    bitmap.recycle()
                }
            }
        }
        assertTrue(output.listFiles().orEmpty().size == EmojiPattern.entries.size * (EMOJI_SCALE_INTERVALS + 1))
    }
}
