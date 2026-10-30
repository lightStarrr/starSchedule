package com.star.schedule.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 撤销这类「可以挽回」的操作留给用户的默认时间。 */
const val UNDO_SNACKBAR_TIMEOUT_MILLIS = 5_000

/** 只是告知结果的提示：看得清就够了，不用占太久。 */
const val MESSAGE_SNACKBAR_TIMEOUT_MILLIS = 4_000

/**
 * 带倒计时进度条的提示条，配合 [SnackbarHost] 的 `snackbarHost` 槽位使用。
 *
 * Compose 的 Snackbar 只有「4 秒 / 10 秒 / 一直显示」三档，不会画倒计时：
 * 想让人看到「还剩多少时间可以撤销」，只能自己跑一条进度。这里统一做掉，
 * 调用方用 [androidx.compose.material3.SnackbarHostState.showSnackbar] 时传
 * `SnackbarDuration.Indefinite`，由进度条走完来关闭，别再用时长参数，否则两个计时器会打架。
 *
 * 有操作按钮时按钮点击会走 `performAction()`（`showSnackbar` 返回 `ActionPerformed`），
 * 没有操作时就是一条普通的结果提示，同样按 [timeoutMillis] 自动收起。
 */
@Composable
fun CountdownSnackbar(
    data: SnackbarData,
    timeoutMillis: Int,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(1f) }
    // data 每次 showSnackbar 都是新实例：连续两条提示会各自重新开始倒计时。
    LaunchedEffect(data) {
        progress.snapTo(1f)
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = timeoutMillis,
                easing = LinearEasing,
            ),
        )
        data.dismiss()
    }

    // 进度条画在 Snackbar 的底边上、叠在它上面，不参与排版：
    // 塞进内容里会把文字往上顶，还不让提示条变高。
    Box(
        modifier = modifier
            // 官方 Snackbar 自己不处理边距：内容一接管就丢了默认 16dp，这里补回来。
            // 底部让开导航栏的事交给 SnackbarHost。
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(SnackbarDefaults.shape),
    ) {
        Snackbar(
            // 底色用 surface，和页面内容同一色系：文字 onSurface、操作项和进度条用 primary。
            // 默认的 inverseSurface 在深色模式下是浅底配浅字，读不清。
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionContentColor = MaterialTheme.colorScheme.primary,
            action = {
                data.visuals.actionLabel?.let { label ->
                    TextButton(
                        onClick = { data.performAction() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(label)
                    }
                }
            },
        ) {
            Text(data.visuals.message)
        }
        LinearProgressIndicator(
            progress = { progress.value },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent,
            // 新规范会在轨道末端画一个「停止点」，2dp 的细线上就是一颗突兀的小圆点。
            drawStopIndicator = {},
        )
    }
}
