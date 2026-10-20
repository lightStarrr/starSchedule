package com.star.schedule.feature.importing.wakeup.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.CircleBackButton

@Composable
fun WakeUpImportRoute(
    viewModel: WakeUpImportViewModel,
    onBack: () -> Unit,
    onImported: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.phase) {
        if (state.phase is WakeUpImportPhase.Finished) onImported()
    }

    WakeUpImportScreen(
        state = state,
        onBack = onBack,
        onInputChange = viewModel::onInputChange,
        onImport = viewModel::import,
    )
}

/**
 * WakeUp 导入：整页只有一个输入框和一颗按钮。
 *
 * 这个流程的实质是把别处的分享口令搬过来，没有需要填的表单，所以不裱卡片、不做步骤条，
 * 空页面的注意力全部给输入框；底部那颗九瓣饼干形状的按钮是唯一动作。
 */
@Composable
private fun WakeUpImportScreen(
    state: WakeUpImportUiState,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onImport: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // 底部只认一个内边距：键盘收起时是导航栏高度，弹出时是键盘高度。
                // 分开写 imePadding() + 导航栏内边距的话，两者切换是离散的，动画中途会跳一下。
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                ),
        ) {
            // 尺寸跟着可用高度连续变化，不能用阈值切换规格：一旦跨阈值，
            // 键盘动画到一半时输入框会先冲过头再弹回来。
            val roominess = ((maxHeight - 400.dp) / 140.dp).coerceIn(0f, 1f)
            val fieldHeight = 88.dp + 44.dp * roominess
            val topReserve = 48.dp + 24.dp * roominess
            val bottomGap = 12.dp + 28.dp * roominess

            CircleBackButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp),
            )

            // 上下两段空白把输入框压在正中；按钮参与布局而不是浮在上面，
            // 这样键盘顶起页面时两者不会重叠。
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    // 把顶栏那块让出来，输入框才不会顶到返回按钮上。
                    .padding(top = topReserve),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 上方权重略大于下方：按钮占了自己的高度，等权重会让输入框整体偏上，
                // 按 1.2 : 1 分才回到屏幕视觉中心。
                Spacer(Modifier.weight(1.2f))

                ShareKeyField(
                    state = state,
                    onInputChange = onInputChange,
                    minFieldHeight = fieldHeight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .padding(horizontal = 28.dp),
                )

                Spacer(Modifier.weight(1f))

                CookieImportButton(
                    enabled = state.canImport,
                    importing = state.isImporting,
                    onClick = onImport,
                    modifier = Modifier.padding(bottom = bottomGap),
                )
            }
        }
    }
}

@Composable
private fun ShareKeyField(
    state: WakeUpImportUiState,
    onInputChange: (String) -> Unit,
    minFieldHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(28.dp)

    Column(modifier = modifier) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                width = 1.dp,
                color = when {
                    focused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    state.phase is WakeUpImportPhase.Failed -> MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
            ),
        ) {
            BasicTextField(
                value = state.input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minFieldHeight)
                    .padding(20.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.TopStart) {
                        if (state.input.isEmpty()) {
                            Text(
                                text = stringResource(R.string.wakeup_share_code_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        StatusLine(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
        )
    }
}

@Composable
private fun StatusLine(
    state: WakeUpImportUiState,
    modifier: Modifier = Modifier,
) {
    val phase = state.phase
    val (text, color) = when {
        phase is WakeUpImportPhase.Failed -> phase.error.message() to MaterialTheme.colorScheme.error
        phase is WakeUpImportPhase.Importing ->
            stringResource(R.string.wakeup_import_fetching) to MaterialTheme.colorScheme.onSurfaceVariant
        state.shareKey.isNotEmpty() ->
            stringResource(R.string.wakeup_import_key_detected, state.shareKey.preview()) to
                MaterialTheme.colorScheme.primary
        state.input.isNotBlank() ->
            stringResource(R.string.wakeup_error_invalid_code) to MaterialTheme.colorScheme.onSurfaceVariant
        else ->
            stringResource(R.string.wakeup_import_key_hint) to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun WakeUpImportError.message(): String = stringResource(
    when (this) {
        WakeUpImportError.EmptyCode -> R.string.wakeup_error_empty_code
        WakeUpImportError.InvalidCode -> R.string.wakeup_error_invalid_code
        WakeUpImportError.Network -> R.string.wakeup_error_network
        WakeUpImportError.Rejected -> R.string.wakeup_error_import_failed
        WakeUpImportError.Unsupported -> R.string.wakeup_error_unsupported
    },
)

/** 口令一般 16 位，露前 8 位就够用户确认识别对了，不必让整串把状态行挤成两行。 */
private fun String.preview(): String = if (length > 8) take(8) + "…" else this

/**
 * 九瓣饼干形状的提交按钮，加载时在原地换成进度环，不额外弹任何遮罩。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CookieImportButton(
    enabled: Boolean,
    importing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = enabled || importing
    val cookieShape = MaterialShapes.Cookie9Sided.toShape()
    val containerColor = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    IconButton(
        onClick = onClick,
        enabled = enabled,
        // requiredSize：形状按钮被父布局压扁时会更难看，宁可溢出也不变形。
        modifier = modifier.requiredSize(92.dp),
        shapes = IconButtonDefaults.shapes(shape = cookieShape),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
    ) {
        if (importing) {
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                color = contentColor,
                strokeWidth = 2.5.dp,
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = stringResource(R.string.action_import),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
