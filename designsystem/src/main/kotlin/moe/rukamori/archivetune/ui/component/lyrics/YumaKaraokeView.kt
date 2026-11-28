/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ui.component.smoothFadingEdge
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val MIN_SCROLL_DELTA_PX = 2f
private const val MAX_SMOOTH_SCROLL_ITEM_DISTANCE = 8
private const val MAX_SCROLL_STAGGER_REL = 25
private const val USER_SCROLL_DAMP_SEC = 0.15

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
    listState: LazyListState = rememberLazyListState(),
    onSeek: (Long) -> Unit = {},
    onLineLongPress: ((YumaLyricsLine) -> Unit)? = null,
) {
    val lines = document.lines
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val textMeasurer = rememberTextMeasurer()

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
            delay(remaining)
        }
        userScrollUntilMs = 0L
    }

    val scrollTargetIndex by remember(lines, timing) {
        derivedStateOf { findScrollTargetLineIndex(lines, positionMsProvider(), timing) }
    }
    val activeLineIndex by remember(lines) {
        derivedStateOf { findActiveLineIndex(lines, positionMsProvider()) }
    }
    val creditsFocused by remember(lines, timing) {
        derivedStateOf {
            val lastSung = lines.indexOfLast { !it.isInstrumental }
            if (lastSung < 0) false else positionMsProvider() + timing.scrollOffsetMs >= lines[lastSung].endTimeMs
        }
    }

    val scrollAnims = remember(document) { ArrayList<ScrollAnim>() }
    val animFrameState = remember(document) { mutableLongStateOf(0L) }
    var animWakeNanos by remember(document) { mutableLongStateOf(0L) }
    val easeEasing = remember(motion.ease) {
        Easing { x -> motion.ease.at(x) }
    }

    LaunchedEffect(document, activeLineIndex, isAutoscrollPaused, animWakeNanos) {
        val startNanos = System.nanoTime()
        val minRunSec = motion.opacityTransitionSecs.toDouble() + 0.1
        while (true) {
            val frameNanos = withFrameNanos { it }
            animFrameState.longValue = frameNanos
            val elapsedSec = (frameNanos - startNanos) / 1_000_000_000.0
            if (scrollAnims.isEmpty() && elapsedSec >= minRunSec) break
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
                        animWakeNanos = nowNanos
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
        val horizontalPadPx = with(density) { 16.dp.toPx() }
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

        LaunchedEffect(document, autoScrollEnabled, isAutoscrollPaused, anchorOffsetPx, motion.scrollBaseSecs) {
            if (!autoScrollEnabled || isAutoscrollPaused || lines.isEmpty()) return@LaunchedEffect
            snapshotFlow { scrollTargetIndex }
                .distinctUntilChanged()
                .collectLatest { targetIndex ->
                    val clamped = if (targetIndex in lines.indices) targetIndex else 0
                    scrollLineToAnchor(
                        listState = listState,
                        targetIndex = clamped,
                        anchorOffsetPx = anchorOffsetPx,
                        baseSecs = motion.scrollBaseSecs.toDouble(),
                        scrollAnims = scrollAnims,
                    )
                    animWakeNanos = System.nanoTime()
                }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 24.dp, bottom = bottomPaddingDp),
            modifier = Modifier
                .fillMaxSize()
                .smoothFadingEdge(top = 40.dp, bottom = 56.dp),
        ) {
            itemsIndexed(
                items = preparedLines,
                key = { index, prepared -> "${lines[index].timeMs}_$index" },
            ) { index, prepared ->
                val line = lines[index]
                val nextLineStartSec = lines.getOrNull(index + 1)?.let { it.timeMs / 1000.0 } ?: Double.POSITIVE_INFINITY
                val isLineActive = index == activeLineIndex
                val distance = if (activeLineIndex >= 0) abs(index - activeLineIndex) else index + 1
                val distStyle = when {
                    isLineActive -> LineDistanceStyle(alpha = visual.activeAlpha, blurFraction = 0f)
                    activeLineIndex >= 0 && index < activeLineIndex -> ladderStyle(visual.past, distance)
                    else -> ladderStyle(visual.upcoming, distance)
                }
                val targetBlurDp = if (style.blurEnabled && !isLineActive) {
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
                        .padding(horizontal = 16.dp)
                        .graphicsLayer {
                            val nowFrameNanos = animFrameState.longValue
                            val posMs = positionMsProvider()
                            val tSec = posMs / 1000.0
                            val lineStartSec = prepared.startSec
                            val lineEndSec = max(nextLineStartSec, prepared.startSec + prepared.durSec + 0.05)
                            val inLine = tSec >= lineStartSec && tSec < lineEndSec

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

                            var staggerDy = 0f
                            if (scrollAnims.isNotEmpty()) {
                                val nowNanos = System.nanoTime()
                                val baseDur = motion.scrollBaseSecs.toDouble().coerceAtLeast(0.01)
                                for (i in scrollAnims.lastIndex downTo 0) {
                                    val a = scrollAnims[i]
                                    val rel = index - a.targetIndex
                                    val lineDur = scrollDurationSec(rel, a.deltaPx, baseDur).coerceAtLeast(0.01)
                                    if (a.dampStartNanos > 0L) {
                                        val dampElapsed = (nowNanos - a.dampStartNanos) / 1_000_000_000.0
                                        if (dampElapsed >= USER_SCROLL_DAMP_SEC) {
                                            scrollAnims.removeAt(i)
                                            continue
                                        }
                                        val elapsedAtDamp = (a.dampStartNanos - a.startNanos) / 1_000_000_000.0
                                        val pAtDamp = (elapsedAtDamp / lineDur).toFloat().coerceIn(0f, 1f)
                                        val initialDy = a.deltaPx * (1f - motion.scrollEase.at(pAtDamp))
                                        val dampP = (dampElapsed / USER_SCROLL_DAMP_SEC).toFloat().coerceIn(0f, 1f)
                                        staggerDy += initialDy * (1f - motion.easeOut.at(dampP))
                                    } else {
                                        val elapsedSec = (nowNanos - a.startNanos) / 1_000_000_000.0
                                        if (elapsedSec >= a.maxLineDurSec) {
                                            scrollAnims.removeAt(i)
                                            continue
                                        }
                                        val p = (elapsedSec / lineDur).toFloat().coerceIn(0f, 1f)
                                        staggerDy += a.deltaPx * (1f - motion.scrollEase.at(p))
                                    }
                                }
                            }

                            scaleX = scale
                            scaleY = scale
                            translationY = staggerDy
                            this.transformOrigin = transformOrigin
                        }
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
                        animTickProvider = { animFrameState.longValue },
                    )
                }
            }

            if (document.songwriters.isNotEmpty()) {
                item(key = "songwriters_footer") {
                    val writers = remember(document.songwriters) {
                        document.songwriters.joinToString(", ")
                    }
                    val creditsAlpha = if (creditsFocused) visual.creditsAlphaFocused else visual.creditsAlphaRest
                    Text(
                        text = writtenByFormat.format(writers),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = style.fontFamily,
                            fontSize = (14f * style.textScale).sp,
                        ),
                        color = style.textColor.copy(alpha = creditsAlpha.coerceIn(0f, 1f)),
                        textAlign = when (style.alignment) {
                            YumaLyricsAlignment.Center -> TextAlign.Center
                            YumaLyricsAlignment.End -> TextAlign.End
                            else -> TextAlign.Start
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
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
                    val target = scrollTargetIndex.takeIf { it in lines.indices } ?: 0
                    coroutineScope.launch {
                        scrollLineToAnchor(
                            listState = listState,
                            targetIndex = target,
                            anchorOffsetPx = anchorOffsetPx,
                            baseSecs = motion.scrollBaseSecs.toDouble(),
                            scrollAnims = scrollAnims,
                        )
                    }
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

private fun findScrollTargetLineIndex(
    lines: List<YumaLyricsLine>,
    positionMs: Long,
    timing: TimingTokens,
): Int {
    if (lines.isEmpty()) return -1
    val scrollTimeMs = positionMs + timing.scrollOffsetMs
    val active = ArrayList<Int>(2)
    for (i in lines.indices) {
        val line = lines[i]
        val nextStart = lines.getOrNull(i + 1)?.timeMs ?: Long.MAX_VALUE
        if (scrollTimeMs >= line.timeMs - timing.earlyScrollMs &&
            (scrollTimeMs < nextStart || scrollTimeMs < line.endTimeMs)
        ) {
            active.add(i)
        }
    }
    if (active.isEmpty()) {
        return findActiveLineIndex(lines, scrollTimeMs)
    }
    val sung = active.filterIndexed { k, idx ->
        scrollTimeMs < lines[idx].endTimeMs - timing.endingThresholdMs || k == active.lastIndex
    }
    return sung.lastOrNull { lines[it].timeMs <= scrollTimeMs && scrollTimeMs < lines[it].endTimeMs }
        ?: sung.lastOrNull { lines[it].timeMs <= scrollTimeMs }
        ?: sung.first()
}

private fun maxScrollDurationSec(deltaPx: Float, baseSecs: Double): Double =
    max(
        scrollDurationSec(-MAX_SCROLL_STAGGER_REL, deltaPx, baseSecs),
        scrollDurationSec(MAX_SCROLL_STAGGER_REL, deltaPx, baseSecs),
    )

private suspend fun scrollLineToAnchor(
    listState: LazyListState,
    targetIndex: Int,
    anchorOffsetPx: Int,
    baseSecs: Double,
    scrollAnims: MutableList<ScrollAnim>,
) {
    val total = listState.layoutInfo.totalItemsCount
    if (total == 0) return
    val clampedIndex = targetIndex.coerceIn(0, total - 1)

    var itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == clampedIndex }
    if (itemInfo == null) {
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val firstVisible = visibleItems.firstOrNull()
        val lastVisible = visibleItems.lastOrNull()
        val itemDistance = when {
            firstVisible != null && clampedIndex < firstVisible.index -> firstVisible.index - clampedIndex
            lastVisible != null && clampedIndex > lastVisible.index -> clampedIndex - lastVisible.index
            else -> abs(clampedIndex - listState.firstVisibleItemIndex)
        }

        if (itemDistance <= MAX_SMOOTH_SCROLL_ITEM_DISTANCE && visibleItems.isNotEmpty()) {
            val avgItemStep = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size
            val estimatedTargetCenter = when {
                firstVisible != null && clampedIndex < firstVisible.index ->
                    firstVisible.offset - (firstVisible.index - clampedIndex - 0.5f) * avgItemStep
                lastVisible != null && clampedIndex > lastVisible.index ->
                    (lastVisible.offset + lastVisible.size) + (clampedIndex - lastVisible.index - 0.5f) * avgItemStep
                else -> anchorOffsetPx.toFloat()
            }
            val estimatedDelta = estimatedTargetCenter - anchorOffsetPx
            if (abs(estimatedDelta) > MIN_SCROLL_DELTA_PX) {
                val consumed = listState.scrollBy(estimatedDelta)
                if (abs(consumed) > MIN_SCROLL_DELTA_PX) {
                    scrollAnims.add(
                        ScrollAnim(
                            startNanos = System.nanoTime(),
                            deltaPx = consumed,
                            targetIndex = clampedIndex,
                            maxLineDurSec = maxScrollDurationSec(consumed, baseSecs),
                        ),
                    )
                }
            }
            return
        }

        scrollAnims.clear()
        listState.scrollToItem(clampedIndex, scrollOffset = -anchorOffsetPx / 2)
        withFrameNanos { }
        itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == clampedIndex }
    }
    itemInfo ?: return

    val itemCenterOffset = itemInfo.offset + itemInfo.size / 2
    val delta = (itemCenterOffset - anchorOffsetPx).toFloat()
    if (abs(delta) > MIN_SCROLL_DELTA_PX) {
        val consumed = listState.scrollBy(delta)
        if (abs(consumed) > MIN_SCROLL_DELTA_PX) {
            scrollAnims.add(
                ScrollAnim(
                    startNanos = System.nanoTime(),
                    deltaPx = consumed,
                    targetIndex = clampedIndex,
                    maxLineDurSec = maxScrollDurationSec(consumed, baseSecs),
                ),
            )
        }
    }
}
