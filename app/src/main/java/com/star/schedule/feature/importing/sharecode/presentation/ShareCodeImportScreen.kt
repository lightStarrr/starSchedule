package com.star.schedule.feature.importing.sharecode.presentation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.star.schedule.R
import com.star.schedule.core.designsystem.component.CircleBackButton
import com.star.schedule.core.designsystem.component.CookieActionButton
import com.star.schedule.core.designsystem.component.CookieCycleShapes
import com.star.schedule.core.designsystem.component.pickDifferentCookieShapeIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * 各家口令导入页的品牌差异：顶栏图标、整页配色、输入框提示语。
 *
 * [colorScheme] 以宿主主题为底换成品牌配色，由各家自己的配色函数提供。
 */
data class ShareCodeImportBrand(
    @param:DrawableRes val logoRes: Int,
    val colorScheme: @Composable (ColorScheme) -> ColorScheme,
    @param:StringRes val hintRes: Int,
)

@Composable
fun ShareCodeImportRoute(
    viewModel: ShareCodeImportViewModel,
    brand: ShareCodeImportBrand,
    onBack: () -> Unit,
    onImported: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ShareCodeImportScreen(
        state = state,
        brand = brand,
        onBack = onBack,
        onInputChange = viewModel::onInputChange,
        onImport = viewModel::import,
        // 成功不再一到就把页面撤掉：先让指示器原地收回饼干、染成 success 色、弹一个勾，
        // 停一下再交给外面（那个回调会 finish 这个 Activity）。
        onImported = onImported,
    )
}

/**
 * 口令导入页：顶栏、输入框、按钮，三者之外暂时不放任何内容。
 *
 * 错误不单独占一行：它顶替输入框下面那句提示语，用户一改输入就换回原文案。
 * 整页套一层品牌自己的配色，只影响这一页。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShareCodeImportScreen(
    state: ShareCodeImportUiState,
    brand: ShareCodeImportBrand,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onImport: () -> Unit,
    onImported: () -> Unit,
) {
    // 用 MaterialExpressiveTheme 而不是 MaterialTheme：后者会把 motionScheme 重置成默认，
    // 这里的形状/颜色动画虽然是显式 tween，但没必要让整页的运动规格跟主题脱节。
    MaterialExpressiveTheme(colorScheme = brand.colorScheme(MaterialTheme.colorScheme)) {
        ShareCodeImportContent(
            state = state,
            brand = brand,
            onBack = onBack,
            onInputChange = onInputChange,
            onImport = onImport,
            onImported = onImported,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShareCodeImportContent(
    state: ShareCodeImportUiState,
    brand: ShareCodeImportBrand,
    onBack: () -> Unit,
    onInputChange: (String) -> Unit,
    onImport: () -> Unit,
    onImported: () -> Unit,
) {
    val submitting = state.phase is ShareCodeImportPhase.Importing
    // 成功不走归位：按钮留在屏幕中间，就地收回饼干、染成 success 色、弹一个勾。
    val succeeding = state.phase is ShareCodeImportPhase.Finished

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
    var message by remember { mutableStateOf<ShareCodeImportError?>(null) }
    val failure = (state.phase as? ShareCodeImportPhase.Failed)?.error
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
                    hint = stringResource(brand.hintRes),
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
                    CookieActionButton(
                        // 归位途中按钮还在往下飞，这时候点它会把两套编排撞在一起，先不理。
                        onClick = { if (!returning) onImport() },
                        actionContentDescription = stringResource(R.string.action_import),
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

            // 顶栏中间放品牌自己的图标：这一页的配色也是从它取的，一眼能看出是从哪导入。
            // 只有返回键时顶栏是空的，中间空着反而像是没画完。
            Image(
                painter = painterResource(brand.logoRes),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    // 和返回键（48dp 触摸区、12dp 外边距）中心对齐，不是简单贴同一个上边距。
                    .padding(top = BRAND_LOGO_TOP_PADDING)
                    .size(BRAND_LOGO_SIZE),
            )

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
    hint: String,
    errorText: String?,
    hintShift: Float,
    hintAlpha: Float,
    modifier: Modifier = Modifier,
) {

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
 * 提示位换字的时长。
 *
 * 320ms 够让人看出是「一行字换成了另一行」，而不是硬切；再长就会拖到用户已经重新打字了还没换完。
 */
private const val MESSAGE_SWAP_MILLIS = 320

/** 换字时糊到的最大半径。够看出是"化开"，又不至于让 12sp 的字糊成一团。 */
private val MESSAGE_BLUR = 5.dp

/** 每个失败类别对应一句文案；放 UI 层是因为 ViewModel 不持有 Context。 */
@StringRes
private fun ShareCodeImportError.messageRes(): Int = when (this) {
    ShareCodeImportError.EmptyCode -> R.string.share_code_import_error_empty
    ShareCodeImportError.InvalidCode -> R.string.share_code_import_error_invalid
    ShareCodeImportError.Network -> R.string.share_code_import_error_network
    ShareCodeImportError.Rejected -> R.string.share_code_import_error_rejected
    ShareCodeImportError.Unsupported -> R.string.share_code_import_error_unsupported
    ShareCodeImportError.Busy -> R.string.share_code_import_error_busy
}

/**
 * 点击提交后整段出场动画的总时长。
 *
 * ViewModel 用同一个常量来决定「什么时候才真的发请求」——编排播完、按钮已经变成加载指示器
 * 之后才发，所以口令错误也不会把这段动画打断。
 */
private val SUBMIT_MILLIS = SHARE_CODE_SUBMIT_ANIMATION_MILLIS.toInt()

/** 归位（失败回滚）整段编排的时长。比去程短一点：回来这件事不需要慢慢演。 */
private const val RETURN_MILLIS = 720

/** 顶栏中间的品牌图标大小。和返回键的 40dp 圆形容器接近，不会显得比它更重。 */
private val BRAND_LOGO_SIZE = 32.dp

/**
 * 图标的顶边留白。
 *
 * 返回键是「12dp 外边距 + 48dp 触摸区」，图标只有 32dp，贴同一个 12dp 会让两者中心差 8dp；
 * 补一半的差值（8dp）之后图标中心和返回键中心齐平。
 */
private val BRAND_LOGO_TOP_PADDING = 20.dp

/** 去程时间轴上「按钮已经到位」的位置，从这里起按钮开始换形状。 */
private const val LOADING_START = 0.86f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val CycleShapes: List<RoundedPolygon> = CookieCycleShapes

internal fun pickDifferentShapeIndex(current: Int, shapeCount: Int, random: Random): Int =
    pickDifferentCookieShapeIndex(current, shapeCount, random)

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

