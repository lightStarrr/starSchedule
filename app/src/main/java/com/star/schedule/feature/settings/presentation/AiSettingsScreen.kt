package com.star.schedule.feature.settings.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    AiSettingsScreen(
        state = state,
        onBack = onBack,
        onSelectProvider = viewModel::selectProvider,
        onNewProvider = onNewProvider,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AiSettingsScreen(
    state: AiSettingsUiState,
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
            }
        }
    }
}

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
