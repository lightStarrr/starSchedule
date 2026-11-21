package com.star.schedule.feature.emoji.presentation

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.star.schedule.core.database.AppDatabase
import com.star.schedule.feature.emoji.data.EmojiTypefaceLoader
import com.star.schedule.feature.emoji.domain.EmojiPattern
import com.star.schedule.feature.emoji.domain.EmojiWallpaperColors
import com.star.schedule.feature.emoji.domain.EmojiWallpaperSpec
import com.star.schedule.feature.emoji.domain.emojiWallpaperLayout
import com.star.schedule.feature.wallpaper.data.RoomWallpaperRepository
import com.star.schedule.feature.wallpaper.domain.RecentWallpaper
import com.star.schedule.feature.wallpaper.domain.WallpaperDesign
import com.star.schedule.feature.wallpaper.domain.WallpaperState
import com.star.schedule.feature.wallpaper.presentation.WallpaperBackground
import com.star.schedule.feature.wallpaper.presentation.GeneratedWallpaperBackground
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GeneratedWallpaperTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val design = WallpaperDesign(
        emoji = EmojiWallpaperSpec(
            listOf("🐢", "✌"), EmojiPattern.STACK, .5f,
            EmojiWallpaperColors(0xffc5e9c8.toInt(), 0xff448b50.toInt(), 0xff448b50.toInt()), 10,
        ),
    )

    @Test
    fun repositoryRestoresParametersAndReturnsToDefaultWithoutCreatingImages() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        // 偏好没有课表外键；使用独立 id 和内存库，避免修改用户课表。
        val id = Long.MAX_VALUE
        val folder = File(context.filesDir, "wallpapers/$id")
        try {
            var repository = RoomWallpaperRepository(context, database.preferenceDao())
            repository.applyDesign(id, design).getOrThrow()
            repository = RoomWallpaperRepository(context, database.preferenceDao())
            assertEquals(WallpaperState.Generated(design), repository.observe(id).first())
            assertTrue(repository.observeRecents().first().contains(RecentWallpaper.Generated(design)))
            val solid = WallpaperDesign(solidArgb = 0xff123456.toInt())
            repository.applyDesign(id, solid).getOrThrow()
            assertEquals(WallpaperState.Generated(solid), repository.observe(id).first())
            repository.applyRecent(id, RecentWallpaper.Generated(design)).getOrThrow()
            assertEquals(WallpaperState.Generated(design), repository.observe(id).first())
            repository.applyRecent(id, RecentWallpaper.Default).getOrThrow()
            assertEquals(WallpaperState.None, repository.observe(id).first())
            assertTrue(folder.listFiles().isNullOrEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun squareThumbnailShowsThePortraitLayoutInsteadOfRegeneratingSquareRows() {
        val font = File(context.filesDir, "optional-resources/noto-emoji/NotoEmoji-3.002.ttf")
        assertTrue("请先安装表情字体", font.isFile)
        val typeface = EmojiTypefaceLoader.load(font)
        compose.setContent {
            val density = LocalDensity.current
            Box(Modifier.requiredSize(with(density) { 300.toDp() }).testTag("thumbnail")) {
                GeneratedWallpaperBackground(design, previewViewport = IntSize(300, 500))
            }
        }
        val spec = design.emoji!!
        val portrait = Bitmap.createBitmap(300, 500, Bitmap.Config.ARGB_8888)
        try {
            drawEmojiWallpaper(
                Canvas(portrait), 300, 500, spec,
                emojiWallpaperLayout(spec.pattern, 2, spec.scale, spec.seed, 300f, 500f),
                typeface,
            )
            compose.waitUntil(5_000) {
                val actual = compose.onNodeWithTag("thumbnail").captureToImage().asAndroidBitmap()
                var differences = 0
                for (y in 0 until 300 step 3) for (x in 0 until 300 step 3) {
                    if (kotlin.math.abs(
                        android.graphics.Color.red(actual.getPixel(x, y)) -
                            android.graphics.Color.red(portrait.getPixel(x, y + 100)),
                    ) > 32) differences++
                }
                differences < 300 * 300 / 9 * .03
            }
        } finally {
            portrait.recycle()
        }
    }

    @Test
    fun canvasResizeRecomputesTheSameLayoutAsDirectRendering() {
        val font = File(context.filesDir, "optional-resources/noto-emoji/NotoEmoji-3.002.ttf")
        assertTrue("请先安装表情字体", font.isFile)
        val typeface = EmojiTypefaceLoader.load(font)
        val size = mutableStateOf(300 to 500)
        compose.setContent {
            val density = LocalDensity.current
            Box(Modifier.requiredSize(
                with(density) { size.value.first.toDp() },
                with(density) { size.value.second.toDp() },
            ).testTag("wallpaper")) {
                WallpaperBackground(WallpaperState.Generated(design))
            }
        }
        for ((width, height) in listOf(300 to 500, 500 to 300, 300 to 500)) {
            compose.runOnIdle { size.value = width to height }
            val spec = design.emoji!!
            val expected = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            drawEmojiWallpaper(
                Canvas(expected), width, height, spec,
                emojiWallpaperLayout(spec.pattern, 2, spec.scale, spec.seed, width.toFloat(), height.toFloat()),
                typeface,
            )
            compose.waitUntil(5_000) {
                val actual = compose.onNodeWithTag("wallpaper").captureToImage().asAndroidBitmap()
                // Canvas 硬件/软件绘制边缘可有色差；背景与字形主体应覆盖相同位置。
                var differences = 0
                for (y in 0 until height step 3) for (x in 0 until width step 3) {
                    if (kotlin.math.abs(
                        android.graphics.Color.red(actual.getPixel(x, y)) -
                            android.graphics.Color.red(expected.getPixel(x, y)),
                    ) > 32) differences++
                }
                differences < width * height / 9 * .03
            }
            val output = File(context.filesDir, "wallpaper-size-verification").apply { mkdirs() }
            File(output, "$width-$height.png").outputStream().use { stream ->
                compose.onNodeWithTag("wallpaper").captureToImage().asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            expected.recycle()
        }
    }
}
