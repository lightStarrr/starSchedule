package com.star.schedule.feature.update.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    var job: Job? = null
    var shiftJob: Job? = null
}

private class GhostLine(
    val glyphs: List<RollGlyph>,
    val exiting: Set<Long>,
)

private class RollState {
    var lastText: String? = null
    var inFallback = false
    var nextId = 0L
}

@Composable
internal fun RollingText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val rollStyle = style.copy(fontFeatureSettings = "tnum")
    val fontSize = if (style.fontSize.isUnspecified) 14.sp else style.fontSize
    val dyPx = with(density) { fontSize.toPx() * 0.5f }
    val maxBlurPx = with(density) { 8.dp.toPx() }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val scope = rememberCoroutineScope()
    val state = remember { RollState() }
    val current = remember { mutableStateListOf<RollGlyph>() }
    val ghosts = remember { mutableStateListOf<GhostLine>() }

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

    suspend fun RollGlyph.shiftBy(dx: Float) {
        shiftX.snapTo(shiftX.value + dx)
        shiftX.animateTo(0f, spatial)
    }

    fun launchExitLine(glyphs: List<RollGlyph>, exitingIds: Set<Long>) {
        if (glyphs.isEmpty() || exitingIds.isEmpty()) return
        val line = GhostLine(glyphs, exitingIds)
        ghosts.add(line)
        val delays = rollStaggerDelays(exitingIds.size)
        val exitJobs = glyphs
            .filter { it.id in exitingIds }
            .mapIndexed { i, glyph ->
                glyph.job?.cancel()
                scope.launch { glyph.exit(delays[i]) }
                    .also { glyph.job = it }
            }
        scope.launch {
            exitJobs.joinAll()
            ghosts.remove(line)
        }
    }

    BoxWithConstraints(
        modifier = modifier.clearAndSetSemantics {
            this.text = AnnotatedString(text)
        },
        contentAlignment = Alignment.Center,
    ) {
        val fits = measurer.measure(
            AnnotatedString(text),
            rollStyle,
            maxLines = 1,
            softWrap = false,
        ).size.width <= constraints.maxWidth

        if (state.lastText == null) {
            state.lastText = text
            if (fits) {
                text.rollGlyphs().forEach { current.add(newGlyph(it)) }
            } else {
                state.inFallback = true
            }
        }

        LaunchedEffect(text) {
            if (text == state.lastText) return@LaunchedEffect
            if (!fits) {
                ghosts.clear()
                current.clear()
                state.inFallback = true
                state.lastText = text
                return@LaunchedEffect
            }
            val oldList = if (state.inFallback) {
                emptyList()
            } else {
                current.toList()
            }
            val oldText = state.lastText.orEmpty()
            val newParts = text.rollGlyphs()
            val plan = planTextRoll(oldList.map { it.text }, newParts)
            val oldW = measurer.measure(
                AnnotatedString(oldText),
                rollStyle,
                maxLines = 1,
                softWrap = false,
            ).size.width.toFloat()
            val newW = measurer.measure(
                AnnotatedString(text),
                rollStyle,
                maxLines = 1,
                softWrap = false,
            ).size.width.toFloat()

            if (plan.wholeChange) {
                launchExitLine(
                    glyphs = oldList,
                    exitingIds = oldList.mapTo(HashSet()) { it.id },
                )
                current.clear()
                newParts.forEach { current.add(newGlyph(it)) }
                val delays = rollStaggerDelays(current.size)
                current.forEachIndexed { i, glyph ->
                    glyph.job = scope.launch { glyph.enter(delays[i]) }
                }
            } else {
                val prefix = plan.prefixLength
                val suffix = plan.suffixLength
                val exiting = oldList.subList(prefix, oldList.size - suffix)
                launchExitLine(
                    glyphs = oldList,
                    exitingIds = exiting.mapTo(HashSet()) { it.id },
                )
                val middle = newParts
                    .subList(prefix, newParts.size - suffix)
                    .map { newGlyph(it) }
                current.clear()
                current.addAll(
                    oldList.subList(0, prefix) +
                        middle +
                        oldList.subList(oldList.size - suffix, oldList.size),
                )
                val delaysIn = rollStaggerDelays(middle.size)
                middle.forEachIndexed { i, glyph ->
                    glyph.job = scope.launch { glyph.enter(delaysIn[i]) }
                }
                // 保留的字可能还在入场途中，平移单独起协程，不能打断入场动画。
                oldList.subList(0, prefix).forEach { glyph ->
                    glyph.shiftJob?.cancel()
                    glyph.shiftJob = scope.launch {
                        glyph.shiftBy((newW - oldW) / 2f)
                    }
                }
                oldList.subList(oldList.size - suffix, oldList.size)
                    .forEach { glyph ->
                        glyph.shiftJob?.cancel()
                        glyph.shiftJob = scope.launch {
                            glyph.shiftBy((oldW - newW) / 2f)
                        }
                    }
            }
            state.inFallback = false
            state.lastText = text
        }

        if (fits) {
            ghosts.forEach { line ->
                Row {
                    line.glyphs.forEach { glyph ->
                        GlyphText(
                            glyph = glyph,
                            visible = glyph.id in line.exiting,
                            style = rollStyle,
                            color = color,
                            dyPx = dyPx,
                            maxBlurPx = maxBlurPx,
                        )
                    }
                }
            }
            Row {
                current.forEach { glyph ->
                    GlyphText(
                        glyph = glyph,
                        visible = true,
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GlyphText(
    glyph: RollGlyph,
    visible: Boolean,
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
            translationX = glyph.shiftX.value
            translationY = glyph.offset.value * dyPx
            alpha = if (visible) glyph.alpha.value else 0f
            val blur = glyph.blur.value.coerceIn(0f, 1f)
            renderEffect = if (blur > 0.01f) {
                BlurEffect(blur * maxBlurPx, blur * maxBlurPx, TileMode.Decal)
            } else {
                null
            }
        },
    )
}
