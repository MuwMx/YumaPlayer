/*
 * YumaPlayer (2026) | Original work by MuwMix
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.ui.component.smoothFadingEdge
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private const val MIN_SCROLL_DELTA_PX = 2f
private const val MAX_SCROLL_STAGGER_REL = 25

class YumaLyricsScrollState {
    var isAtTop by mutableStateOf(true)
        internal set
}

val LocalYumaLyricsScrollState = staticCompositionLocalOf<YumaLyricsScrollState?> { null }

private class ScrollAnim(
    val startNanos: Long,
    val deltaPx: Float,
    val targetIndex: Int,
    val maxLineDurSec: Double,
    var dampStartNanos: Long = 0L,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YumaKaraokeView(
    document: YumaLyricsDocument,
    positionMsProvider: () -> Long,
    modifier: Modifier = Modifier,
    style: YumaLyricsStyle = YumaLyricsStyle(),
    autoScrollEnabled: Boolean = true,
    resumeAutoscrollLabel: String = "Resume Autoscroll",
    writtenByFormat: String = "Written by %s",
    @Suppress("UNUSED_PARAMETER") listState: LazyListState? = null,
    onSeek: (Long) -> Unit = {},
    onLineLongPress: ((YumaLyricsLine) -> Unit)? = null,
) {
    val lines = document.lines
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val textMeasurer = rememberTextMeasurer(cacheSize = 0)

    val tokens = style.tokens
    val timing = tokens.timing
    val motion = tokens.motion
    val visual = tokens.visual

    var userScrollUntilMs by remember(document) { mutableLongStateOf(0L) }
    val isAutoscrollPaused by remember(document) {
        derivedStateOf { userScrollUntilMs > 0L }
    }

    LaunchedEffect(userScrollUntilMs) {
        if (userScrollUntilMs <= 0L) return@LaunchedEffect
        val remaining = userScrollUntilMs - System.currentTimeMillis()
        if (remaining > 0L) {
            delay(remaining.milliseconds)
        }
        userScrollUntilMs = 0L
    }

    val activeLineIndex by remember(lines) {
        derivedStateOf { findActiveLineIndex(lines, positionMsProvider()) }
    }
    val creditsFocused by remember(lines, timing) {
        derivedStateOf {
            val lastSung = lines.indexOfLast { !it.isInstrumental }
            lastSung >= 0 && positionMsProvider() + timing.scrollOffsetMs >= lines[lastSung].endTimeMs
        }
    }

    val scrollAnims = remember(document) { ArrayList<ScrollAnim>() }
    val scrollAnimVersion = remember(document) { mutableIntStateOf(0) }
    val animFrameState = remember(document) { mutableLongStateOf(0L) }
    var lastAlphaChangeNanos by remember(document) { mutableLongStateOf(System.nanoTime()) }
    val easeEasing = remember(motion.ease) {
        Easing { x -> motion.ease.at(x) }
    }

    LaunchedEffect(document, activeLineIndex, isAutoscrollPaused) {
        lastAlphaChangeNanos = System.nanoTime()
    }

    LaunchedEffect(document) {
        val minRunSec = motion.opacityTransitionSecs.toDouble() + 0.1
        while (true) {
            val nowCheck = System.nanoTime()
            val elapsedAlpha = (nowCheck - lastAlphaChangeNanos) / 1_000_000_000.0
            if (scrollAnims.isEmpty() && elapsedAlpha >= minRunSec) {
                snapshotFlow {
                    Triple(activeLineIndex, isAutoscrollPaused, scrollAnimVersion.intValue)
                }.drop(1).first()
                lastAlphaChangeNanos = System.nanoTime()
            }
            withFrameNanos { }
            val nowNanos = System.nanoTime()
            animFrameState.longValue = nowNanos
            pruneExpiredScrollAnims(scrollAnims, nowNanos, motion.userScrollDampSec.toDouble())
        }
    }

    val nestedScrollConnection = remember(document, timing.userScrollResumeSec) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    userScrollUntilMs = System.currentTimeMillis() + (timing.userScrollResumeSec * 1000f).toLong()
                    if (scrollAnims.isNotEmpty()) {
                        val nowNanos = System.nanoTime()
                        for (anim in scrollAnims) {
                            if (anim.dampStartNanos == 0L) {
                                anim.dampStartNanos = nowNanos
                            }
                        }
                        scrollAnimVersion.intValue++
                    }
                }
                return Offset.Zero
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection),
    ) {
        val viewportWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(40f)
        val viewportHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val horizontalPadPx = with(density) { visual.horizontalPaddingDp.dp.toPx() }
        val lineFullWidthPx = (viewportWidthPx - horizontalPadPx * 2f).coerceAtLeast(40f)
        val anchorOffsetPx = (viewportHeightPx * visual.anchorRatio).roundToInt()
        val bottomPaddingDp = maxHeight * (1f - visual.anchorRatio)

        val preparedLines = remember(lines, lineFullWidthPx, style, layoutDirection) {
            lines.mapIndexed { index, line ->
                prepareLyricsLine(
                    line = line,
                    lineIndex = index,
                    lines = lines,
                    fullWidthPx = lineFullWidthPx,
                    style = style,
                    textMeasurer = textMeasurer,
                    density = density,
                    layoutDirection = layoutDirection,
                )
            }
        }

        val minPadTopPx = with(density) { visual.listTopPaddingDp.dp.toPx() }
        val padBottomPx = with(density) { bottomPaddingDp.toPx() }
        val minLineHeightPx = with(density) { 1.dp.toPx() }
        val cullMarginPx = with(density) { 40.dp.toPx() }

        val lineHeights = remember(preparedLines, minLineHeightPx) {
            FloatArray(preparedLines.size) { i ->
                max(preparedLines[i].totalHeightPx, minLineHeightPx)
            }
        }
        val firstLineHeight = lineHeights.firstOrNull() ?: 0f
        val padTopPx = max(anchorOffsetPx - firstLineHeight * 0.5f, minPadTopPx)

        val lineTops = remember(lineHeights, padTopPx) {
            FloatArray(lineHeights.size).also { tops ->
                var y = padTopPx
                for (i in lineHeights.indices) {
                    tops[i] = y
                    y += lineHeights[i]
                }
            }
        }
        val linesEndTop = if (lineTops.isEmpty()) padTopPx else lineTops.last() + lineHeights.last()

        val writersText = remember(document.songwriters, writtenByFormat) {
            if (document.songwriters.isEmpty()) {
                ""
            } else {
                writtenByFormat.format(document.songwriters.joinToString(", "))
            }
        }
        val footerStyle = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = style.fontFamily,
            fontSize = (visual.footerFontSizeSp * style.textScale).sp,
        )
        val footerVerticalPadPx = with(density) { visual.footerVerticalPaddingDp.dp.toPx() }
        val footerHeightPx = remember(writersText, footerStyle, lineFullWidthPx) {
            if (writersText.isEmpty()) {
                0f
            } else {
                textMeasurer.measure(
                    text = writersText,
                    style = footerStyle,
                    constraints = Constraints(maxWidth = lineFullWidthPx.roundToInt().coerceAtLeast(1)),
                ).size.height.toFloat() + footerVerticalPadPx * 2f
            }
        }
        val totalContentHeight = linesEndTop + footerHeightPx + padBottomPx
        val maxScrollTop = (totalContentHeight - viewportHeightPx).coerceAtLeast(0f)

        val scrollStateBridge = LocalYumaLyricsScrollState.current
        var scrollTop by remember(document) { mutableFloatStateOf(0f) }
        var hasInitialScroll by remember(document) { mutableStateOf(false) }
        var lastScrollActive by remember(document) { mutableStateOf(emptyList<Int>()) }
        var prevPosMs by remember(document) { mutableLongStateOf(Long.MIN_VALUE) }
        var wasUserScroll by remember(document) { mutableStateOf(false) }

        val isAtTop by remember { derivedStateOf { scrollTop <= 0.5f } }
        LaunchedEffect(scrollStateBridge, isAtTop) {
            scrollStateBridge?.isAtTop = isAtTop
        }
        DisposableEffect(scrollStateBridge) {
            onDispose { scrollStateBridge?.isAtTop = true }
        }

        LaunchedEffect(document, isAutoscrollPaused) {
            if (isAutoscrollPaused) {
                wasUserScroll = true
            }
        }

        LaunchedEffect(maxScrollTop) {
            if (scrollTop > maxScrollTop) {
                scrollTop = maxScrollTop
            }
        }

        val scrollableState = rememberScrollableState { delta ->
            if (abs(delta) > 0f) {
                hasInitialScroll = true
            }
            val old = scrollTop
            val next = (old - delta).coerceIn(0f, maxScrollTop)
            scrollTop = next
            scrollStateBridge?.isAtTop = next <= 0.5f
            old - next
        }

        fun applyScrollTop(desiredScrollTop: Float, targetIndex: Int) {
            if (lines.isEmpty()) return
            val clampedIndex = targetIndex.coerceIn(0, lines.lastIndex)
            if (!hasInitialScroll) {
                scrollTop = desiredScrollTop
                hasInitialScroll = true
                return
            }
            val delta = desiredScrollTop - scrollTop
            if (abs(delta) > MIN_SCROLL_DELTA_PX) {
                scrollTop = desiredScrollTop
                scrollAnims.add(
                    ScrollAnim(
                        startNanos = System.nanoTime(),
                        deltaPx = delta,
                        targetIndex = clampedIndex,
                        maxLineDurSec = maxScrollDurationSec(delta, motion.scrollBaseSecs.toDouble()),
                    ),
                )
                scrollAnimVersion.intValue++
            }
        }

        LaunchedEffect(document, autoScrollEnabled, isAutoscrollPaused, anchorOffsetPx, maxScrollTop, motion.scrollBaseSecs) {
            if (!autoScrollEnabled || isAutoscrollPaused || lines.isEmpty()) return@LaunchedEffect
            snapshotFlow { positionMsProvider() }
                .collect { posMs ->
                    val jumped = prevPosMs != Long.MIN_VALUE && abs(posMs - prevPosMs) > timing.timeJumpMs
                    prevPosMs = posMs
                    val exitedUser = wasUserScroll
                    val eval = evaluateScrollTrigger(
                        lines = lines,
                        positionMs = posMs,
                        lastScrollActive = lastScrollActive,
                        timing = timing,
                        jumped = jumped,
                        exitedUserScroll = exitedUser,
                        hasInitialScroll = hasInitialScroll,
                    )
                    if (exitedUser) wasUserScroll = false
                    if (eval.shouldScroll) {
                        val active = eval.activeIndices.ifEmpty { listOf(0) }
                        val desiredScrollTop = computeActiveLinesScrollTop(
                            lines = lines,
                            activeIndices = active,
                            scrollTimeMs = posMs + timing.scrollOffsetMs,
                            lineTops = lineTops,
                            lineHeights = lineHeights,
                            anchorOffsetPx = anchorOffsetPx.toFloat(),
                            viewportHeightPx = viewportHeightPx,
                            maxScrollTop = maxScrollTop,
                            endingThresholdMs = timing.endingThresholdMs,
                        )
                        applyScrollTop(desiredScrollTop, targetIndex = active.last())
                        lastScrollActive = active
                    }
                }
        }

        val visibleLineRange by remember(
            lineTops,
            lineHeights,
            viewportHeightPx,
            cullMarginPx,
            motion,
        ) {
            derivedStateOf {
                if (lineTops.isEmpty()) {
                    IntRange.EMPTY
                } else {
                    scrollAnimVersion.intValue
                    val frameNanos = animFrameState.longValue
                    val nowNanos = if (frameNanos != 0L) frameNanos else System.nanoTime()
                    val currentScroll = scrollTop
                    val minVisY = -cullMarginPx
                    val maxVisY = viewportHeightPx + cullMarginPx
                    var first = -1
                    var last = -1
                    for (i in lineTops.indices) {
                        val dy = if (scrollAnims.isEmpty()) {
                            0f
                        } else {
                            computeStaggerDy(i, nowNanos, scrollAnims, motion)
                        }
                        val visualTop = lineTops[i] - currentScroll + dy
                        val visualBottom = visualTop + lineHeights[i]
                        if (visualBottom >= minVisY && visualTop <= maxVisY) {
                            if (first == -1) first = i
                            last = i
                        }
                    }
                    if (first == -1) IntRange.EMPTY else first..last
                }
            }
        }

        val isFooterVisible by remember(
            writersText,
            linesEndTop,
            footerHeightPx,
            viewportHeightPx,
            cullMarginPx,
            preparedLines.size,
            motion,
        ) {
            derivedStateOf {
                if (writersText.isEmpty()) {
                    false
                } else {
                    scrollAnimVersion.intValue
                    val frameNanos = animFrameState.longValue
                    val nowNanos = if (frameNanos != 0L) frameNanos else System.nanoTime()
                    val dy = if (scrollAnims.isEmpty()) {
                        0f
                    } else {
                        computeStaggerDy(preparedLines.size, nowNanos, scrollAnims, motion)
                    }
                    val visualTop = linesEndTop - scrollTop + dy
                    val visualBottom = visualTop + footerHeightPx
                    visualBottom >= -cullMarginPx && visualTop <= viewportHeightPx + cullMarginPx
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .scrollable(
                    state = scrollableState,
                    orientation = Orientation.Vertical,
                    flingBehavior = ScrollableDefaults.flingBehavior(),
                )
                .smoothFadingEdge(top = visual.topEdgeFadeDp.dp, bottom = visual.bottomEdgeFadeDp.dp),
        ) {
            for (index in visibleLineRange) {
                val prepared = preparedLines[index]
                val line = lines[index]
                key("${line.timeMs}_$index") {
                    val nextLineStartSec = lines.getOrNull(index + 1)?.let { it.timeMs / 1000.0 } ?: Double.POSITIVE_INFINITY
                    val isLineActive = index == activeLineIndex
                    val distance = if (activeLineIndex >= 0) abs(index - activeLineIndex) else index + 1
                    val distStyle = when {
                        isLineActive -> LineDistanceStyle(alpha = visual.activeAlpha, blurFraction = 0f)
                        activeLineIndex >= 0 && index < activeLineIndex -> ladderStyle(visual.past, distance)
                        else -> ladderStyle(visual.upcoming, distance)
                    }
                    val targetBlurDp = if (style.blurEnabled && !isLineActive && !isAutoscrollPaused) {
                        style.maxBlurRadiusDp * distStyle.blurFraction
                    } else {
                        0f
                    }
                    val animatedBlurDp by animateFloatAsState(
                        targetValue = targetBlurDp,
                        animationSpec = tween(
                            durationMillis = (motion.blurTransitionSecs * 1000f).roundToInt(),
                            easing = easeEasing,
                        ),
                        label = "yumaLineBlur",
                    )

                    val transformOrigin = when (prepared.resolvedAlignment) {
                        YumaLyricsAlignment.Center -> TransformOrigin(0.5f, 0.5f)
                        YumaLyricsAlignment.End -> TransformOrigin(1f, 0.5f)
                        else -> TransformOrigin(0f, 0.5f)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scrollAnimVersion.intValue
                                val frameNanos = animFrameState.longValue
                                val nowNanos = if (frameNanos != 0L) frameNanos else System.nanoTime()
                                val posMs = positionMsProvider()
                                val tSec = posMs / 1000.0
                                val lineStartSec = prepared.startSec
                                val lineEndSec = max(nextLineStartSec, prepared.startSec + prepared.durSec + timing.lineEndTailSec)
                                val inLine = tSec in lineStartSec..<lineEndSec

                                val scale = when {
                                    inLine -> {
                                        val p = ((tSec - lineStartSec) / motion.scaleSecs.coerceAtLeast(0.001f))
                                            .toFloat()
                                            .coerceIn(0f, 1f)
                                        lerpFloat(motion.restScale, motion.activeScale, motion.ease.at(p))
                                    }
                                    tSec >= lineEndSec -> {
                                        val p = ((tSec - lineEndSec) / motion.scaleSecs.coerceAtLeast(0.001f))
                                            .toFloat()
                                            .coerceIn(0f, 1f)
                                        lerpFloat(motion.activeScale, motion.restScale, motion.ease.at(p))
                                    }
                                    else -> motion.restScale
                                }

                                scaleX = scale
                                scaleY = scale
                                translationY = (lineTops[index] - scrollTop) +
                                    computeStaggerDy(index, nowNanos, scrollAnims, motion)
                                this.transformOrigin = transformOrigin
                            }
                            .clip(MaterialTheme.shapes.medium)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = !line.isInstrumental,
                                onClick = {
                                    if (!line.isInstrumental && line.timeMs >= 0L) {
                                        userScrollUntilMs = 0L
                                        onSeek(line.timeMs)
                                    }
                                },
                                onLongClick = onLineLongPress?.let { handler -> { handler(line) } },
                            )
                            .padding(horizontal = visual.horizontalPaddingDp.dp)
                            .then(
                                if (animatedBlurDp > 0.25f) {
                                    Modifier.blur(animatedBlurDp.dp)
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        YumaLyricsLineCanvas(
                            prepared = prepared,
                            nextLineStartSec = nextLineStartSec,
                            positionMsProvider = positionMsProvider,
                            lineAlphaProvider = {
                                if (isAutoscrollPaused) 1f else distStyle.alpha
                            },
                            style = style,
                            animFrameProvider = { animFrameState.longValue },
                        )
                    }
                }
            }

            if (isFooterVisible) {
                val creditsAlpha = if (creditsFocused) visual.creditsAlphaFocused else visual.creditsAlphaRest
                Text(
                    text = writersText,
                    style = footerStyle,
                    color = style.textColor.copy(alpha = creditsAlpha.coerceIn(0f, 1f)),
                    textAlign = when (style.alignment) {
                        YumaLyricsAlignment.Center -> TextAlign.Center
                        YumaLyricsAlignment.End -> TextAlign.End
                        else -> TextAlign.Start
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scrollAnimVersion.intValue
                            val frameNanos = animFrameState.longValue
                            val nowNanos = if (frameNanos != 0L) frameNanos else System.nanoTime()
                            translationY = (linesEndTop - scrollTop) +
                                computeStaggerDy(preparedLines.size, nowNanos, scrollAnims, motion)
                        }
                        .padding(horizontal = visual.horizontalPaddingDp.dp, vertical = visual.footerVerticalPaddingDp.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = autoScrollEnabled && isAutoscrollPaused && lines.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        ) {
            Surface(
                onClick = {
                    userScrollUntilMs = 0L
                    wasUserScroll = false
                    val posMs = positionMsProvider()
                    val eval = evaluateScrollTrigger(
                        lines = lines,
                        positionMs = posMs,
                        lastScrollActive = lastScrollActive,
                        timing = timing,
                        exitedUserScroll = true,
                        hasInitialScroll = hasInitialScroll,
                    )
                    val active = eval.activeIndices.ifEmpty { listOf(0) }
                    val desired = computeActiveLinesScrollTop(
                        lines = lines,
                        activeIndices = active,
                        scrollTimeMs = posMs + timing.scrollOffsetMs,
                        lineTops = lineTops,
                        lineHeights = lineHeights,
                        anchorOffsetPx = anchorOffsetPx.toFloat(),
                        viewportHeightPx = viewportHeightPx,
                        maxScrollTop = maxScrollTop,
                        endingThresholdMs = timing.endingThresholdMs,
                    )
                    applyScrollTop(desired, targetIndex = active.last())
                    lastScrollActive = active
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 6.dp,
            ) {
                Text(
                    text = resumeAutoscrollLabel,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

private fun pruneExpiredScrollAnims(
    scrollAnims: MutableList<ScrollAnim>,
    nowNanos: Long,
    userScrollDampSec: Double,
) {
    if (scrollAnims.isEmpty()) return
    for (i in scrollAnims.lastIndex downTo 0) {
        val a = scrollAnims[i]
        val expired = if (a.dampStartNanos > 0L) {
            (nowNanos - a.dampStartNanos) / 1_000_000_000.0 >= userScrollDampSec
        } else {
            (nowNanos - a.startNanos) / 1_000_000_000.0 >= a.maxLineDurSec
        }
        if (expired) {
            scrollAnims.removeAt(i)
        }
    }
}

private fun computeStaggerDy(
    index: Int,
    nowNanos: Long,
    scrollAnims: List<ScrollAnim>,
    motion: MotionTokens,
): Float {
    if (scrollAnims.isEmpty()) return 0f
    val baseDur = motion.scrollBaseSecs.toDouble().coerceAtLeast(0.01)
    val dampSec = motion.userScrollDampSec.toDouble().coerceAtLeast(0.001)
    var staggerDy = 0f
    for (i in scrollAnims.indices) {
        val a = scrollAnims[i]
        val rel = index - a.targetIndex
        val lineDur = scrollDurationSec(rel, a.deltaPx, baseDur).coerceAtLeast(0.01)
        if (a.dampStartNanos > 0L) {
            val dampElapsed = ((nowNanos - a.dampStartNanos) / 1_000_000_000.0).coerceAtLeast(0.0)
            if (dampElapsed >= dampSec) continue
            val elapsedAtDamp = ((a.dampStartNanos - a.startNanos) / 1_000_000_000.0).coerceAtLeast(0.0)
            val pAtDamp = (elapsedAtDamp / lineDur).toFloat().coerceIn(0f, 1f)
            val initialDy = a.deltaPx * (1f - motion.scrollEase.at(pAtDamp))
            val dampP = (dampElapsed / dampSec).toFloat().coerceIn(0f, 1f)
            staggerDy += initialDy * (1f - motion.easeOut.at(dampP))
        } else {
            val elapsedSec = ((nowNanos - a.startNanos) / 1_000_000_000.0).coerceAtLeast(0.0)
            if (elapsedSec >= a.maxLineDurSec) continue
            val p = (elapsedSec / lineDur).toFloat().coerceIn(0f, 1f)
            staggerDy += a.deltaPx * (1f - motion.scrollEase.at(p))
        }
    }
    return staggerDy
}

private fun findActiveLineIndex(
    lines: List<YumaLyricsLine>,
    positionMs: Long,
): Int {
    if (lines.isEmpty()) return -1
    var low = 0
    var high = lines.lastIndex
    var result = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (lines[mid].timeMs <= positionMs) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

private fun maxScrollDurationSec(deltaPx: Float, baseSecs: Double): Double =
    max(
        scrollDurationSec(-MAX_SCROLL_STAGGER_REL, deltaPx, baseSecs),
        scrollDurationSec(MAX_SCROLL_STAGGER_REL, deltaPx, baseSecs),
    )
