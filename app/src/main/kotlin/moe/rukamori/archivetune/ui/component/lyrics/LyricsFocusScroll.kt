/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.component.lyrics

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val LYRIC_FOCUS_ANCHOR_RATIO = 0.42f
internal const val LYRIC_LINE_SYNC_TOP_ANCHOR_RATIO = 0.35f
internal const val LYRIC_FOCUS_TOP_GUARD_RATIO = 0.18f
internal const val LYRIC_FOCUS_BOTTOM_GUARD_RATIO = 0.24f
internal const val LYRIC_FOCUS_MIN_SCROLL_PX = 6
internal const val LYRIC_FOCUS_ANIMATED_DISTANCE = 12
internal const val LYRIC_FOCUS_SCROLL_DURATION_MS = 520

internal fun SyncedLyrics.positionForStableLineFocus(time: Int): Int {
    if (lines.isEmpty()) return time
    val index = findLastStartedLineIndex(time)
    if (index < 0) return time

    val line = lines[index]
    if (time < line.end) return time

    return (line.end - 1).coerceAtLeast(line.start)
}

internal fun SyncedLyrics.findLastStartedLineIndex(time: Int): Int {
    var low = 0
    var high = lines.lastIndex
    var result = -1

    while (low <= high) {
        val mid = low + (high - low) / 2
        if (lines[mid].start <= time) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }

    return result
}

internal suspend fun LazyListState.scrollLyricIntoFocus(
    index: Int,
    animateToNearbyItem: Boolean,
    force: Boolean,
    alignByItemCenter: Boolean,
) {
    val itemCount = layoutInfo.totalItemsCount
    if (itemCount == 0) return

    val targetIndex = index.coerceIn(0, itemCount - 1)
    var itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { item -> item.index == targetIndex }
    if (itemInfo == null) {
        val distance = abs(targetIndex - firstVisibleItemIndex)
        if (animateToNearbyItem && distance <= LYRIC_FOCUS_ANIMATED_DISTANCE) {
            animateScrollToItem(targetIndex)
        } else {
            scrollToItem(targetIndex)
        }
        withFrameNanos { }
        itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { item -> item.index == targetIndex }
    }

    itemInfo ?: return

    val viewportStart = layoutInfo.viewportStartOffset
    val viewportEnd = layoutInfo.viewportEndOffset
    val viewportHeight = viewportEnd - viewportStart
    if (viewportHeight <= 0) return

    val itemFocusPoint =
        if (alignByItemCenter) {
            itemInfo.offset + itemInfo.size / 2
        } else {
            itemInfo.offset
        }
    val topGuard = viewportStart + (viewportHeight * LYRIC_FOCUS_TOP_GUARD_RATIO).roundToInt()
    val bottomGuard = viewportEnd - (viewportHeight * LYRIC_FOCUS_BOTTOM_GUARD_RATIO).roundToInt()
    if (!force && itemFocusPoint in topGuard..bottomGuard) return

    val anchorRatio =
        if (alignByItemCenter) {
            LYRIC_FOCUS_ANCHOR_RATIO
        } else {
            LYRIC_LINE_SYNC_TOP_ANCHOR_RATIO
        }
    val targetFocusPoint = viewportStart + (viewportHeight * anchorRatio).roundToInt()
    val scrollDelta = itemFocusPoint - targetFocusPoint
    if (abs(scrollDelta) > LYRIC_FOCUS_MIN_SCROLL_PX) {
        animateScrollBy(
            value = scrollDelta.toFloat(),
            animationSpec =
                tween(
                    durationMillis = LYRIC_FOCUS_SCROLL_DURATION_MS,
                    easing = FastOutSlowInEasing,
                ),
        )
    }
}

@Composable
internal fun LyricsFocusAutoScrollEffect(
    listState: LazyListState,
    syncedLyrics: SyncedLyrics,
    lyricsSessionKey: Pair<String, String?>,
    isSynced: Boolean,
    isTtmlFormat: Boolean,
    isReadyToParse: Boolean,
    isLyricsVisible: Boolean,
    isManualScrolling: Boolean,
    isSelectionModeActive: Boolean,
    lineFocusPosition: () -> Int,
) {
    val latestIsLyricsVisible = rememberUpdatedState(isLyricsVisible)

    LaunchedEffect(lyricsSessionKey, syncedLyrics, isSynced, isReadyToParse, isLyricsVisible) {
        if (!isReadyToParse || !isSynced || !isLyricsVisible) return@LaunchedEffect
        if (syncedLyrics.lines.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.viewportEndOffset > listState.layoutInfo.viewportStartOffset
        }.first { it }

        var forceNextScroll = true
        snapshotFlow {
            if (!latestIsLyricsVisible.value || isManualScrolling || isSelectionModeActive) {
                null
            } else {
                syncedLyrics
                    .getCurrentFirstHighlightLineIndexByTime(lineFocusPosition())
                    .takeIf { index -> index in syncedLyrics.lines.indices }
            }
        }.distinctUntilChanged()
            .collectLatest { index ->
                if (index == null) {
                    forceNextScroll = true
                    return@collectLatest
                }
                listState.scrollLyricIntoFocus(
                    index = index,
                    animateToNearbyItem = !forceNextScroll,
                    force = forceNextScroll,
                    alignByItemCenter = isTtmlFormat,
                )
                forceNextScroll = false
            }
    }
}
