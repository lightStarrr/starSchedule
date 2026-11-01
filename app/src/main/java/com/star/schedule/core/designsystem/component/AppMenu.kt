package com.star.schedule.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuGroupShapes
import androidx.compose.material3.MenuItemShapes
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp

/**
 * 项目里的下拉菜单封装。
 *
 * 底层还是官方的 `DropdownMenuPopup` / `DropdownMenuGroup`，但官方那套直接用在手机上会「怪」，
 * 这里把五处修正在一处做掉，两个菜单按钮共用：
 *
 * 1. **项的内边距**：普通项左右 12 / 上下 0，选中项还额外带 4dp 让边，同一张菜单里文字左边差 4dp；
 * 2. **组内项间距**：官方相邻项间距为 0，两行胶囊会连成一片；
 * 3. **分组纵向留白**：官方 2dp，比胶囊左右的 4dp 让边还小，末项看着像被切掉；
 * 4. **圆角同心**：选中胶囊用固定圆角、分组圆角按位置变，两条弧线会打架；
 * 5. **按下形变**：官方挂在 `hoverable` 上（手机收不到 hover），且只作用于外壳，内部项跟不上。
 *
 * 还有一个坑写在 [MorphingItemShape] 上：形状对象必须身份稳定，否则官方的
 * `key(shapes.shape, …)` 每帧重建节点，按压波纹会瞬间消失。
 */
@Immutable
data class MenuEntry(
    val label: String,
    val onClick: () -> Unit,
    val leadingIcon: ImageVector? = null,
    /** null 表示这是纯动作项；true/false 表示可选中项及其选中状态。 */
    val selected: Boolean? = null,
)

/** 一个菜单分组。分组与分组之间由 [AppMenu] 自动留缝。 */
@Composable
fun AppMenuGroup(
    entries: List<MenuEntry>,
    groupIndex: Int,
    groupCount: Int,
    modifier: Modifier = Modifier,
) {
    val baseShapes = MenuDefaults.groupShape(index = groupIndex, count = groupCount)
    val press = rememberPressState()
    val morph = rememberGroupMorph(
        shapes = baseShapes,
        pressed = press.pressed,
        itemCount = entries.size,
    )

    DropdownMenuGroup(
        shapes = morph.containerShapes(),
        contentPadding = MENU_GROUP_CONTENT_PADDING,
        modifier = modifier.then(press.modifier),
    ) {
        entries.forEachIndexed { index, entry ->
            val itemModifier = if (index == 0) {
                Modifier
            } else {
                Modifier.padding(top = MENU_ITEM_GAP)
            }
            val leading: (@Composable () -> Unit)? = entry.leadingIcon?.let { icon ->
                { Icon(imageVector = icon, contentDescription = null) }
            }

            if (entry.selected != null) {
                SelectableDropdownMenuItem(
                    selected = entry.selected,
                    onClick = entry.onClick,
                    text = { Text(entry.label) },
                    shapes = morph.itemShapes(index = index),
                    contentPadding = MENU_ITEM_CONTENT_PADDING,
                    leadingIcon = leading,
                    modifier = itemModifier,
                )
            } else {
                DropdownMenuItem(
                    onClick = entry.onClick,
                    text = { Text(entry.label) },
                    shape = morph.itemShapes(index = index).shape,
                    contentPadding = MENU_ITEM_CONTENT_PADDING,
                    leadingIcon = leading,
                    modifier = itemModifier,
                )
            }
        }
    }
}

/**
 * 整个菜单：外层用 `DropdownMenuPopup`（它没有自己的容器，视觉全部由分组承担），
 * 分组之间留 `GroupSpacing`。
 */
@Composable
fun AppMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    groups: List<List<MenuEntry>>,
) {
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismissRequest) {
        groups.forEachIndexed { groupIndex, entries ->
            if (groupIndex > 0) {
                Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            }
            AppMenuGroup(
                entries = entries,
                groupIndex = groupIndex,
                groupCount = groups.size,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 以下为实现细节：按压状态、圆角形变、形状缓存
// ---------------------------------------------------------------------------------------------

/**
 * 按压状态：只观察不消费触摸，所以不影响菜单项自己的点击。
 */
private class PressState(val pressed: Boolean, val modifier: Modifier)

@Composable
private fun rememberPressState(): PressState {
    var pressed by remember { mutableStateOf(false) }
    val modifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            pressed = true
            waitForUpOrCancellation(pass = PointerEventPass.Initial)
            pressed = false
        }
    }
    return PressState(pressed = pressed, modifier = modifier)
}

/** 分组四个角当前的半径，形变过程中是插值出来的中间值。 */
private data class GroupRadii(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomEnd: Dp,
    val bottomStart: Dp,
)

/**
 * 一组菜单当前的分组形状与菜单项形状。
 *
 * 形状对象在这里缓存，保证同一次组合里身份不变（见 [MorphingItemShape]）。
 */
@Stable
private class GroupMorph(
    private val radii: MutableState<GroupRadii>,
    private val itemCount: Int,
) {
    private val itemShapesCache = mutableMapOf<Int, MenuItemShapes>()

    /** 容器每帧的形状。容器没有水波纹，重建不会打断任何状态。 */
    fun containerShapes(): MenuGroupShapes {
        val shape = radii.value.toShape()
        return MenuGroupShapes(shape = shape, inactiveShape = shape)
    }

    fun itemShapes(index: Int): MenuItemShapes = itemShapesCache.getOrPut(index) {
        val shape = MorphingItemShape(radii = radii, index = index, count = itemCount)
        MenuItemShapes(shape = shape, selectedShape = shape)
    }
}

/**
 * 菜单项形状：与分组当前圆角同心，且对象身份不变。
 *
 * 官方项内部是 `key(shapes.shape, shapes.selectedShape) { rememberAnimatedShape(…) }`，
 * 如果每帧新建形状对象，这个 key 就每帧变化，内部节点连同按压波纹一起被重建——波纹刚按下就消失。
 * 半径放在 `State` 里、只在 `createOutline` 读取，变化只触发重绘，节点不重建。
 */
private class MorphingItemShape(
    private val radii: State<GroupRadii>,
    private val index: Int,
    private val count: Int,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val current = radii.value
        val isFirst = index == 0
        val isLast = index == count - 1

        fun concentric(value: Dp): Dp =
            (value - MENU_ITEM_INSET).coerceAtLeast(MENU_ITEM_SEAM_RADIUS)

        return RoundedCornerShape(
            topStart = if (isFirst) concentric(current.topStart) else MENU_ITEM_SEAM_RADIUS,
            topEnd = if (isFirst) concentric(current.topEnd) else MENU_ITEM_SEAM_RADIUS,
            bottomStart = if (isLast) concentric(current.bottomStart) else MENU_ITEM_SEAM_RADIUS,
            bottomEnd = if (isLast) concentric(current.bottomEnd) else MENU_ITEM_SEAM_RADIUS,
        ).createOutline(size, layoutDirection, density)
    }
}

private fun GroupRadii.toShape(): Shape = RoundedCornerShape(
    topStart = topStart,
    topEnd = topEnd,
    bottomEnd = bottomEnd,
    bottomStart = bottomStart,
)

/**
 * 按下时把分组圆角从 `shape` 插值到 `inactiveShape`。
 *
 * 官方 `DropdownMenuGroup` 内部也这么算，但只作用于外壳；这里把同一份半径分给容器和内部项。
 */
@Composable
private fun rememberGroupMorph(
    shapes: MenuGroupShapes,
    pressed: Boolean,
    itemCount: Int,
): GroupMorph {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current

    val active = remember(shapes, density, direction) {
        shapes.shape.cornerRadiiDp(density, direction)
    }
    val inactive = remember(shapes, density, direction) {
        shapes.inactiveShape.cornerRadiiDp(density, direction)
    }

    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "menuGroupMorph",
    )

    val radiiState = remember { mutableStateOf(active.toGroupRadii()) }
    radiiState.value = GroupRadii(
        topStart = lerp(active[0], inactive[0], progress),
        topEnd = lerp(active[1], inactive[1], progress),
        bottomEnd = lerp(active[2], inactive[2], progress),
        bottomStart = lerp(active[3], inactive[3], progress),
    )

    return remember(radiiState, itemCount) { GroupMorph(radiiState, itemCount) }
}

private fun List<Dp>.toGroupRadii(): GroupRadii =
    GroupRadii(topStart = this[0], topEnd = this[1], bottomEnd = this[2], bottomStart = this[3])

/** 从形状的 Outline 里读出四个角的半径，取不到时退回接缝圆角。 */
private fun Shape.cornerRadiiDp(density: Density, direction: LayoutDirection): List<Dp> =
    cornerRadiiPx(density, direction)
        ?.map { (it / density.density).dp }
        ?: List(4) { MENU_ITEM_SEAM_RADIUS }

/** 菜单项内容的内边距，各种项共用，保证文字左对齐。 */
private val MENU_ITEM_CONTENT_PADDING = PaddingValues(
    horizontal = 16.dp,
    vertical = 12.dp,
)

/** 分组容器的纵向内边距：取 4dp，与胶囊左右的让边一致。 */
private val MENU_GROUP_CONTENT_PADDING = PaddingValues(
    vertical = 4.dp,
)

/** 项与项之间的间隔。 */
private val MENU_ITEM_GAP = 4.dp

/** 胶囊离分组边缘的留白，与官方给选中项让边的 4dp 一致。 */
private val MENU_ITEM_INSET = 4.dp

/** 相邻两项之间的接缝圆角。 */
private val MENU_ITEM_SEAM_RADIUS = 4.dp
