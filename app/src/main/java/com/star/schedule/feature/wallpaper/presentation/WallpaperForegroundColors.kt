package com.star.schedule.feature.wallpaper.presentation

import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import com.star.schedule.core.designsystem.rememberAnimationsEnabled
import com.star.schedule.feature.wallpaper.domain.WallpaperDesign
import com.star.schedule.feature.wallpaper.domain.WallpaperLuminanceProfile
import com.star.schedule.feature.wallpaper.domain.WallpaperState
import com.star.schedule.feature.wallpaper.domain.computeLuminanceProfile
import com.star.schedule.feature.wallpaper.domain.designLuminanceProfile
import com.star.schedule.feature.wallpaper.domain.isWallpaperRegionDark
import com.star.schedule.feature.wallpaper.domain.showsScrim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class WallpaperTextColors(
    val primary: Color,
    val secondary: Color,
)

@Immutable
data class WallpaperForegroundColors(
    val topBarTitle: Color,
    val top: WallpaperTextColors,
    val leftColumn: WallpaperTextColors,
    val center: WallpaperTextColors,
)

// 照片壁纸分区亮度缓存：每条仅存 3 个 Float，进程内跨主页/预览/行高页零成本复用。
private val wallpaperLuminanceCache = LruCache<String, WallpaperLuminanceProfile>(64)

private val LightTextOnDarkWallpaper = WallpaperTextColors(
    primary = Color(0xFFF5F5F7),
    secondary = Color(0xFFDCE0E8),
)

private val DarkTextOnLightWallpaper = WallpaperTextColors(
    primary = Color(0xFF14161A),
    secondary = Color(0xFF3A3F47),
)

private const val PHOTO_SAMPLE_LONG_EDGE = 96

@Composable
fun rememberWallpaperForegroundColors(
    wallpaperState: WallpaperState,
): WallpaperForegroundColors = when (wallpaperState) {
    WallpaperState.None -> rememberWallpaperForegroundColors(
        design = null,
        photoPath = null,
        photoCacheKey = null,
        scrimEnabled = true,
    )
    is WallpaperState.Generated -> rememberWallpaperForegroundColors(
        design = wallpaperState.design,
        photoPath = null,
        photoCacheKey = null,
        scrimEnabled = wallpaperState.showsScrim,
    )
    is WallpaperState.Ready -> rememberWallpaperForegroundColors(
        design = null,
        photoPath = wallpaperState.localPath,
        photoCacheKey = wallpaperState.contentHash,
        scrimEnabled = wallpaperState.showsScrim,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberWallpaperForegroundColors(
    design: WallpaperDesign?,
    photoPath: String?,
    photoCacheKey: String?,
    scrimEnabled: Boolean,
): WallpaperForegroundColors {
    val defaultColors = defaultWallpaperForegroundColors()
    val hasWallpaper = design != null || photoPath != null
    val needsAdaptive = hasWallpaper && !scrimEnabled

    val windowSize = LocalWindowInfo.current.containerSize
    val photoLuminanceKey = remember(needsAdaptive, design, photoPath, photoCacheKey, windowSize) {
        if (needsAdaptive && design == null && photoPath != null &&
            windowSize.width > 0 && windowSize.height > 0
        ) {
            wallpaperBitmapCacheKey(
                cacheKey = photoCacheKey ?: photoPath,
                targetWidth = windowSize.width,
                targetHeight = windowSize.height,
            )
        } else {
            null
        }
    }

    val cachedPhotoProfile = remember(photoLuminanceKey) {
        photoLuminanceKey?.let(wallpaperLuminanceCache::get)
    }

    val sampledPhotoProfile by produceState<WallpaperLuminanceProfile?>(
        initialValue = cachedPhotoProfile,
        key1 = photoLuminanceKey,
        key2 = photoPath,
    ) {
        if (photoLuminanceKey == null || photoPath == null) {
            value = null
            return@produceState
        }
        wallpaperLuminanceCache.get(photoLuminanceKey)?.let {
            value = it
            return@produceState
        }
        // 优先复用已解码在内存里的主屏壁纸位图（0 磁盘 I/O，960 点跨步采样耗时 < 0.1ms）；
        // 仅在冷启动尚未解码主图时走小图区域解码并立即回收临时位图。
        val existingBitmap = peekCachedWallpaperBitmap(
            cacheKey = photoCacheKey ?: photoPath,
            targetWidth = windowSize.width,
            targetHeight = windowSize.height,
        )
        val computed = if (existingBitmap != null && !existingBitmap.isRecycled) {
            withContext(Dispatchers.Default) {
                computeLuminanceProfile(
                    width = existingBitmap.width,
                    height = existingBitmap.height,
                    pixelAt = existingBitmap::getPixel,
                )
            }
        } else {
            withContext(Dispatchers.IO) {
                val longEdge = maxOf(windowSize.width, windowSize.height).coerceAtLeast(1)
                val sampleScale = PHOTO_SAMPLE_LONG_EDGE.toFloat() / longEdge.toFloat()
                val sampleW = (windowSize.width * sampleScale).toInt().coerceAtLeast(1)
                val sampleH = (windowSize.height * sampleScale).toInt().coerceAtLeast(1)
                val smallBitmap = decodeWallpaperBitmapForSampling(photoPath, sampleW, sampleH)
                    ?: return@withContext null
                try {
                    computeLuminanceProfile(
                        width = smallBitmap.width,
                        height = smallBitmap.height,
                        pixelAt = smallBitmap::getPixel,
                    )
                } finally {
                    smallBitmap.recycle()
                }
            }
        }
        if (computed != null) {
            wallpaperLuminanceCache.put(photoLuminanceKey, computed)
            value = computed
        }
    }

    val activeProfile: WallpaperLuminanceProfile? = when {
        !needsAdaptive -> null
        design != null -> remember(design) { designLuminanceProfile(design) }
        else -> cachedPhotoProfile ?: sampledPhotoProfile
    }

    val targetTop = activeProfile?.let { colorsForLuminance(it.top) } ?: defaultColors.top
    val targetLeft = activeProfile?.let { colorsForLuminance(it.leftColumn) } ?: defaultColors.leftColumn
    val targetCenter = activeProfile?.let { colorsForLuminance(it.center) } ?: defaultColors.center
    val targetTopBarTitle = activeProfile?.let { colorsForLuminance(it.top).primary }
        ?: defaultColors.topBarTitle

    val animationsEnabled = rememberAnimationsEnabled()
    val colorSpec = if (animationsEnabled) {
        MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    } else {
        snap<Color>()
    }

    val topBarTitle by animateColorAsState(targetTopBarTitle, colorSpec, label = "wallpaperTopBarTitle")
    val topPrimary by animateColorAsState(targetTop.primary, colorSpec, label = "wallpaperTopPrimary")
    val topSecondary by animateColorAsState(targetTop.secondary, colorSpec, label = "wallpaperTopSecondary")
    val leftPrimary by animateColorAsState(targetLeft.primary, colorSpec, label = "wallpaperLeftPrimary")
    val leftSecondary by animateColorAsState(targetLeft.secondary, colorSpec, label = "wallpaperLeftSecondary")
    val centerPrimary by animateColorAsState(targetCenter.primary, colorSpec, label = "wallpaperCenterPrimary")
    val centerSecondary by animateColorAsState(targetCenter.secondary, colorSpec, label = "wallpaperCenterSecondary")

    return WallpaperForegroundColors(
        topBarTitle = topBarTitle,
        top = WallpaperTextColors(topPrimary, topSecondary),
        leftColumn = WallpaperTextColors(leftPrimary, leftSecondary),
        center = WallpaperTextColors(centerPrimary, centerSecondary),
    )
}

@Composable
private fun defaultWallpaperForegroundColors(): WallpaperForegroundColors {
    val defaultPair = WallpaperTextColors(
        primary = MaterialTheme.colorScheme.onSurface,
        secondary = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return WallpaperForegroundColors(
        topBarTitle = MaterialTheme.colorScheme.onBackground,
        top = defaultPair,
        leftColumn = defaultPair,
        center = defaultPair,
    )
}

private fun colorsForLuminance(luminance: Float): WallpaperTextColors =
    if (isWallpaperRegionDark(luminance)) {
        LightTextOnDarkWallpaper
    } else {
        DarkTextOnLightWallpaper
    }
