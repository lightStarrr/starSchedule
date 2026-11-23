package com.star.schedule.feature.timetable.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.SettingChevron
import com.star.schedule.core.designsystem.component.cornerRadiiPx
import com.star.schedule.feature.settings.presentation.settingsItemColors
import com.star.schedule.feature.wallpaper.presentation.WallpaperOption
import com.star.schedule.feature.wallpaper.presentation.WallpaperThumbnail
import com.star.schedule.feature.wallpaper.presentation.GeneratedWallpaperBackground
import com.star.schedule.feature.wallpaper.presentation.reconcileWallpaperOrder
import com.star.schedule.feature.wallpaper.presentation.sameAs
import com.star.schedule.feature.wallpaper.presentation.wallpaperStripLayout
import com.star.schedule.feature.wallpaper.presentation.wallpaperThumbnailCornerRadius

/**
 * 课表设置里的壁纸卡片，交互参考系统「壁纸与风格」。
 *
 * 整卡就是一个列表项（点空白处进壁纸页，按压效果与其它行一致）：
 * content 上半是一排缩略图——选中项原地展开成方形（带勾选标记），其余项
 * 保持窄幅、顺序不动；点某一格就是选用那张壁纸，不跳位置。切到另一张课表时
 * 按那张的当前壁纸重排（它排第一格并展开）。格子里换照片用淡入淡出 + 模糊交叉。
 * 下半是标题、副标题 + 右箭头。缩略图排不用 trailingContent 槽位：
 * 否则它会被右侧槽位挤窄。
 *
 * [available] 是 [实际当前] 加去重后的最近（最新在前）；[selected] 是显示为
 * 选中项的那一个，含乐观选中；[timetableId] 变化时按新课表的当前壁纸重建顺序。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WallpaperSettingsCard(
    timetableId: Long,
    available: List<WallpaperOption>,
    selected: WallpaperOption,
    onOpenWallpaper: () -> Unit,
    onSelect: (WallpaperOption) -> Unit,
    shapeIndex: Int,
    shapeCount: Int,
) {
    val shapes = ListItemDefaults.segmentedShapes(shapeIndex, shapeCount)
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    // 上边距拉到与水平相同的 16dp（列表项默认 top 是 10dp），其余沿用
    // 列表项自带值；数值从 ContentPadding 读出来，不写死。
    val contentPadding = ListItemDefaults.ContentPadding
    val cardContentPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection),
        top = contentPadding.calculateStartPadding(layoutDirection),
        end = contentPadding.calculateEndPadding(layoutDirection),
        bottom = contentPadding.calculateBottomPadding(),
    )
    // 缩略图圆角与列表项外圆角同心：外半径减缩略图离卡片边缘的距离。
    // 上下左右内边距此时一致，偏移就是 content 的 start 内边距。
    val outerRadiusDp =
        (shapes.shape.cornerRadiiPx(density, layoutDirection)?.get(0) ?: 0f) / density.density
    val thumbnailCorner = RoundedCornerShape(
        wallpaperThumbnailCornerRadius(
            outerRadiusDp,
            cardContentPadding.calculateStartPadding(layoutDirection).value,
        ).dp,
    )
    // 跨重组记上一次的显示顺序（不用 mutableState：组合期不写状态）。
    // timetableId 变化即重置，新课表的当前壁纸排第一。
    val orderRef = remember(timetableId) { arrayOf(emptyList<WallpaperOption>()) }
    SegmentedListItem(
        onClick = onOpenWallpaper,
        shapes = shapes,
        colors = settingsItemColors(),
        contentPadding = cardContentPadding,
        content = {
            Column {
                BoxWithConstraints {
                    val strip = wallpaperStripLayout(maxWidth.value)
                    val slotCount = 1 + strip.recentCount
                    val order = reconcileWallpaperOrder(
                        orderRef[0],
                        available,
                        selected,
                        slotCount,
                    )
                    SideEffect { orderRef[0] = order }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 按位置渲染：换选中只是各格宽度与内容变化，项不跳位置。
                        repeat(slotCount) { index ->
                            val option = order.getOrNull(index)
                            WallpaperStripSlot(
                                option = option,
                                isSelected = option?.sameAs(selected) == true,
                                shape = thumbnailCorner,
                                squareSize = strip.squareSize.dp,
                                collapsedWidth = strip.recentWidth.dp,
                                onSelect = { if (option != null) onSelect(option) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.timetable_settings_wallpaper))
                        // 与列表项 supportingContent 同款（BodyMedium + OnSurfaceVariant），
                        // 与标题直接相叠：列表项内部 headline/supporting 之间没有额外间距。
                        Text(
                            text = stringResource(
                                R.string.timetable_settings_wallpaper_support,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SettingChevron()
                }
            }
        },
    )
}

/**
 * 壁纸条上按位置渲染的一格：选中态展开为 [squareSize] 的正方形，否则收窄到
 * [collapsedWidth]；宽度动画与反向收窄共用同一个 spatial 规格，整排总宽守恒。
 * 有内容的格消费点击（选中格点了无动作）；空槽透明且不可点。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WallpaperStripSlot(
    option: WallpaperOption?,
    isSelected: Boolean,
    shape: Shape,
    squareSize: Dp,
    collapsedWidth: Dp,
    onSelect: () -> Unit,
) {
    val widthState = animateDpAsState(
        targetValue = if (isSelected) squareSize else collapsedWidth,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "wallpaperSlotWidth",
    )
    // 内容换图时淡入淡出 + 模糊交叉；transitionSpec 不是 @Composable 作用域，
    // motionScheme 的规格要提前读出来。
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val checkSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    Box(
        Modifier
            .layout { measurable, constraints ->
                val targetWidthPx = widthState.value.roundToPx().coerceAtLeast(0)
                val targetHeightPx = squareSize.roundToPx().coerceAtLeast(0)
                val placeable = measurable.measure(
                    Constraints.fixed(targetWidthPx, targetHeightPx),
                )
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, 0)
                }
            }
            // clip 必须在 clickable 之前，波纹才贴圆角。
            .then(
                if (option != null) {
                    Modifier
                        .clip(shape)
                        .clickable { if (!isSelected) onSelect() }
                } else {
                    Modifier
                },
            ),
    ) {
        AnimatedContent(
            targetState = option,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(effectsSpec) togetherWith fadeOut(effectsSpec) using null
            },
            // 同一项不因路径变化（选用后存进课表目录）而重播切换动画。
            contentKey = { current ->
                when (current) {
                    null -> "empty"
                    WallpaperOption.Default -> "default"
                    is WallpaperOption.Generated -> current.design
                    is WallpaperOption.Image -> current.contentHash
                }
            },
            label = "wallpaperSlotContent",
        ) { current ->
            val contentBlur by transition.animateDp(label = "wallpaperSlotBlur") {
                if (it == EnterExitState.Visible) 0.dp else 16.dp
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (contentBlur > 0.dp) {
                            Modifier.blur(contentBlur, BlurredEdgeTreatment.Rectangle)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                if (current != null) {
                    WallpaperOptionTile(
                        option = current,
                        shape = shape,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = isSelected,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(effectsSpec) + scaleIn(checkSpec),
            exit = fadeOut(effectsSpec) + scaleOut(checkSpec),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(
                        if (option == WallpaperOption.Default) {
                            R.string.wallpaper_default_description
                        } else {
                            R.string.wallpaper_current_description
                        },
                    ),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * 单个壁纸槽位的内容：「默认」画主页无壁纸时实际铺的
 * [MaterialTheme.colorScheme.surfaceContainer] 底色（色块即默认背景本身），
 * 图片项解码前垫一层 surfaceContainerHighest。
 */
@Composable
private fun WallpaperOptionTile(
    option: WallpaperOption,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(shape)
            .background(
                when (option) {
                    WallpaperOption.Default -> MaterialTheme.colorScheme.surfaceContainer
                    is WallpaperOption.Generated -> MaterialTheme.colorScheme.surfaceContainerHighest
                    is WallpaperOption.Image -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
            ),
    ) {
        if (option is WallpaperOption.Image) {
            WallpaperThumbnail(
                path = option.path,
                cacheKey = option.contentHash,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (option is WallpaperOption.Generated) {
            GeneratedWallpaperBackground(
                design = option.design,
                previewViewport = LocalWindowInfo.current.containerSize,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
