package com.star.schedule.feature.settings.presentation

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
                    SettingsGroup {
                        SettingsListItem(
                            title = stringResource(R.string.ai_provider_row_title),
                            supporting = selectedName,
                            onClick = { menuExpanded = true },
                            shapeIndex = 0,
                            shapeCount = 1,
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
                                        onSelectProvider = onSelectProvider,
                                        onNewProvider = onNewProvider,
                                    )
                                }
                            },
                        )
                    }
                }
                if (usage != AiUsageUiState.Hidden) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        QuotaCard(state = usage)
                    }
                }
            }
        }
    }
}

/**
 * 云端额度卡片：只在选中云端 AI 时出现，展示当日剩余与已用比例。
 *
 * 取不到时不留空白——照样占位并说明，用户能分清「没额度」和「没网络」。
 */
@Composable
private fun QuotaCard(state: AiUsageUiState) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        // 和列表卡片同一套圆角，块与块之间留 8dp
        shape = ListItemDefaults.segmentedShapes(0, 1).shape,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.ai_usage_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                val trailing = when (state) {
                    is AiUsageUiState.Ready -> stringResource(
                        R.string.ai_usage_remaining,
                        formatTokens(state.usage.remainingTokens),
                    )
                    AiUsageUiState.Loading -> stringResource(R.string.ai_usage_loading)
                    AiUsageUiState.Unavailable -> stringResource(R.string.ai_usage_unavailable)
                    AiUsageUiState.Hidden -> ""
                }
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state is AiUsageUiState.Ready) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { state.usage.usedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.ai_usage_footer,
                        formatTokens(state.usage.dailyQuota),
                        state.usage.requestCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
