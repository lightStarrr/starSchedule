package com.star.schedule.feature.settings.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
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
import com.star.schedule.feature.settings.domain.AiModelCatalog
import java.text.NumberFormat

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
        onSelectModel = viewModel::selectModel,
        onRefreshModels = viewModel::refreshModels,
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
    onSelectModel: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onNewProvider: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navigationBarInset = navigationBarBottomInset()
    var menuExpanded by remember { mutableStateOf(false) }
    // 进页面时模型/额度这两行会从「无」变成有数据，那一帧不该播展开动画——只有用户自己改服务商才播
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
                    // 额度跟着服务商同进同出，所以这一组只可能是 1 行或 2 行
                    val positions = aiSettingsRows(state.hasCloudDetails)
                        .positions()
                        .associateBy { it.row }
                    val providerPosition = positions.getValue(AiSettingsRow.Provider)

                    SettingsGroup {
                        SettingsListItem(
                            title = stringResource(R.string.ai_provider_row_title),
                            supporting = selectedName,
                            onClick = { menuExpanded = true },
                            shapeIndex = providerPosition.index,
                            shapeCount = providerPosition.count,
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
                            visible = state.hasCloudDetails,
                            enter = quotaEnter(userSwitchedProvider),
                            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                        ) {
                            // 收起动画期间这一行仍在树上，位置直接写死 1/2：
                            // 用「可见行」查位置会因为查不到而拿不到值
                            QuotaRow(usage = usage, shapeIndex = 1, shapeCount = 2)
                        }
                    }
                }
                item {
                    // 模型是独立分组：它属于「选哪个模型」而不是「用哪个服务商」
                    AnimatedVisibility(
                        visible = state.hasCloudDetails,
                        enter = quotaEnter(userSwitchedProvider),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                    ) {
                        Column {
                            SettingsSectionTitle(
                                resId = R.string.ai_section_model,
                                topPadding = 24.dp,
                            )
                            SettingsGroup {
                                ModelRow(
                                    state = state,
                                    shapeIndex = 0,
                                    shapeCount = 1,
                                    onSelectModel = onSelectModel,
                                    onRefreshModels = onRefreshModels,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 模型行：和服务商一样用菜单选，只是可选集合来自服务端。
 *
 * 拉取失败时点整行等于重试——比弹一个空菜单更符合直觉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelRow(
    state: AiSettingsUiState,
    shapeIndex: Int,
    shapeCount: Int,
    onSelectModel: (String) -> Unit,
    onRefreshModels: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val models = state.models
    val menuAvailable = models is AiModelUiState.Ready

    val onClick: (() -> Unit)? = when (models) {
        AiModelUiState.Hidden, AiModelUiState.Loading -> null
        AiModelUiState.Unavailable -> onRefreshModels
        is AiModelUiState.Ready -> {
            { expanded = true }
        }
    }

    SettingsListItem(
        title = stringResource(R.string.ai_model_row_title),
        supporting = modelSupportingText(state),
        enabled = onClick != null,
        onClick = onClick,
        shapeIndex = shapeIndex,
        shapeCount = shapeCount,
        leading = { Icon(Icons.Rounded.Memory, contentDescription = null) },
        trailing = {
            Box {
                IconButton(
                    onClick = onClick ?: {},
                    enabled = onClick != null,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.UnfoldMore,
                        contentDescription = stringResource(R.string.ai_model_menu),
                    )
                }
                if (menuAvailable) {
                    ModelMenu(
                        expanded = expanded,
                        catalog = (models as AiModelUiState.Ready).catalog,
                        selectedAlias = state.selectedModelAlias,
                        onDismiss = { expanded = false },
                        onSelectModel = { alias ->
                            expanded = false
                            onSelectModel(alias)
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun modelSupportingText(state: AiSettingsUiState): String = when (val models = state.models) {
    AiModelUiState.Hidden, AiModelUiState.Loading -> stringResource(R.string.ai_model_loading)
    AiModelUiState.Unavailable -> stringResource(R.string.ai_model_unavailable)
    is AiModelUiState.Ready -> {
        val selected = state.selectedModel
        when {
            models.catalog.isEmpty -> stringResource(R.string.ai_model_empty)
            selected != null -> selected.displayName
            else -> stringResource(R.string.ai_model_follow_default)
        }
    }
}

/**
 * 模型菜单：首项「跟随服务端默认」，其余为后台已启用的模型。
 *
 * 只显示名字、不拼厂商——后台约定 alias 全局唯一，同名冲突不会发生。
 */
@Composable
private fun ModelMenu(
    expanded: Boolean,
    catalog: AiModelCatalog,
    selectedAlias: String?,
    onDismiss: () -> Unit,
    onSelectModel: (String) -> Unit,
) {
    val followDefaultLabel = stringResource(R.string.ai_model_follow_default)
    val entries = buildList {
        add(
            MenuEntry(
                label = followDefaultLabel,
                selected = selectedAlias == null,
                onClick = {
                    onDismiss()
                    onSelectModel("")
                },
            ),
        )
        catalog.models.forEach { model ->
            add(
                MenuEntry(
                    label = model.displayName,
                    selected = model.alias == selectedAlias,
                    onClick = {
                        onDismiss()
                        onSelectModel(model.alias)
                    },
                ),
            )
        }
    }

    AppMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        groups = listOf(entries),
    )
}

/**
 * 额度行：和「服务商」「模型」同组，选中云端时展开、切走时收起。
 *
 * 取不到时同样占位并说明，用户才能分清「没额度」和「没网络」。
 */
@Composable
private fun QuotaRow(usage: AiUsageUiState, shapeIndex: Int, shapeCount: Int) {
    val ready = usage as? AiUsageUiState.Ready
    SettingsQuotaItem(
        title = stringResource(R.string.ai_usage_title),
        value = when (usage) {
            is AiUsageUiState.Ready -> stringResource(
                R.string.ai_usage_remaining,
                formatTokens(ready!!.usage.remainingTokens),
            )
            AiUsageUiState.Loading, AiUsageUiState.Hidden ->
                stringResource(R.string.ai_usage_loading)
            AiUsageUiState.Unavailable -> stringResource(R.string.ai_usage_unavailable)
        },
        supporting = when (usage) {
            is AiUsageUiState.Ready -> stringResource(
                R.string.ai_usage_footer,
                formatTokens(ready!!.usage.dailyQuota),
                ready.usage.requestCount,
            )
            AiUsageUiState.Loading, AiUsageUiState.Hidden ->
                stringResource(R.string.ai_usage_loading_hint)
            AiUsageUiState.Unavailable -> stringResource(R.string.ai_usage_unavailable_hint)
        },
        fraction = ready?.usage?.usedFraction,
        shapeIndex = shapeIndex,
        shapeCount = shapeCount,
    )
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

/**
 * 云端专属区块的进入动画：进页面直接落位，只有用户自己切服务商时才展开。
 */
private fun quotaEnter(userSwitchedProvider: Boolean): EnterTransition =
    if (userSwitchedProvider) {
        expandVertically(expandFrom = Alignment.Top) + fadeIn()
    } else {
        EnterTransition.None
    }

/** 千分位，别让用户数位数。 */
private fun formatTokens(value: Long): String = NumberFormat.getIntegerInstance().format(value)
