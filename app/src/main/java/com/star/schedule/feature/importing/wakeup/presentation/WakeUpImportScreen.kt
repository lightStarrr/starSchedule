package com.star.schedule.feature.importing.wakeup.presentation

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.CircleBackButton
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun WakeUpImportRoute(
    viewModel: WakeUpImportViewModel,
    onBack: () -> Unit,
    onImported: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    WakeUpImportScreen(
        state = state,
        onBack = onBack,
        onInputChange = viewModel::onInputChange,
        onImport = viewModel::import,
        // 成功不再一到就把页面撤掉：先让指示器原地收回饼干、染成 success 色、弹一个勾，
        // 停一下再交给外面（那个回调会 finish 这个 Activity）。
        onImported = onImported,
    )
}

/**
 * WakeUp 导入：顶栏、输入框、按钮，三者之外暂时不放任何内容。
 *
 * 错误不单独占一行：它顶替输入框下面那句「口令 30 分钟内有效」，用户一改输入就换回原文案。
 * 整页套一层 WakeUp 自己的珊瑚粉配色，只影响这一页。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WakeUpImportScreen(
    state: WakeUpImportUiState,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onImport: () -> Unit,
    onImported: () -> Unit,
) {
    // 用 MaterialExpressiveTheme 而不是 MaterialTheme：后者会把 motionScheme 重置成默认，
    // 这里的形状/颜色动画虽然是显式 tween，但没必要让整页的运动规格跟主题脱节。
    MaterialExpressiveTheme(colorScheme = wakeUpImportColorScheme()) {
        WakeUpImportContent(
            state = state,
            onBack = onBack,
            onInputChange = onInputChange,
            onImport = onImport,
            onImported = onImported,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WakeUpImportContent(
    state: WakeUpImportUiState,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onImport: () -> Unit,
    onImported: () -> Unit,
) {
    val submitting = state.phase is WakeUpImportPhase.Importing
    // 成功不走归位：按钮留在屏幕中间，就地收回饼干、染成 success 色、弹一个勾。
    val succeeding = state.phase is WakeUpImportPhase.Finished

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 去程和归位是两套编排，归位不是把去程倒放：箭头从左边滑进来、输入框从上方落下来。
    // 两条时间轴各管一套，谁也不影响谁；`returning` 只是决定下游读哪一套。
    val timeline = remember { Animatable(0f) }
    val rewind = remember { Animatable(0f) }
    var returning by remember { mutableStateOf(false) }
    // 加载指示器有没有"收回饼干"。归位要等它点头才开演。
    var indicatorSettled by remember { mutableStateOf(false) }

    LaunchedEffect(submitting) {
        if (submitting) {
            // 先收键盘再动：输入法展开时可用高度少一截，布局是压缩过的，
            // 这时候起跳，飞出去的位置和落点都不对。
            focusManager.clearFocus()
            keyboardController?.hide()
            returning = false
            indicatorSettled = false
            timeline.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = SUBMIT_MILLIS, easing = LinearEasing),
            )
        } else if (timeline.value > 0f) {
            // 结果可能比出场编排还早到。这个 LaunchedEffect 是按 `submitting` 重启的，
            // 一重启，上面那条 `timeline.animateTo(1f)` 就被取消，时间轴停在 0.9 附近。
            // 后面的映射虽然都按 0..1 收口，停在半路也意味着箭头、输入框的离场值都没走完——
            // 先把编排补完，再谈收尾。
            val remaining = (1f - timeline.value) * SUBMIT_MILLIS
            if (remaining > 1f) {
                timeline.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = remaining.toInt(), easing = LinearEasing),
                )
            }
            // 收尾一律先等按钮把形状收回饼干——它此刻是"转到一半的某个形状"，
            // 直接撤掉再换一颗饼干回来，就是用户说的"太突兀"。
            // 这一步是它自己的时间轴，时长取决于结果落在哪一帧，所以只能等它通知。
            //
            // 这里的 `succeeding` 是启动这个协程那一帧的值（phase 和 submitting 同时变），
            // 之后用户改输入再把 phase 拨回 Idle 也不会影响已经选好的分支。
            if (succeeding) {
                // 成功：不归位、不往下飞。指示器原地收回饼干（顺带染成 success 色、弹一个勾），
                // 停一下再离开这一页，让用户看清"成了"。
                withTimeoutOrNull(INDICATOR_SETTLE_TIMEOUT_MILLIS) {
                    snapshotFlow { indicatorSettled }.first { it }
                }
                delay(SUCCESS_HOLD_MILLIS)
                onImported()
            } else {
                returning = true
                withTimeoutOrNull(INDICATOR_SETTLE_TIMEOUT_MILLIS) {
                    snapshotFlow { indicatorSettled }.first { it }
                }
                rewind.snapTo(0f)
                rewind.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = RETURN_MILLIS, easing = LinearEasing),
                )
                // 收尾把两条轴都归零再切回去程映射：那一刻两套算出来都是静止态，不会跳。
                rewind.snapTo(0f)
                timeline.snapTo(0f)
                returning = false
            }
        } else if (succeeding) {
            // 没播过去程就已经是成功态（例如进程被回收后恢复）：别把用户卡在这一页。
            onImported()
        }
    }

    val elapsed = timeline.value
    // 去程：全部从 0 涨到 1，元素依次"离开"。
    val arrowExit = elapsed.segment(0.00f, 0.20f).accelerate()
    val hintExit = elapsed.segment(0.08f, 0.30f).accelerate()
    val fieldExit = elapsed.segment(0.16f, 0.56f).accelerate()
    val buttonExit = elapsed.segment(0.50f, 0.86f).decelerate()

    // 归位：按钮先回原位，输入框、提示、箭头再依次回来。
    // 0.00~0.16 空着不动：那是收尾刚结束的一拍，让它停一下再往下走，别和归位黏在一起。
    val rewindProgress = rewind.value
    val buttonBack = rewindProgress.segment(0.16f, 0.74f).settle()
    val fieldIn = rewindProgress.segment(0.50f, 0.86f).settle()
    val hintIn = rewindProgress.segment(0.62f, 0.90f).settle()
    val arrowIn = rewindProgress.segment(0.70f, 0.96f).settle()

    // 两套编排收敛成同几个量，下游只认这些。
    // 归位时箭头从左边滑进来（终点 0），去程时向右飞出去（终点 +ARROW_FLY）。
    val arrowOffsetX = if (returning) -ARROW_FLY * (1f - arrowIn) else ARROW_FLY * arrowExit
    val arrowAlpha = if (returning) arrowIn else 1f - arrowExit
    // 0 = 原位，1 = 上移 HINT_LIFT；归位时从上方落回原位。
    val hintShift = if (returning) 1f - hintIn else hintExit
    val hintAlpha = if (returning) hintIn else 1f - hintExit
    val buttonMove = if (returning) 1f - buttonBack else buttonExit

    // 按钮飞到屏幕中间之后才开始换形状。失败要等指示器收回饼干（indicatorSettled）才交回按钮，
    // 否则会在序列走了一半的时候把形状直接换成饼干。
    val loading = elapsed >= LOADING_START && !(returning && indicatorSettled)

    // 三段动画期间输入框都不接输入：去程它压着按钮，归位时它还没落稳，成功时页面正要离开。
    val frozen = submitting || returning || succeeding

    // 提示位上放什么：默认是「口令 30 分钟内有效」，失败时换成错误文案，用户一改输入就换回原文案。
    //
    // 只卡"去程"（[submitting]），不卡归位：失败一发生就把文案换成错误，这样输入框飞回来的时候
    // 里面已经写着错误了——用户看到的是"框带着错误回来"，而不是"先回提示、再换成错误"。
    // 归位前半段整块文案的 alpha 还是 0，换这一下正好落在那段里，看不见。
    //
    // 去程必须卡住：重试时 phase 会先回到 Idle，若立刻跟着换，提示会在淡出的前半段闪回原文案。
    var message by remember { mutableStateOf<WakeUpImportError?>(null) }
    val failure = (state.phase as? WakeUpImportPhase.Failed)?.error
    LaunchedEffect(failure, submitting) {
        if (!submitting) message = failure
    }
    val errorText = message?.let { stringResource(it.messageRes()) }

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

            // 按钮的终点。不用 constraints：它的高度已经扣掉了底部 inset，
            // 算出来会比屏幕中心高半个导航栏。窗口尺寸才是真·屏幕中心。
            val windowSize = LocalWindowInfo.current.containerSize
            val screenCenter = Offset(windowSize.width / 2f, windowSize.height / 2f)
            // 两个中心都在 root 坐标系里取，跟各自最终摆在哪无关；只在布局阶段写一次，
            // 后面的位移全部在 graphicsLayer / offset 里读，不触发重组。
            var fieldCenter by remember { mutableStateOf(Offset.Zero) }
            var buttonCenter by remember { mutableStateOf(Offset.Zero) }

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
                    value = state.input,
                    onValueChange = onInputChange,
                    minFieldHeight = fieldHeight,
                    errorText = errorText,
                    hintShift = hintShift,
                    hintAlpha = hintAlpha,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .padding(horizontal = 28.dp)
                        .onGloballyPositioned { fieldCenter = it.centerInRoot() }
                        // 顺序不能反：graphicsLayer 必须在外层。blur 内部会开一个跟控件同样大小的
                        // 离屏缓冲，位移若写在它里层，飞出原来那块矩形的内容就被这个缓冲裁掉了——
                        // 表现就是"没有真的移动，只是被切了一刀"。
                        .graphicsLayer {
                            if (returning) {
                                // 归位：从上方微微落下来，不缩放、不向按钮收。
                                translationX = 0f
                                translationY = -(RETURN_DROP.toPx() * (1f - fieldIn))
                                scaleX = 1f
                                scaleY = 1f
                                alpha = fieldIn
                            } else {
                                val delta = (buttonCenter - fieldCenter) * fieldExit
                                translationX = delta.x
                                translationY = delta.y
                                val shrink = 1f - FIELD_SHRINK * fieldExit
                                scaleX = shrink
                                scaleY = shrink
                                alpha = 1f - fieldExit
                            }
                        }
                        // 模糊半径只能给具体 dp，必须参与重组；位移和缩放不重组。
                        // 归位时从糊到清晰，配合淡入，像是"聚焦上来"。
                        .blur(
                            radius = if (returning) {
                                RETURN_BLUR * (1f - fieldIn)
                            } else {
                                FIELD_BLUR * fieldExit
                            },
                            edgeTreatment = BlurredEdgeTreatment.Unbounded,
                        ),
                )

                Spacer(Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .padding(bottom = bottomGap)
                        .onGloballyPositioned { buttonCenter = it.centerInRoot() }
                        // offset 写在 observer 之后：上报的是移动前的位置，终点才是稳定的。
                        .offset {
                            IntOffset(
                                x = ((screenCenter.x - buttonCenter.x) * buttonMove).roundToInt(),
                                y = ((screenCenter.y - buttonCenter.y) * buttonMove).roundToInt(),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    CookieButton(
                        // 归位途中按钮还在往下飞，这时候点它会把两套编排撞在一起，先不理。
                        onClick = { if (!returning) onImport() },
                        // 输入框空着、或文本里认不出口令时按钮置灰，避免点了没反应又看不到错误提示。
                        enabled = state.canImport,
                        // 提交期间按钮已经点不动了（scrim 也挡着），但颜色要保住品牌色。
                        busy = submitting,
                        loading = loading,
                        settling = returning || succeeding,
                        success = succeeding,
                        arrowOffsetX = arrowOffsetX,
                        arrowAlpha = arrowAlpha,
                        onSettled = { indicatorSettled = true },
                    )
                }
            }

            // 挡板：出场/归位期间盖在内容上，让事件根本到不了输入框。
            //
            // 输入框飞出去时正好落在按钮身上，而 graphicsLayer 的位移连触摸区域一起搬过去，
            // 所以那会儿点按钮等于点输入框——焦点一给出去键盘就弹回来，连点几下还会往输入框里打字。
            // 这里不是"消费"事件，而是让它排在最上面：命中测试只看最上层，下面的输入框收不到。
            if (frozen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                )
            }

            // 返回按钮放在最后画：它在挡板之上，加载很久的时候也退得出去。
            CircleBackButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun ShareKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    minFieldHeight: Dp,
    errorText: String?,
    hintShift: Float,
    hintAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val hint = stringResource(R.string.wakeup_import_key_hint)

    // 0 = 提示，1 = 错误。一个值管住两个方向：错的时候在 1，改输入后往 0 落。
    val swap = remember { Animatable(if (errorText != null) 1f else 0f) }
    LaunchedEffect(errorText) {
        if (errorText != null) {
            // 错误一出现就顶上，不演"提示→错误"的换字：那句话只是过渡，用户不需要看到。
            swap.snapTo(1f)
        } else {
            // 只有"改输入、提示回来"这一程是模糊过渡——那是用户主动操作，值得看清楚。
            swap.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = MESSAGE_SWAP_MILLIS, easing = LinearEasing),
            )
        }
    }
    val weight = swap.value
    // 两句严格互补（和恒为 1）：任一刻至少有一句是"够看得见"的，不会出现中间那段空白。
    // 之前是错开收尾（0.70 / 0.30），中途两句都掉到 0.24，看上去就是字"没了"一下。
    val hintVisible = 1f - weight
    val errorVisible = weight
    // 糊的程度取一条中段最高的钟形曲线，和淡入淡出不同步：交叉的瞬间两句都是半透明，
    // 若还都清清楚楚，叠在同一行里就是"两个字重影"。糊到最厉害正好盖住这一瞬。
    val messageBlur = MESSAGE_BLUR * sin(PI.toFloat() * weight)

    // 退场的那句要画完再走。`errorText` 一变 null 它就该消失了，但若这时直接从组合里撤掉，
    // 这块 supportingText 的高度会当场掉成 0——外层 Column 的 weight 重新分配，输入框猛地往下
    // 挪一截再挪回来，看起来就是「框往下扩了一下」。所以留住上一句，让它跟提示一起淡完。
    var fadingError by remember { mutableStateOf(errorText) }
    LaunchedEffect(errorText) {
        if (errorText != null) fadingError = errorText
    }
    // 委派属性不能智能转换，取出来给下面用。
    val shownError = fadingError

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minFieldHeight),
        textStyle = MaterialTheme.typography.bodyLarge,
        // 用主题里的形状预设，不自己写圆角；extraLarge 在当前 M3 形状表里就是 28dp。
        shape = MaterialTheme.shapes.extraLarge,
        supportingText = {
            // 两条文案叠在同一个位置上互相替换，位置只由外面这层管：
            // 出场时整块往上推、淡出，归位时从上方落回来。
            //
            // 两句都常驻，不写 `if (visible > 0f)`：谁被临时移出组合，这块的高度就变一次。
            // 提示那句永远在（就算 alpha 是 0），所以这块的高度从头到尾是「一行」，
            // 输入框不会因为换字而移动一分。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = -(HINT_LIFT.toPx() * hintShift)
                        alpha = hintAlpha
                    },
                contentAlignment = Alignment.Center,
            ) {
                FieldMessage(
                    text = hint,
                    visible = hintVisible,
                    blur = messageBlur,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (shownError != null) {
                    FieldMessage(
                        text = shownError,
                        visible = errorVisible,
                        blur = messageBlur,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

/**
 * 提示位上的一行字：按 [visible] 淡入淡出，按 [blur] 糊开。
 *
 * 两者分开传，是因为换字那一段要的曲线不一样：透明度互补（和恒为 1）保证不空场，
 * 模糊则在中段冲最高，正好把两句半透明的字糊成一团，而不是叠出重影。
 *
 * 刻意收成一行（[TextOverflow.Ellipsis]）：两条文案叠在一起，谁更高谁就决定这块的高度。
 * 若允许换行，错误文案一出现会把输入框整体顶高一行，而那时它还是半透明的——
 * 看上去就是"字还没出来，框先跳了"。
 */
@Composable
private fun FieldMessage(text: String, visible: Float, blur: Dp, color: Color) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = visible }
            // 模糊半径只能给具体 dp，必须参与重组；淡入淡出不参与。
            .blur(radius = blur, edgeTreatment = BlurredEdgeTreatment.Unbounded),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

/**
 * 九瓣饼干按钮：按下的圆、提交时的逐帧换形、成功后的染绿和勾，全都在这一颗按钮里画。
 *
 * **为什么只有一颗**：以前按钮和加载指示器是两层——按钮给 `IconButton` 一个 `Shape`，
 * 指示器用 `Canvas` 自己画，交接靠"一层盖一层"。两套画法哪怕都走 [toFittedPath]，
 * 只要有一点差异（动画停在 0.99、角度差半度、透明度没铺满），叠起来就是错位。
 * 现在形状只由 [CookieShape] 提供，旋转烘进轮廓（所以里面的图标不跟着转），颜色和图标各自插值：
 * 任何时刻屏幕上只有一份几何，也就没有"两边对不齐"这回事。
 *
 * MaterialShapes 的 `toShape()` 返回匿名 `Shape`，不是 `CornerBasedShape`，
 * IconButton 自带的形状动画永远不会参与——只能自己用 `Morph` 驱动进度；
 * `colors` 也只吃静态值，颜色同样要自己插值。
 *
 * [loading] 一起，形状进入"饼干 → 指示器序列 → 饼干"；[settling] 表示要收尾，手头这一步走完
 * 就边转一步边变回饼干；[success] 让收回的那一步染成 success 色并弹出一个勾；
 * [busy] 表示出场编排正在播——此时虽然点不动，颜色必须保持品牌色，不能淡成禁用态的灰。
 * 收回完成（成功时连勾也算）才回调 [onSettled]。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CookieButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    settling: Boolean = false,
    success: Boolean = false,
    busy: Boolean = false,
    arrowOffsetX: Dp = 0.dp,
    arrowAlpha: Float = 1f,
    onSettled: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressMorph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }

    // 按下收成圆；一旦进加载序列就锁回饼干：序列的第一帧必须是按钮此刻画的那块饼干，
    // 否则换形状的第一帧会跳。长按不放时也不会把圆带进序列。
    val press = remember { Animatable(0f) }
    LaunchedEffect(pressed, loading) {
        if (loading) {
            press.snapTo(0f)
        } else {
            press.animateTo(
                targetValue = if (pressed) 1f else 0f,
                animationSpec = tween(durationMillis = PRESS_MORPH_MILLIS),
            )
        }
    }

    // 加载序列：状态机和自绘版一样，只是把"画"换成了"给容器提供 Shape"。
    val shapes = remember { WakeUpIndicatorShapes }
    val morphs = remember { WakeUpIndicatorMorphs }
    // 形变和这一步的旋转各走一条曲线：形变可以回弹，旋转必须单调。见 [TURN_SPRING]。
    val step = remember { Animatable(0f) }
    val turn = remember { Animatable(0f) }
    var index by remember { mutableIntStateOf(0) }
    // 收尾时改画这一段：从"当前形态"变回饼干。非空即表示正在收。
    var settleMorph by remember { mutableStateOf<Morph?>(null) }
    // 只有"变回饼干"那一下才染绿：成功信号来时手头那一步变成的是序列里的下一个形状，不是饼干。
    var becomingCookie by remember { mutableStateOf(false) }
    // 勾的入场进度。0 = 还没出现，1 = 到位；弹簧带一点过冲，看起来是"弹"出来的。
    val check = remember { Animatable(0f) }

    // 协程里读组合参数拿到的是启动那一刻的旧值，必须走 rememberUpdatedState。
    val settleNow by rememberUpdatedState(settling)
    val successNow by rememberUpdatedState(success)
    val notifySettled by rememberUpdatedState(onSettled)

    LaunchedEffect(loading, morphs) {
        if (!loading) return@LaunchedEffect
        // 每一轮都从饼干重新开始：上一轮停在哪个形状、转到哪个角度、勾有没有出来，都不该继承。
        index = 0
        step.snapTo(0f)
        turn.snapTo(0f)
        settleMorph = null
        becomingCookie = false
        check.snapTo(0f)

        // 按钮刚飞到位，先让它当一帧饼干再开始变形，节奏上有个喘气。
        delay(HANDOFF_HOLD_MILLIS)
        while (true) {
            // 手头这一步先走完再判断要不要收：在半途切开，形状会从中间硬换一次。
            // 两条动画并行跑，等慢的那条走完再进下一步。
            coroutineScope {
                launch { step.animateTo(targetValue = 1f, animationSpec = MORPH_SPRING) }
                launch { turn.animateTo(targetValue = ROTATE_PER_STEP, animationSpec = TURN_SPRING) }
            }
            if (settleNow) break
            index = (index + 1) % morphs.size
            // 序列首尾相接，所以这里从 1 归零不会看到形状跳变。
            step.snapTo(0f)
            turn.snapTo(0f)
            delay(MORPH_GAP_MILLIS)
            if (settleNow) break
        }

        // 停在 step = 1 时形状是 shapes[index + 1]；在两步之间被叫停则是 shapes[index]。
        // 两种都从"当前正画着的那个形状"起步，所以切到 settleMorph 的那一帧没有变化。
        val from = shapes[if (step.value >= 1f) (index + 1) % shapes.size else index]
        settleMorph = Morph(from, MaterialShapes.Cookie9Sided)
        step.snapTo(0f)
        // 从这一帧起画的是"变回饼干"的形变，成功的话颜色也从这一刻开始染。
        becomingCookie = true
        // 回来的这一程也要转：原地把形状直接换成饼干太生硬，边走边转才和前面的节奏接得上。
        // 转的是整整一个 [ROTATE_PER_STEP]，终点仍落在 [COOKIE_SYMMETRY_DEGREES] 的整数倍上。
        coroutineScope {
            launch { step.animateTo(targetValue = 1f, animationSpec = SETTLE_SPRING) }
            launch {
                turn.animateTo(
                    targetValue = turn.value + ROTATE_PER_STEP,
                    animationSpec = SETTLE_TURN_SPRING,
                )
            }
        }
        // 弹簧按 visibilityThreshold 提前收（`Animatable` 不会把值吸附到 target），可能停在 0.995
        // 这种地方。而下一帧就要把形状交还给"按钮自己的饼干"——`press` 那条 Morph 的 0 帧。
        // 差这一点点就是"换过去时边缘挪一下"，所以收回本来就是"回到饼干"，直接吸附到终点。
        step.snapTo(1f)
        // 同理，角度收到最近的 40° 整数倍：饼干九瓣，转 40° 与没转等价，这样和按钮那颗完全重合。
        turn.snapTo(
            (turn.value / COOKIE_SYMMETRY_DEGREES).roundToInt() * COOKIE_SYMMETRY_DEGREES,
        )
        // 勾要等饼干彻底变回来才出现，否则会和形状变化抢注意力。
        if (successNow) check.animateTo(targetValue = 1f, animationSpec = CHECK_SPRING)
        notifySettled()
    }

    val successColors = wakeUpSuccessColors()
    // 提交/加载期间就算点不动也要一直是品牌色：按钮这会儿正在往外飞，掉成灰色整段出场就脏了。
    // `enabled` 在 Importing / Finished 会变成 false，所以不能只看它。
    val tinted = enabled || busy || loading
    val successTint = becomingCookie && success
    // 染绿和"能不能点"是两件事，时长也不同，所以规格按当下这一程取。
    val colorMillis = if (successTint) SUCCESS_COLOR_MILLIS else ENABLE_COLOR_MILLIS
    // 颜色自己插值：IconButton 的 colors 只吃静态值，直接给 disabledContainerColor
    // 会在 enabled 翻转的那一帧硬切。插值结果同时喂给启用、禁用两组槽位，
    // 于是真正的 enabled 翻转发生时颜色早已走到位，中间不会闪。
    val containerColor by animateColorAsState(
        targetValue = when {
            successTint -> successColors.container
            tinted -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        },
        animationSpec = tween(durationMillis = colorMillis),
        label = "cookieContainerColor",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            successTint -> successColors.content
            tinted -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        },
        animationSpec = tween(durationMillis = colorMillis),
        label = "cookieContentColor",
    )

    // 形状就这三样：哪一对 Morph、走到哪、转了多少。三者任一变化都换一个新实例，
    // IconButton 每帧按新轮廓重画（形状实例不变的话它不会重新解算轮廓）。
    val morph = if (loading) settleMorph ?: morphs[index] else pressMorph
    val morphProgress = if (loading) step.value.coerceIn(0f, 1f) else press.value
    // 旋转只在形变那一下发生，转完就停住等下一步，和 M3 的 LoadingIndicator 同一个节奏。
    // 角度必须由 [turn] 这条单调曲线给，不能借用形变弹簧的 step——它低阻尼会回弹，
    // 角度冲过终点再被下一步 snapTo 拽回来，看起来就是"转过去了又倒一点"。
    // 每步的角度取模 360：序列绕回第一项时角度才不会倒着跳回去。
    val rotation = if (loading) (index * ROTATE_PER_STEP) % 360f + turn.value else 0f
    val shape = remember(morph, morphProgress, rotation) {
        CookieShape(morph, morphProgress, rotation)
    }

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        enabled = enabled && !loading,
        // requiredSize：形状按钮被父布局压扁时会更难看，宁可溢出也不变形。
        // clip：让飞出去的箭头被裁在按钮形状里——形状每帧都在变，clip 跟着重组。
        modifier = modifier
            .requiredSize(BUTTON_SIZE)
            .clip(shape),
        // 两个槽位给同一个实例，IconButton 才会当静态形状直接画，不再自己切换。
        shapes = IconButtonDefaults.shapes(shape = shape, pressedShape = shape),
        // 四个槽位都塞插值后的颜色：禁用态若留给默认值会退回「透明底 + 半透明图标」，
        // 整颗按钮看着会直接消失。
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
    ) {
        Box(
            modifier = Modifier.requiredSize(BUTTON_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            // 勾和箭头都不在旋转的轮廓里，所以它们不会被形状转着走。
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(CHECK_SIZE)
                    .graphicsLayer {
                        val entered = check.value
                        // 弹簧会冲过 1，透明度不能跟着过冲。
                        alpha = entered.coerceIn(0f, 1f)
                        val scale = CHECK_START_SCALE + (1f - CHECK_START_SCALE) * entered
                        scaleX = scale
                        scaleY = scale
                    },
            )
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = stringResource(R.string.action_import),
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer {
                        // 去程向右滑出去、归位从左滑回来，都由外部算好偏移量传进来；
                        // 被上面的 clip 关在按钮内，所以是「从按钮内部进出」，不是「从边缘冒出去」。
                        translationX = arrowOffsetX.toPx()
                        alpha = arrowAlpha * (1f - check.value.coerceIn(0f, 1f))
                    },
            )
        }
    }
}

/**
 * 把 `Morph` 在某个进度上的轮廓画成控件大小的形状。
 *
 * 这些形状都是 `normalized()` 过的：轮廓落在 [0,1] 单位盒里，按控件尺寸缩放后包围盒中心正好
 * 就是控件中心。所以这里**只缩放、不再按包围盒重新居中**。
 *
 * 按包围盒逐帧居中是踩过的坑：形变中途的插值轮廓会略微外凸，"凸"出来的那一侧会把包围盒中心
 * 拽偏，形状就被整体挪几像素；等形变收成饼干、包围盒回到正中，它又弹回原位。表现出来的正是
 * 「转到最后、往下走之前错位一下」。不重新居中就没有这个漂移，首尾两帧也和按钮那颗完全重合。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun Morph.toFittedPath(progress: Float, size: Size, out: Path = Path()): Path {
    out.reset()
    toPath(progress, out)
    out.transform(Matrix().apply { scale(size.width, size.height) })
    return out
}

/**
 * 把 `Morph` 在某个进度上的轮廓当成 `Shape` 用，并把这个形状转一个角度。
 *
 * 几何处理和 [toFittedPath] 一致：只按控件尺寸缩放，不按包围盒重新居中（原因见那里）。
 * 旋转在最后一步做，绕控件中心转——轮廓转、里面的图标不转。
 *
 * 旋转烘进轮廓而不是用 `graphicsLayer { rotationZ }`，是因为后者会把按钮内容一起转掉，
 * 而加载结束时那颗勾必须是正的。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class CookieShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotationDegrees: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = morph.toFittedPath(progress, size)
        if (rotationDegrees != 0f) {
            path.transform(
                Matrix().apply {
                    translate(size.width / 2f, size.height / 2f)
                    rotateZ(rotationDegrees)
                    translate(-size.width / 2f, -size.height / 2f)
                },
            )
        }
        return Outline.Generic(path)
    }
}

private const val PRESS_MORPH_MILLIS = 220

/**
 * 提示位换字的时长。
 *
 * 320ms 够让人看出是「一行字换成了另一行」，而不是硬切；再长就会拖到用户已经重新打字了还没换完。
 */
private const val MESSAGE_SWAP_MILLIS = 320

/** 换字时糊到的最大半径。够看出是"化开"，又不至于让 12sp 的字糊成一团。 */
private val MESSAGE_BLUR = 5.dp

/** 每个失败类别对应一句文案；放 UI 层是因为 ViewModel 不持有 Context。 */
@StringRes
private fun WakeUpImportError.messageRes(): Int = when (this) {
    WakeUpImportError.EmptyCode -> R.string.wakeup_import_error_empty
    WakeUpImportError.InvalidCode -> R.string.wakeup_import_error_invalid
    WakeUpImportError.Network -> R.string.wakeup_import_error_network
    WakeUpImportError.Rejected -> R.string.wakeup_import_error_rejected
    WakeUpImportError.Unsupported -> R.string.wakeup_import_error_unsupported
}

/** 可用状态切换时底色与图标色的过渡时长，和按下形变保持同一节奏。 */
private const val ENABLE_COLOR_MILLIS = 200

/**
 * 点击提交后整段出场动画的总时长。
 *
 * ViewModel 用同一个常量来决定「什么时候才真的发请求」——编排播完、按钮已经变成加载指示器
 * 之后才发，所以口令错误也不会把这段动画打断。
 */
private val SUBMIT_MILLIS = WAKEUP_SUBMIT_ANIMATION_MILLIS.toInt()

/** 归位（失败回滚）整段编排的时长。比去程短一点：回来这件事不需要慢慢演。 */
private const val RETURN_MILLIS = 720

private val BUTTON_SIZE = 92.dp

/**
 * 去程时间轴上「按钮已经到位」的位置，从这里起按钮开始换形状。
 *
 * 时间轴名义上到 1.0 才走完，但按钮的位移在 0.86 就结束了；早一点进，按钮还在收尾的那几帧
 * 就已经开始变形。真正的第一帧形变还有 [HANDOFF_HOLD_MILLIS] 的停顿兜着，不会抢。
 */
private const val LOADING_START = 0.86f

/**
 * 到位之后先当一帧饼干再开始变形。
 *
 * 按钮落地的减速还没完全停下就换形状，看起来像"没停稳就转起来了"；留 160ms 的呼吸，
 * 也让「按下松手变回饼干」这一步先收干净。
 */
private const val HANDOFF_HOLD_MILLIS = 160L

/** 饼干九个瓣，转 40° 和没转看上去完全一样。步进角取它的整数倍，交接时就不必再修正角度。 */
private const val COOKIE_SYMMETRY_DEGREES = 40f

/**
 * 每变形一步转过的角度。
 *
 * 只在这一步的形变过程中转，转完就停住，靠"转、换形、停一下"的节奏表达忙碌，不做持续自转。
 * 取 [COOKIE_SYMMETRY_DEGREES] 的两倍（80°）而不是 M3 的 90°：80 的任意整数倍都落在 40° 上，
 * 所以每一步的终点角度画出来都和"没转过"的饼干一模一样，归位时不必再把角度拉回去
 * （90 的整数倍离 40° 最远差 20°，会在最后一刻看到一次反向扭转）。
 */
private const val ROTATE_PER_STEP = 2f * COOKIE_SYMMETRY_DEGREES

/** 每步变形之后停一下，和弹簧一起构成约 1.1s 一步的节奏。 */
private const val MORPH_GAP_MILLIS = 260L

/**
 * 加载指示器的形状序列：饼干打头，绕一圈回到饼干。
 *
 * 各形状的曲线数不要求一致——`Morph.match()` 在数量不等时会补占位曲线。
 * 打头放饼干是为了接住按钮：进度 0 的那一帧和按钮画的完全一样。
 * 末尾再补一次饼干，是为了让最后一跳也落在饼干上，循环首尾相接不跳变。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val WakeUpIndicatorShapes: List<RoundedPolygon> =
    listOf(MaterialShapes.Cookie9Sided) +
        LoadingIndicatorDefaults.IndeterminateIndicatorPolygons +
        listOf(MaterialShapes.Cookie9Sided)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val WakeUpIndicatorMorphs: List<Morph> =
    WakeUpIndicatorShapes.zipWithNext { from, to -> Morph(from, to) }

/**
 * 变形一步用的弹簧。
 *
 * 可见阈值放大到 0.1：低阻尼弹簧本该有点回弹，阈值太小会拖出一条尾巴。
 * 刚度比 M3 默认（200）低：一步变形走得更缓，不容易看成“一直在切形状”。
 */
private val MORPH_SPRING = spring<Float>(
    dampingRatio = 0.6f,
    stiffness = 110f,
    visibilityThreshold = 0.1f,
)

/**
 * 每步那一段旋转的曲线。
 *
 * 必须**过阻尼到不回弹**（`dampingRatio = 1`）。旋转若复用形变那条低阻尼弹簧，角度会先冲过
 * 终点，下一步 `snapTo` 回到整步时再退回来——看起来就是"转过去了又倒一点"。
 * 形状变过去再回一点是柔软的，角度退一点就是纯倒退，所以这两件事不能用同一条曲线。
 * 刚度取 150：形变弹簧在第一个峰值附近就因为 `visibilityThreshold` 结束（约 0.4s），
 * 旋转收得稍晚一点，观感是"形状到位之后把这一下转完"，两条不会互相拖。
 */
private val TURN_SPRING = spring<Float>(
    dampingRatio = 1f,
    stiffness = 150f,
    visibilityThreshold = 0.5f,
)

/**
 * 归位时"收回饼干"那一步的弹簧。
 *
 * 比常规一步硬：用户已经等了一整轮失败，这里不宜再慢慢演，只要看起来是连续的就行。
 */
private val SETTLE_SPRING = spring<Float>(
    dampingRatio = 0.85f,
    stiffness = 190f,
    visibilityThreshold = 0.01f,
)

/**
 * 归位时那一步旋转的曲线。
 *
 * 同样不能回弹，而且要快：[TURN_SPRING] 转 80° 要 0.55s，归位前最多还会先走完手头一步
 * （0.55s），加起来会顶到 [INDICATOR_SETTLE_TIMEOUT_MILLIS] 的兜底时间。
 */
private val SETTLE_TURN_SPRING = spring<Float>(
    dampingRatio = 1f,
    stiffness = 260f,
    visibilityThreshold = 0.5f,
)

/**
 * 成功后从品牌粉染到 success 绿用的时长。
 *
 * 和收尾的形变同时开始。比形变略短一点：粉和绿之间是插值出来的褐色，那一段越短越好，
 * 但要能和形变对上，不能绿在形状还没收回来之前就到位。
 */
private const val SUCCESS_COLOR_MILLIS = 300

/** 勾的大小。饼干是 92dp，勾占一半左右，四周还能看见一圈绿底。 */
private val CHECK_SIZE = 46.dp

/** 勾入场时的起始缩放。不用 0：从 0 弹出来会像凭空冒出来，0.6 才看得出是"长出来"的。 */
private const val CHECK_START_SCALE = 0.6f

/**
 * 勾的入场弹簧。
 *
 * 阻尼 0.5 是刻意留一点过冲：这是整段流程里唯一一个"结果"信号，弹一下比匀速放大更像确认。
 */
private val CHECK_SPRING = spring<Float>(
    dampingRatio = 0.5f,
    stiffness = 420f,
    visibilityThreshold = 0.01f,
)

/** 勾出来之后停一下再离开这一页，让人看清"成了"。 */
private const val SUCCESS_HOLD_MILLIS = 420L

/**
 * 等指示器收回饼干的兜底上限。
 *
 * 正常情况 700ms 内就回调了；成功时还要多弹一个勾，最坏要 1.4s 左右（走完手头一步 0.55s、
 * 收尾 0.5s、勾 0.35s）。万一因为重组、重新进入页面之类的原因没回调，也不能把流程永远卡住——
 * 宁可突兀一下，也不能让用户面对一颗永远转着的饼干。
 */
private const val INDICATOR_SETTLE_TIMEOUT_MILLIS = 2200L

/** 箭头在按钮内部向右滑出的距离，滑到边界外就被 clip 裁掉。 */
private val ARROW_FLY = 56.dp

/** 提示文案上移的距离。 */
private val HINT_LIFT = 14.dp

/** 输入框淡出时糊到的最大半径。 */
private val FIELD_BLUR = 18.dp

/** 归位时输入框从上方落下的距离：只"微微"动一点，不要像掉下来。 */
private val RETURN_DROP = 20.dp

/** 归位时输入框起始的模糊半径，落位过程中收到 0。 */
private val RETURN_BLUR = 12.dp

/** 输入框飞到按钮时收缩到原尺寸的比例。 */
private const val FIELD_SHRINK = 0.55f

/**
 * 把主时间轴上的 `[start, end]` 区间映射成 0..1，区间外截断成 0 / 1。
 *
 * 这样多条动作共享一个 `Animatable`，加减动作只改区间，不用重排时长总和。
 */
private fun Float.segment(start: Float, end: Float): Float =
    ((this - start) / (end - start)).coerceIn(0f, 1f)

/** 离场用加速曲线：起步慢、越走越快，像是被"抽走"。 */
private fun Float.accelerate(): Float = FastOutLinearInEasing.transform(this)

/** 位移用减速曲线：起步快、稳稳停住。 */
private fun Float.decelerate(): Float = FastOutSlowInEasing.transform(this)

/**
 * 归位专用：二次 ease-out，一上来就有速度、越走越慢地贴到终点。
 *
 * 归位的元素是从"停住"的状态重新启动的，再用 ease-in-out（[decelerate]）会先慢一下再走，
 * 观感上像"顿一下才动"，所以这里换成单调减速。
 */
private fun Float.settle(): Float = 1f - (1f - this) * (1f - this)

/** 控件在 root 坐标系里的中心点。 */
private fun LayoutCoordinates.centerInRoot(): Offset {
    val origin = positionInRoot()
    return Offset(origin.x + size.width / 2f, origin.y + size.height / 2f)
}

