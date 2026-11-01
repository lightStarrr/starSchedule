package com.star.schedule.feature.settings.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.NumberFormat
import com.star.schedule.R
import com.star.schedule.core.common.Constants
import com.star.schedule.core.designsystem.component.AppMenu
import com.star.schedule.core.designsystem.component.MenuEntry
import com.star.schedule.core.designsystem.component.PageContentTopSpacing
import com.star.schedule.core.designsystem.component.PageTopBar
import com.star.schedule.core.designsystem.navigationBarBottomInset

@Composable
fun AiSettingsRoute(
    viewModel: AiSettingsViewModel,
    onBack: () -> Unit,
    onNewProvider: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    AiSettingsScreen(
        state = state,
        usage = usage,
        onBack = onBack,
        onSelectProvider = viewModel::selectProvider,
        onNewProvider = onNewProvider,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AiSettingsScreen(
    state: AiSettingsUiState,
    usage: AiUsageUiState,
    onBack: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onNewProvider: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navigationBarInset = navigationBarBottomInset()
    var menuExpanded by remember { mutableStateOf(false) }
    // 进入页面时 usage 会从 Hidden 变成 Loading，这时不该播展开动画——只有用户自己切换服务商才播
    var userSwitchedProvider by remember { mutableStateOf(false) }

    val selectedName = when (state.effectiveProviderId) {
        Constants.AI_PROVIDER_CLOUD -> stringResource(R.string.ai_provider_cloud)
        else -> stringResource(R.string.ai_provider_none_hint)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageTopBar(
                title = stringResource(R.string.ai_settings_title),
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
                        resId = R.string.ai_section_provider,
                        topPadding = 0.dp,
                    )
                }
                item {
                    val quotaVisible = usage != AiUsageUiState.Hidden
                    SettingsGroup {
                        SettingsListItem(
                            title = stringResource(R.string.ai_provider_row_title),
                            supporting = selectedName,
                            onClick = { menuExpanded = true },
                            // 额度行出现时本行变成组内首项，圆角跟着换
                            shapeIndex = 0,
                            shapeCount = if (quotaVisible) 2 else 1,
                            leading = { Icon(Icons.Rounded.Cloud, contentDescription = null) },
                            trailing = {
                                // 菜单以这个图标为锚点展开，所以触发按钮与菜单必须同一层
                                Box {
                                    IconButton(onClick = { menuExpanded = true }) {
                                        Icon(
                                            imageVector = Icons.Rounded.UnfoldMore,
                                            contentDescription = stringResource(
                                                R.string.ai_provider_menu,
                                            ),
                                        )
                                    }
                                    ProviderMenu(
                                        expanded = menuExpanded,
                                        cloudEnabled = state.cloudEnabled,
                                        selectedProviderId = state.effectiveProviderId,
                                        onDismiss = { menuExpanded = false },
                                        onSelectProvider = { providerId ->
                                            userSwitchedProvider = true
                                            onSelectProvider(providerId)
                                        },
                                        onNewProvider = onNewProvider,
                                    )
                                }
                            },
                        )
                        AnimatedVisibility(
                            visible = quotaVisible,
                            // 首次进入直接落位；用户切换服务商时才展开/收起
                            enter = if (userSwitchedProvider) {
                                expandVertically(expandFrom = Alignment.Top) + fadeIn()
                            } else {
                                EnterTransition.None
                            },
                            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                        ) {
                            QuotaRow(usage = usage)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 额度行：和「当前服务商」同组，选中云端时展开、切走时收起。
 *
 * 取不到时同样占位并说明，用户才能分清「没额度」和「没网络」。
 */
@Composable
private fun QuotaRow(usage: AiUsageUiState) {
    val ready = usage as? AiUsageUiState.Ready
    SettingsQuotaItem(
        title = stringResource(R.string.ai_usage_title),
        value = when (usage) {
            is AiUsageUiState.Ready -> stringResource(
                R.string.ai_usage_remaining,
                formatTokens(ready!!.usage.remainingTokens),
            )
            AiUsageUiState.Loading -> stringResource(R.string.ai_usage_loading)
            AiUsageUiState.Unavailable -> stringResource(R.string.ai_usage_unavailable)
            AiUsageUiState.Hidden -> ""
        },
        supporting = when (usage) {
            is AiUsageUiState.Ready -> stringResource(
                R.string.ai_usage_footer,
                formatTokens(ready!!.usage.dailyQuota),
                ready.usage.requestCount,
            )
            AiUsageUiState.Loading -> stringResource(R.string.ai_usage_loading_hint)
            AiUsageUiState.Unavailable -> stringResource(R.string.ai_usage_unavailable_hint)
            AiUsageUiState.Hidden -> ""
        },
        fraction = ready?.usage?.usedFraction,
        shapeIndex = 1,
        shapeCount = 2,
    )
}

/** 千分位，别让用户数位数。 */
private fun formatTokens(value: Long): String = NumberFormat.getIntegerInstance().format(value)

/**
 * 服务商菜单：两组，第一组是可选中的服务商，第二组是「新建服务商」动作。
 * 样式细节（内边距、间距、同心圆角、按压形变）都由 [AppMenu] 统一处理。
 */
@Composable
private fun ProviderMenu(
    expanded: Boolean,
    cloudEnabled: Boolean,
    selectedProviderId: String?,
    onDismiss: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onNewProvider: () -> Unit,
) {
    val noneLabel = stringResource(R.string.ai_provider_none)
    val cloudLabel = stringResource(R.string.ai_provider_cloud)
    val newLabel = stringResource(R.string.ai_provider_new)

    val providerEntries = buildList {
        add(
            MenuEntry(
                label = noneLabel,
                selected = selectedProviderId == null,
                onClick = {
                    onDismiss()
                    onSelectProvider(Constants.AI_PROVIDER_NONE)
                },
            ),
        )
        if (cloudEnabled) {
            add(
                MenuEntry(
                    label = cloudLabel,
                    selected = selectedProviderId == Constants.AI_PROVIDER_CLOUD,
                    onClick = {
                        onDismiss()
                        onSelectProvider(Constants.AI_PROVIDER_CLOUD)
                    },
                ),
            )
        }
    }
    val actionEntries = listOf(
        MenuEntry(
            label = newLabel,
            onClick = {
                onDismiss()
                onNewProvider()
            },
        ),
    )

    AppMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        groups = listOf(providerEntries, actionEntries),
    )
}
