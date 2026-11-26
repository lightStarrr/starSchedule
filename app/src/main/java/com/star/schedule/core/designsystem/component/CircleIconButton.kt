package com.star.schedule.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.star.schedule.core.designsystem.rememberAnimationsEnabled

/**
 * 顶栏右侧的纯图标按钮，容器与按压形变和 [CircleBackButton] 同一套。
 *
 * 用在 [PageTopBar] 的 actions 槽位里：左侧返回、右侧操作，两端视觉重量对齐。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledContainerColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    disabledContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val colorSpec = if (animationsEnabled) {
        MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    } else {
        snap()
    }
    val resolvedContainerColor by animateColorAsState(
        targetValue = if (enabled) containerColor else disabledContainerColor,
        animationSpec = colorSpec,
        label = "circleIconContainerColor",
    )
    val resolvedContentColor by animateColorAsState(
        targetValue = if (enabled) contentColor else disabledContentColor,
        animationSpec = colorSpec,
        label = "circleIconContentColor",
    )

    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(shape = CircleShape),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = resolvedContainerColor,
            contentColor = resolvedContentColor,
            disabledContainerColor = resolvedContainerColor,
            disabledContentColor = resolvedContentColor,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
        )
    }
}
