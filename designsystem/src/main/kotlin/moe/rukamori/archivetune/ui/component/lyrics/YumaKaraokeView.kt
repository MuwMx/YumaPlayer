/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val VIEWPORT_ANCHOR_RATIO = 0.37f
private const val AUTOSCROLL_DURATION_MS = 460
private const val MIN_SCROLL_DELTA_PX = 6

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

    var isAutoscrollPaused by remember(document) { mutableStateOf(false) }

    val activeIndex by remember(lines) {
        derivedStateOf { findActiveLineIndex(lines, positionMsProvider()) }
    }

    val nestedScrollConnection = remember(document) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    isAutoscrollPaused = true
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
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val anchorOffsetPx = (viewportHeightPx * VIEWPORT_ANCHOR_RATIO).roundToInt()

        LaunchedEffect(document, autoScrollEnabled, isAutoscrollPaused, anchorOffsetPx) {
            if (!autoScrollEnabled || isAutoscrollPaused || lines.isEmpty()) return@LaunchedEffect
            snapshotFlow { activeIndex }
                .distinctUntilChanged()
                .collectLatest { targetIndex ->
                    if (targetIndex in lines.indices) {
                        scrollLineToAnchor(
                            listState = listState,
                            targetIndex = targetIndex,
                            anchorOffsetPx = anchorOffsetPx,
                        )
                    }
                }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(
                items = lines,
                key = { index, line -> "${line.timeMs}_$index" },
            ) { index, line ->
                val resolvedAlignment = resolveLineAlignment(style.alignment, line.agent)
                YumaLyricsLineItem(
                    line = line,
                    index = index,
                    activeIndex = activeIndex,
                    alignment = resolvedAlignment,
                    style = style,
                    onClick = {
                        if (!line.isInstrumental && line.timeMs >= 0L) {
                            isAutoscrollPaused = false
                            onSeek(line.timeMs)
                        }
                    },
                    onLongPress = onLineLongPress?.let { handler -> { handler(line) } },
                )
            }

            if (document.songwriters.isNotEmpty()) {
                item(key = "songwriters_footer") {
                    val writers = remember(document.songwriters) {
                        document.songwriters.joinToString(", ")
                    }
                    Text(
                        text = writtenByFormat.format(writers),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = style.fontFamily,
                            fontSize = (14f * style.textScale).sp,
                        ),
                        color = style.textColor.copy(alpha = 0.55f),
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
                    isAutoscrollPaused = false
                    val target = activeIndex.takeIf { it in lines.indices } ?: 0
                    coroutineScope.launch {
                        scrollLineToAnchor(
                            listState = listState,
                            targetIndex = target,
                            anchorOffsetPx = anchorOffsetPx,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YumaLyricsLineItem(
    line: YumaLyricsLine,
    index: Int,
    activeIndex: Int,
    alignment: YumaLyricsAlignment,
    style: YumaLyricsStyle,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)?,
) {
    val isActive = index == activeIndex
    val distance = if (activeIndex >= 0) abs(index - activeIndex) else 0

    val targetAlpha = when {
        activeIndex < 0 -> 0.78f
        isActive -> 1f
        distance == 1 -> lerp(style.dimAlpha, 1f, 0.35f)
        else -> style.dimAlpha
    }
    val targetScale = when {
        activeIndex < 0 -> style.inactiveScale
        isActive -> style.activeScale
        else -> style.inactiveScale
    }
    val targetBlurDp = when {
        !style.blurEnabled || activeIndex < 0 || isActive -> 0f
        distance == 1 -> style.maxBlurRadiusDp * 0.35f
        else -> style.maxBlurRadiusDp * (distance.coerceAtMost(3) / 3f)
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 260),
        label = "lineAlpha",
    )
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "lineScale",
    )
    val animatedBlurDp by animateFloatAsState(
        targetValue = targetBlurDp,
        animationSpec = tween(durationMillis = 280),
        label = "lineBlur",
    )

    val textAlign = when (alignment) {
        YumaLyricsAlignment.Center -> TextAlign.Center
        YumaLyricsAlignment.End -> TextAlign.End
        else -> TextAlign.Start
    }
    val horizontalAlignment = when (alignment) {
        YumaLyricsAlignment.Center -> Alignment.CenterHorizontally
        YumaLyricsAlignment.End -> Alignment.End
        else -> Alignment.Start
    }
    val transformOrigin = when (alignment) {
        YumaLyricsAlignment.Center -> TransformOrigin(0.5f, 0.5f)
        YumaLyricsAlignment.End -> TransformOrigin(1f, 0.5f)
        else -> TransformOrigin(0f, 0.5f)
    }

    val baseFontSize = 26f * style.textScale
    val lineHeight = baseFontSize * style.lineSpacingMultiplier

    Column(
        horizontalAlignment = horizontalAlignment,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !line.isInstrumental,
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .graphicsLayer {
                alpha = animatedAlpha
                scaleX = animatedScale
                scaleY = animatedScale
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
        val displayText = if (line.isInstrumental && line.text.isBlank()) "•••" else line.text
        Text(
            text = displayText,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = baseFontSize.sp,
                lineHeight = lineHeight.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = style.fontFamily,
            ),
            color = style.textColor,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )

        if (style.showRomanization && !line.romanizedText.isNullOrBlank()) {
            Text(
                text = line.romanizedText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = (baseFontSize * 0.55f).sp,
                    lineHeight = (baseFontSize * 0.55f * style.lineSpacingMultiplier).sp,
                    fontFamily = style.fontFamily,
                ),
                color = style.textColor.copy(alpha = 0.78f),
                textAlign = textAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }

        if (style.showTranslation && !line.translationText.isNullOrBlank()) {
            Text(
                text = line.translationText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = (baseFontSize * 0.62f).sp,
                    lineHeight = (baseFontSize * 0.62f * style.lineSpacingMultiplier).sp,
                    fontFamily = style.fontFamily,
                ),
                color = style.textColor.copy(alpha = 0.72f),
                textAlign = textAlign,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
    }
}

private fun resolveLineAlignment(
    styleAlignment: YumaLyricsAlignment,
    agent: String?,
): YumaLyricsAlignment =
    when (styleAlignment) {
        YumaLyricsAlignment.AutoByAgent -> {
            if (agent.equals("v2", ignoreCase = true)) {
                YumaLyricsAlignment.End
            } else {
                YumaLyricsAlignment.Start
            }
        }
        else -> styleAlignment
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

private suspend fun scrollLineToAnchor(
    listState: LazyListState,
    targetIndex: Int,
    anchorOffsetPx: Int,
) {
    val total = listState.layoutInfo.totalItemsCount
    if (total == 0) return
    val clampedIndex = targetIndex.coerceIn(0, total - 1)

    var itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == clampedIndex }
    if (itemInfo == null) {
        val distance = abs(clampedIndex - listState.firstVisibleItemIndex)
        if (distance <= 10) {
            listState.animateScrollToItem(clampedIndex)
        } else {
            listState.scrollToItem(clampedIndex)
        }
        withFrameNanos { }
        itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == clampedIndex }
    }
    itemInfo ?: return

    val itemCenterOffset = itemInfo.offset + itemInfo.size / 2
    val delta = itemCenterOffset - anchorOffsetPx
    if (abs(delta) > MIN_SCROLL_DELTA_PX) {
        listState.animateScrollBy(
            value = delta.toFloat(),
            animationSpec = tween(durationMillis = AUTOSCROLL_DURATION_MS, easing = FastOutSlowInEasing),
        )
    }
}
