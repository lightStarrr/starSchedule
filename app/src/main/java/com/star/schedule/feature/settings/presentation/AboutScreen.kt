package com.star.schedule.feature.settings.presentation

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.star.schedule.BuildConfig
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.PageContentTopSpacing
import com.star.schedule.core.designsystem.component.PageTopBar
import com.star.schedule.core.designsystem.component.rememberHorizontalSegmentedShape
import com.star.schedule.core.designsystem.navigationBarBottomInset

@Composable
fun AboutRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    AboutScreen(
        onBack = onBack,
        onOpenLink = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
    )
}

/**
 * 关于页：设置页那边只留版本号，完整信息与联系方式放这里。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AboutScreen(
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navigationBarInset = navigationBarBottomInset()
    // 版本与构建信息分两行：排查时常常只关心其中一个，混在一行里每次都得自己找。
    val versionText = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    val buildText = "${BuildConfig.GIT_SHA} · ${BuildConfig.BUILD_TIME}"
    val githubUrl = stringResource(R.string.github_url)
    val qqGroupUrl = stringResource(R.string.qq_group_url)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageTopBar(
                title = stringResource(R.string.about_app_title),
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
                item {
                    SettingsSectionTitle(
                        resId = R.string.about_section_info,
                        topPadding = 0.dp,
                    )
                }
                item {
                    SettingsGroup {
                        SettingsListItem(
                            title = stringResource(R.string.about_version_title),
                            supporting = versionText,
                            shapeIndex = 0,
                            shapeCount = 2,
                            leading = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        )
                        SettingsListItem(
                            title = stringResource(R.string.about_build_title),
                            supporting = buildText,
                            shapeIndex = 1,
                            shapeCount = 2,
                            leading = { Icon(Icons.Rounded.Build, contentDescription = null) },
                        )
                    }
                }

                item { SettingsSectionTitle(R.string.about_section_sponsor) }
                item {
                    // 左右两块各占一半，外侧大圆角、相邻处小圆角，跟列表卡片同一套形状
                    val leftShape = rememberHorizontalSegmentedShape(index = 0, count = 2)
                    val rightShape = rememberHorizontalSegmentedShape(index = 1, count = 2)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        SponsorQr(
                            resId = R.drawable.sponsor_wechat,
                            contentDescription = stringResource(R.string.about_sponsor_wechat),
                            shape = leftShape,
                            modifier = Modifier.weight(1f),
                        )
                        SponsorQr(
                            resId = R.drawable.sponsor_alipay,
                            contentDescription = stringResource(R.string.about_sponsor_alipay),
                            shape = rightShape,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item { SettingsSectionTitle(R.string.about_section_contact) }
                item {
                    SettingsGroup {
                        SettingsActionItem(
                            title = stringResource(R.string.github_title),
                            supporting = githubUrl,
                            onClick = { onOpenLink(githubUrl) },
                            shapeIndex = 0,
                            shapeCount = 2,
                            leading = { Icon(Icons.Rounded.Code, contentDescription = null) },
                        )
                        SettingsActionItem(
                            title = stringResource(R.string.qq_group_title),
                            supporting = "947574953",
                            onClick = { onOpenLink(qqGroupUrl) },
                            shapeIndex = 1,
                            shapeCount = 2,
                            leading = { Icon(Icons.Rounded.Group, contentDescription = null) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 收款码等比缩放到固定高度并在自己那一半里居中。
 *
 * 两张图原始比例不同（0.735 / 0.667），用 [ContentScale.Fit] 而不是拉伸，
 * 否则二维码会变形扫不出来。
 */
@Composable
private fun SponsorQr(
    @DrawableRes resId: Int,
    contentDescription: String,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(vertical = 20.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(resId),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxWidth()
                .height(SPONSOR_QR_HEIGHT),
            contentScale = ContentScale.Fit,
        )
    }
}

private val SPONSOR_QR_HEIGHT = 220.dp
