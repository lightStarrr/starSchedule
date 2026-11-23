package com.star.schedule.feature.wallpaper.presentation

import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import com.star.schedule.core.designsystem.rememberAnimationsEnabled
import com.star.schedule.core.designsystem.theme.seedColorScheme
import com.star.schedule.feature.wallpaper.domain.WallpaperDesign
import com.star.schedule.feature.wallpaper.domain.WallpaperLuminanceProfile
import com.star.schedule.feature.wallpaper.domain.WallpaperState
import com.star.schedule.feature.wallpaper.domain.computeLuminanceProfile
import com.star.schedule.feature.wallpaper.domain.designLuminanceProfile
import com.star.schedule.feature.wallpaper.domain.isWallpaperRegionDark
import com.star.schedule.feature.wallpaper.domain.seedArgb
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

private const val PHOTO_SAMPLE_LONG_EDGE = 96

@Composable
fun rememberWallpaperForegroundColors(
    wallpaperState: WallpaperState,
): WallpaperForegroundColors = when (wallpaperState) {
    WallpaperState.None -> rememberWallpaperForegroundColors(
        design = null,
        photoPath = null,
        photoCacheKey = null,
        seedArgb = null,
        scrimEnabled = true,
    )
    is WallpaperState.Generated -> rememberWallpaperForegroundColors(
        design = wallpaperState.design,
        photoPath = null,
        photoCacheKey = null,
        seedArgb = wallpaperState.seedArgb,
        scrimEnabled = wallpaperState.showsScrim,
    )
    is WallpaperState.Ready -> rememberWallpaperForegroundColors(
        design = null,
        photoPath = wallpaperState.localPath,
        photoCacheKey = wallpaperState.contentHash,
        seedArgb = wallpaperState.seedArgb,
        scrimEnabled = wallpaperState.showsScrim,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberWallpaperForegroundColors(
    design: WallpaperDesign?,
    photoPath: String?,
    photoCacheKey: String?,
    seedArgb: Int?,
    scrimEnabled: Boolean,
): WallpaperForegroundColors {
    val currentScheme = MaterialTheme.colorScheme
    val defaultColors = defaultWallpaperForegroundColors(currentScheme)
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

    // 用同一套壁纸种子色对应的明/暗 Material 3 ColorScheme（onSurface / onSurfaceVariant / onBackground），
    // 让无遮罩时的浅色/深色字自带主题色调，且在背景与系统主题同明暗时与常规主题字色完全一致。
    val context = LocalContext.current
    val isCurrentSchemeDark = currentScheme.surface.luminance() < 0.5f
    val needsDarkScheme = activeProfile != null && (
        isWallpaperRegionDark(activeProfile.top) ||
            isWallpaperRegionDark(activeProfile.leftColumn) ||
            isWallpaperRegionDark(activeProfile.center)
        )
    val needsLightScheme = activeProfile != null && (
        !isWallpaperRegionDark(activeProfile.top) ||
            !isWallpaperRegionDark(activeProfile.leftColumn) ||
            !isWallpaperRegionDark(activeProfile.center)
        )
    val resolvedSeedArgb = seedArgb ?: design?.seedArgb

    val darkTextScheme: ColorScheme = remember(
        needsDarkScheme,
        isCurrentSchemeDark,
        currentScheme,
        resolvedSeedArgb,
        context,
    ) {
        when {
            !needsDarkScheme || isCurrentSchemeDark -> currentScheme
            resolvedSeedArgb != null -> seedColorScheme(resolvedSeedArgb, isDark = true)
            else -> runCatching { dynamicDarkColorScheme(context) }.getOrElse { darkColorScheme() }
        }
    }
    val lightTextScheme: ColorScheme = remember(
        needsLightScheme,
        isCurrentSchemeDark,
        currentScheme,
        resolvedSeedArgb,
        context,
    ) {
        when {
            !needsLightScheme || !isCurrentSchemeDark -> currentScheme
            resolvedSeedArgb != null -> seedColorScheme(resolvedSeedArgb, isDark = false)
            else -> runCatching { dynamicLightColorScheme(context) }
                .getOrElse { expressiveLightColorScheme() }
        }
    }

    fun schemeFor(luminance: Float): ColorScheme =
        if (isWallpaperRegionDark(luminance)) darkTextScheme else lightTextScheme

    val targetTop = activeProfile?.let { textColorsFrom(schemeFor(it.top)) } ?: defaultColors.top
    val targetLeft = activeProfile?.let { textColorsFrom(schemeFor(it.leftColumn)) }
        ?: defaultColors.leftColumn
    val targetCenter = activeProfile?.let { textColorsFrom(schemeFor(it.center)) }
        ?: defaultColors.center
    val targetTopBarTitle = activeProfile?.let { schemeFor(it.top).onBackground }
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

private fun defaultWallpaperForegroundColors(scheme: ColorScheme): WallpaperForegroundColors {
    val defaultPair = textColorsFrom(scheme)
    return WallpaperForegroundColors(
        topBarTitle = scheme.onBackground,
        top = defaultPair,
        leftColumn = defaultPair,
        center = defaultPair,
    )
}

private fun textColorsFrom(scheme: ColorScheme): WallpaperTextColors =
    WallpaperTextColors(
        primary = scheme.onSurface,
        secondary = scheme.onSurfaceVariant,
    )
