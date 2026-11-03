package com.star.schedule.feature.update.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.PageContentTopSpacing
import com.star.schedule.core.designsystem.component.PageTopBar
import com.star.schedule.core.designsystem.navigationBarBottomInset

@Composable
fun SoftwareUpdateRoute(onBack: () -> Unit) {
    SoftwareUpdateScreen(onBack = onBack)
}

/**
 * 软件更新页，目前只有骨架；检查与下载的具体内容后续再填。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoftwareUpdateScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navigationBarInset = navigationBarBottomInset()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageTopBar(
                title = stringResource(R.string.update_entry_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    top = PageContentTopSpacing,
                    bottom = 24.dp + navigationBarInset,
                ),
            ) {
                item { UpdateHeroCard() }
            }
        }
    }
}

/**
 * 顶部装饰卡片：用 M3E 的图形在主题容器色上拼出背景。
 *
 * 每个图形都允许越过卡片边界，靠外层的 clip 裁掉；四角都留一点"切到一半"的形状，
 * 边界才不会像一排整齐贴上的贴纸。形状同样只叠在半透明色上，不抢前景内容。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateHeroCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(HERO_CARD_HEIGHT)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(colors.primaryContainer),
    ) {
        // 构图不对称：右侧放一个大形状占住视觉重心，左侧只留两个小块，
        // 中间偏左留白，之后要叠标题或按钮才有位置。颜色都取同一色相的深浅，
        // 只有左下的小形状用第三色提一下，避免整块背景发灰。
        HeroShape(
            shape = MaterialShapes.SoftBurst.toShape(),
            size = 236.dp,
            align = Alignment.CenterEnd,
            offsetX = 96.dp,
            offsetY = (-26).dp,
            rotation = 12f,
            color = colors.primary.copy(alpha = 0.16f),
        )
        HeroShape(
            shape = MaterialShapes.Cookie4Sided.toShape(),
            size = 128.dp,
            align = Alignment.TopStart,
            offsetX = (-44).dp,
            offsetY = (-36).dp,
            rotation = -14f,
            color = colors.primary.copy(alpha = 0.22f),
        )
        HeroShape(
            shape = MaterialShapes.Sunny.toShape(),
            size = 62.dp,
            align = Alignment.BottomStart,
            offsetX = 22.dp,
            offsetY = 24.dp,
            rotation = -18f,
            color = colors.tertiary.copy(alpha = 0.42f),
        )
        HeroShape(
            shape = MaterialShapes.Circle.toShape(),
            size = 26.dp,
            align = Alignment.CenterStart,
            offsetX = 92.dp,
            offsetY = 18.dp,
            rotation = 0f,
            color = colors.primary.copy(alpha = 0.30f),
        )
        content()
    }
}

@Composable
private fun BoxScope.HeroShape(
    shape: Shape,
    size: Dp,
    color: Color,
    align: Alignment,
    offsetX: Dp,
    offsetY: Dp,
    rotation: Float,
) {
    Box(
        modifier = Modifier
            .align(align)
            .offset(x = offsetX, y = offsetY)
            .size(size)
            .rotate(rotation)
            .clip(shape)
            .background(color),
    )
}

private val HERO_CARD_HEIGHT = 168.dp
