package com.star.schedule.feature.update.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.star.schedule.BuildConfig
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.CompactPageTopBar
import com.star.schedule.core.designsystem.component.PageContentTopSpacing
import com.star.schedule.core.designsystem.navigationBarBottomInset

@Composable
fun SoftwareUpdateRoute(onBack: () -> Unit) {
    var includeSnapshots by rememberSaveable { mutableStateOf(false) }
    SoftwareUpdateScreen(
        onBack = onBack,
        onCheckForUpdates = {},
        includeSnapshots = includeSnapshots,
        onIncludeSnapshotsChange = { includeSnapshots = it },
    )
}

/**
 * 软件更新页，目前只有骨架；检查与下载的具体内容后续再填。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoftwareUpdateScreen(
    onBack: () -> Unit,
    onCheckForUpdates: () -> Unit,
    includeSnapshots: Boolean,
    onIncludeSnapshotsChange: (Boolean) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val navigationBarInset = navigationBarBottomInset()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CompactPageTopBar(
                title = stringResource(R.string.update_entry_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            ) {
                SoftwareUpdateOverflowMenu(
                    includeSnapshots = includeSnapshots,
                    onIncludeSnapshotsChange = onIncludeSnapshotsChange,
                )
            }
        },
        bottomBar = {
            Column {
                UpdateContentFade(opaqueAtTop = false)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(bottom = 16.dp + navigationBarInset),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        onClick = onCheckForUpdates,
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .heightIn(min = 56.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.update_check_for_updates),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 日志延伸到底部按钮后方，不能消费 Scaffold 的底部内边距。
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    top = PageContentTopSpacing + HERO_CARD_HEIGHT + CONTENT_FADE_HEIGHT,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
                ),
            ) {
                item { UpdateChangelogSection() }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(top = PageContentTopSpacing),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    UpdateHeroCard(
                        currentVersion = BuildConfig.VERSION_NAME,
                        modifier = Modifier.widthIn(max = 720.dp),
                    )
                }
                UpdateContentFade(opaqueAtTop = true)
            }
        }
    }
}

@Composable
private fun UpdateContentFade(opaqueAtTop: Boolean) {
    val background = MaterialTheme.colorScheme.surfaceContainer
    val transparent = background.copy(alpha = 0f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(CONTENT_FADE_HEIGHT)
            .background(
                Brush.verticalGradient(
                    colors = if (opaqueAtTop) {
                        listOf(background, transparent)
                    } else {
                        listOf(transparent, background)
                    },
                ),
            ),
    )
}

/**
 * 顶部卡片：图形拼成的背景保持不变，图标和版本号直接叠在上面。
 *
 * 图形都允许越过卡片边界，靠外层的 clip 裁掉，边缘才是切开的弧线而不是整块贴纸。
 * 左上角是当前版本，右上角是应用图标，都贴着卡片内边距。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateHeroCard(
    currentVersion: String,
    modifier: Modifier = Modifier,
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

        Icon(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = null,
            tint = colors.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(HERO_CONTENT_PADDING)
                .size(HERO_LOGO_SIZE),
        )
        Text(
            text = currentVersion,
            style = MaterialTheme.typography.headlineLargeEmphasized,
            fontWeight = FontWeight.Bold,
            color = colors.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(HERO_CONTENT_PADDING),
        )
        Text(
            text = stringResource(R.string.update_latest_version),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 10.dp),
        )
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

private val HERO_CARD_HEIGHT = 200.dp
private val HERO_CONTENT_PADDING = 20.dp
private val HERO_LOGO_SIZE = 40.dp
private val CONTENT_FADE_HEIGHT = 32.dp
