package com.star.schedule.feature.update.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

private class RollGlyph(val id: Long, val text: String) {
    val offset = Animatable(0f)
    val alpha = Animatable(1f)
    val blur = Animatable(0f)
    val shiftX = Animatable(0f)
    val shiftY = Animatable(0f)
    var x by mutableFloatStateOf(0f)
    var y by mutableFloatStateOf(0f)
    var job: Job? = null
    var shiftJob: Job? = null
}

// 退场的字停留在旧布局的坐标上，不再参与新排版。
private class GhostGlyph(val glyph: RollGlyph, val x: Float, val y: Float)

private class RollState {
    var lastText: String? = null
    var inFallback by mutableStateOf(false)
    var nextId = 0L
}

@Composable
internal fun RollingText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    horizontalBias: Float = 0f,
    fallbackMaxLines: Int = 1,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val rollStyle = style.copy(fontFeatureSettings = "tnum")
    val fontSize = if (style.fontSize.isUnspecified) 14.sp else style.fontSize
    val dyPx = with(density) { fontSize.toPx() * 0.5f }
    val maxBlurPx = with(density) { fontSize.toPx() * 0.5f }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val scope = rememberCoroutineScope()
    val state = remember { RollState() }
    val current = remember { mutableStateListOf<RollGlyph>() }
    val ghosts = remember { mutableStateListOf<GhostGlyph>() }
    val textAlign = when {
        horizontalBias < 0f -> TextAlign.Start
        horizontalBias > 0f -> TextAlign.End
        else -> TextAlign.Center
    }
    val measureStyle = rollStyle.copy(textAlign = textAlign)

    fun newGlyph(glyph: String) = RollGlyph(state.nextId++, glyph)

    suspend fun RollGlyph.enter(delayMillis: Long) {
        offset.snapTo(1f)
        alpha.snapTo(0f)
        blur.snapTo(1f)
        delay(delayMillis)
        coroutineScope {
            launch { offset.animateTo(0f, spatial) }
            launch { alpha.animateTo(1f, effects) }
            launch { blur.animateTo(0f, effects) }
        }
    }

    suspend fun RollGlyph.exit(delayMillis: Long) {
        delay(delayMillis)
        coroutineScope {
            launch { offset.animateTo(-1f, spatial) }
            launch { alpha.animateTo(0f, effects) }
            launch { blur.animateTo(1f, effects) }
        }
    }

    suspend fun RollGlyph.shiftBy(dx: Float, dy: Float) {
        shiftX.snapTo(shiftX.value + dx)
        shiftY.snapTo(shiftY.value + dy)
        coroutineScope {
            launch { shiftX.animateTo(0f, spatial) }
            launch { shiftY.animateTo(0f, spatial) }
        }
    }

    fun launchExit(exiting: List<GhostGlyph>) {
        if (exiting.isEmpty()) return
        ghosts.addAll(exiting)
        val delays = rollStaggerDelays(exiting.size)
        val exitJobs = exiting.mapIndexed { i, ghost ->
            ghost.glyph.job?.cancel()
            scope.launch { ghost.glyph.exit(delays[i]) }
                .also { ghost.glyph.job = it }
        }
        scope.launch {
            exitJobs.joinAll()
            ghosts.removeAll(exiting)
        }
    }

    BoxWithConstraints(
        modifier = modifier.clearAndSetSemantics {
            if (text.isNotEmpty()) {
                this.text = AnnotatedString(text)
            }
        },
        contentAlignment = BiasAlignment(horizontalBias, 0f),
    ) {
        // 固定到可用宽度测量，逐字坐标才能直接当作渲染坐标；
        // 对齐交给排版本身，不再由宽度差反推偏移。
        fun measureLayout(value: String): TextLayoutResult = measurer.measure(
            text = AnnotatedString(value),
            style = measureStyle,
            overflow = TextOverflow.Clip,
            softWrap = true,
            maxLines = fallbackMaxLines,
            constraints = if (constraints.hasBoundedWidth) {
                Constraints.fixedWidth(constraints.maxWidth)
            } else {
                Constraints()
            },
        )

        fun TextLayoutResult.overflowed(): Boolean = didOverflowWidth || didOverflowHeight

        fun glyphBoxes(layout: TextLayoutResult, value: String): List<Rect> {
            val boxes = ArrayList<Rect>()
            var utf16 = 0
            for (cp in value.codePoints().toArray()) {
                boxes += layout.getBoundingBox(utf16)
                utf16 += Character.charCount(cp)
            }
            return boxes
        }

        val layout = measureLayout(text)
        val overflowed = layout.overflowed()

        if (state.lastText == null) {
            state.lastText = text
            if (overflowed) {
                state.inFallback = true
            } else {
                val boxes = glyphBoxes(layout, text)
                text.rollGlyphs().forEachIndexed { i, g ->
                    current.add(newGlyph(g).apply {
                        x = boxes[i].left
                        y = boxes[i].top
                    })
                }
            }
        }

        // key 带宽度：文本没变但宽度变化时也要把保留字平移到新排版位置。
        // 宽度变化时 oldList == current，plan 全部命中保留，只剩 shift 动画。
        LaunchedEffect(text, constraints.maxWidth) {
            if (overflowed) {
                ghosts.clear()
                current.clear()
                state.inFallback = true
                state.lastText = text
                return@LaunchedEffect
            }
            val newParts = text.rollGlyphs()
            val newBoxes = glyphBoxes(layout, text)
            val oldList = if (state.inFallback || state.lastText == null) {
                emptyList()
            } else {
                current.toList()
            }
            // 字的 x/y 就是它在旧排版里的位置，不必再测量旧文本。
            val oldPos = oldList.map { it.x to it.y }
            val oldParts = oldList.map { it.text }
            val plan = planTextRoll(oldParts, newParts)
            val keptByNew = HashMap<Int, Int>()
            val keptOld = HashSet<Int>()
            plan.kept.forEach { (oldI, newI) ->
                keptByNew[newI] = oldI
                keptOld += oldI
            }
            launchExit(
                oldList.mapIndexedNotNull { i, glyph ->
                    if (i in keptOld) {
                        null
                    } else {
                        GhostGlyph(glyph, oldPos[i].first, oldPos[i].second)
                    }
                },
            )
            val entering = ArrayList<RollGlyph>()
            current.clear()
            newParts.forEachIndexed { newI, g ->
                val box = newBoxes[newI]
                val oldI = keptByNew[newI]
                val glyph = if (oldI != null) {
                    oldList[oldI]
                } else {
                    newGlyph(g).also(entering::add)
                }
                glyph.x = box.left
                glyph.y = box.top
                current.add(glyph)
            }
            val delaysIn = rollStaggerDelays(entering.size)
            entering.forEachIndexed { i, glyph ->
                glyph.job = scope.launch { glyph.enter(delaysIn[i]) }
            }
            // 保留的字可能还在入场途中，平移单独起协程，不能打断入场动画。
            // 跨行时 y 也会变，所以位移要按二维算。
            plan.kept.forEach { (oldI, newI) ->
                val glyph = oldList[oldI]
                glyph.shiftJob?.cancel()
                glyph.shiftJob = scope.launch {
                    glyph.shiftBy(
                        oldPos[oldI].first - newBoxes[newI].left,
                        oldPos[oldI].second - newBoxes[newI].top,
                    )
                }
            }
            state.inFallback = false
            state.lastText = text
        }

        if (!state.inFallback) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { layout.size.height.toDp() }),
            ) {
                ghosts.forEach { ghost ->
                    GlyphText(
                        glyph = ghost.glyph,
                        x = ghost.x,
                        y = ghost.y,
                        style = rollStyle,
                        color = color,
                        dyPx = dyPx,
                        maxBlurPx = maxBlurPx,
                    )
                }
                current.forEach { glyph ->
                    GlyphText(
                        glyph = glyph,
                        x = glyph.x,
                        y = glyph.y,
                        style = rollStyle,
                        color = color,
                        dyPx = dyPx,
                        maxBlurPx = maxBlurPx,
                    )
                }
            }
        } else {
            Text(
                text = text,
                style = rollStyle,
                color = color,
                maxLines = fallbackMaxLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = textAlign,
            )
        }
    }
}

@Composable
private fun GlyphText(
    glyph: RollGlyph,
    x: Float,
    y: Float,
    style: TextStyle,
    color: Color,
    dyPx: Float,
    maxBlurPx: Float,
) {
    Text(
        text = glyph.text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.graphicsLayer {
            translationX = x + glyph.shiftX.value
            translationY = y + glyph.shiftY.value + glyph.offset.value * dyPx
            alpha = glyph.alpha.value
            val blur = glyph.blur.value.coerceIn(0f, 1f)
            renderEffect = if (blur > 0.01f) {
                BlurEffect(blur * maxBlurPx, blur * maxBlurPx, TileMode.Decal)
            } else {
                null
            }
        },
    )
}
