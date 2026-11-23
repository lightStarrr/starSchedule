package com.star.schedule.feature.wallpaper.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import com.star.schedule.feature.wallpaper.presentation.WallpaperPreviewViewModel
import kotlin.math.roundToInt

private val PillHeight = 56.dp
private val IslandCornerRadius = 28.dp
// 胶囊圆角给足一个超过半高的值，裁剪时自然就是全圆角；
// 展开时动画回落到 28dp，变形是连续看得见的。
private val PillCornerRadius = 64.dp

/**
 * 预览页底部的「特效」岛：收起是一颗 tonal 胶囊，展开变形成卡片。
 *
 * 变形就是同一个 [Surface] 的圆角动画 + [AnimatedContent] 的尺寸/交叉淡入，
 * 不需要 SharedTransition；动画规格走 [MaterialTheme.motionScheme]（尺寸用 spatial、
 * 颜色与淡入淡出用 effects）。展开时岛外一层透明点击区 + 系统返回键都先收起岛。
 * 组件本身无状态，展开与否、模糊参数都由调用方持有。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WallpaperEffectsIsland(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    scrimEnabled: Boolean,
    onScrimEnabledChange: (Boolean) -> Unit,
    blurEnabled: Boolean,
    onBlurEnabledChange: (Boolean) -> Unit,
    blurRadius: Float,
    onBlurRadiusChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = expanded) { onExpandedChange(false) }

    val corner by animateDpAsState(
        targetValue = if (expanded) IslandCornerRadius else PillCornerRadius,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "wallpaperEffectsCorner",
    )
    // transitionSpec 不是 @Composable 作用域，motionScheme 的规格要提前读出来。
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val sizeSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    val containerColor by animateColorAsState(
        targetValue = if (expanded) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "wallpaperEffectsContainer",
    )

    Box(modifier.fillMaxSize()) {
        if (expanded) {
            // 透明点击区：点岛外任意处先收起，不会误触下面的课表。
            Spacer(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onExpandedChange(false) },
            )
        }
        Surface(
            onClick = { onExpandedChange(true) },
            enabled = !expanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
            shape = RoundedCornerShape(corner),
            color = containerColor,
            contentColor = if (expanded) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
            shadowElevation = 3.dp,
        ) {
            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec) using
                        SizeTransform { _, _ -> sizeSpec }
                },
                label = "wallpaperEffectsContent",
            ) { isExpanded ->
                if (isExpanded) {
                    EffectsIslandContent(
                        scrimEnabled = scrimEnabled,
                        onScrimEnabledChange = onScrimEnabledChange,
                        blurEnabled = blurEnabled,
                        onBlurEnabledChange = onBlurEnabledChange,
                        blurRadius = blurRadius,
                        onBlurRadiusChange = onBlurRadiusChange,
                        onCollapse = { onExpandedChange(false) },
                    )
                } else {
                    EffectsPillContent()
                }
            }
        }
    }
}

@Composable
private fun EffectsPillContent() {
    Row(
        modifier = Modifier
            .height(PillHeight)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.wallpaper_effects),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EffectsIslandContent(
    scrimEnabled: Boolean,
    onScrimEnabledChange: (Boolean) -> Unit,
    blurEnabled: Boolean,
    onBlurEnabledChange: (Boolean) -> Unit,
    blurRadius: Float,
    onBlurRadiusChange: (Float) -> Unit,
    onCollapse: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.wallpaper_effects),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onCollapse) {
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = stringResource(R.string.wallpaper_effects_collapse),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.wallpaper_scrim),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.weight(1f))
            Switch(
                checked = scrimEnabled,
                onCheckedChange = onScrimEnabledChange,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.wallpaper_blur),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.weight(1f))
            Switch(
                checked = blurEnabled,
                onCheckedChange = onBlurEnabledChange,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.wallpaper_blur_amount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(
                    R.string.wallpaper_blur_amount_value,
                    blurRadius.roundToInt(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = blurRadius,
            onValueChange = onBlurRadiusChange,
            valueRange = WallpaperPreviewViewModel.BLUR_RADIUS_MIN..
                WallpaperPreviewViewModel.BLUR_RADIUS_MAX,
            enabled = blurEnabled,
        )
    }
}
