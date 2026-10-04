/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */
package moe.rukamori.archivetune.playback.queue

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import java.util.ArrayDeque

@OptIn(UnstableApi::class)
internal interface ReadOnlyTimelineDelegate {
    fun getCurrentTimeline(): Timeline
    fun getMediaItemCount(): Int
    fun getCurrentMediaItemIndex(): Int
}

@OptIn(UnstableApi::class)
internal data class PlayNextInsertionPlan(
    val insertionIndex: Int,
    val items: List<MediaItem>,
    val shuffleOrder: DefaultShuffleOrder?,
)

@OptIn(UnstableApi::class)
internal fun applyCurrentFirstShuffleOrder(
    itemCount: Int,
    currentIndex: Int,
    randomSeed: Long = System.currentTimeMillis(),
): DefaultShuffleOrder? {
    if (itemCount <= 1) return null
    val safeCurrentIndex = currentIndex.coerceIn(0, itemCount - 1)
    val shuffledIndices = IntArray(itemCount) { it }
    shuffledIndices.shuffle()
    val currentPos = shuffledIndices.indexOf(safeCurrentIndex)
    if (currentPos >= 0) shuffledIndices[currentPos] = shuffledIndices[0]
    shuffledIndices[0] = safeCurrentIndex
    return DefaultShuffleOrder(shuffledIndices, randomSeed)
}

@OptIn(UnstableApi::class)
internal fun buildPlayNextShuffleOrder(
    timeline: Timeline,
    mediaItemCount: Int,
    currentIndex: Int,
    insertionIndex: Int,
    insertionCount: Int,
    randomSeed: Long = System.currentTimeMillis(),
): DefaultShuffleOrder? {
    if (insertionCount <= 0 || timeline.isEmpty) return null

    fun adjustedIndex(index: Int): Int =
        if (index >= insertionIndex) index + insertionCount else index

    val previousIndices = ArrayDeque<Int>()
    var traversalIndex = currentIndex
    while (true) {
        traversalIndex = timeline.getPreviousWindowIndex(traversalIndex, REPEAT_MODE_OFF, true)
        if (traversalIndex == C.INDEX_UNSET) break
        previousIndices.addFirst(adjustedIndex(traversalIndex))
    }

    val nextIndices = mutableListOf<Int>()
    traversalIndex = currentIndex
    while (true) {
        traversalIndex = timeline.getNextWindowIndex(traversalIndex, REPEAT_MODE_OFF, true)
        if (traversalIndex == C.INDEX_UNSET) break
        nextIndices += adjustedIndex(traversalIndex)
    }

    val shuffledIndices = buildList(mediaItemCount + insertionCount) {
        addAll(previousIndices)
        add(currentIndex)
        repeat(insertionCount) { offset -> add(insertionIndex + offset) }
        addAll(nextIndices)
    }.toIntArray()

    return DefaultShuffleOrder(shuffledIndices, randomSeed)
}

@OptIn(UnstableApi::class)
internal fun buildPlayNextShuffleOrder(
    timelineDelegate: ReadOnlyTimelineDelegate,
    insertionIndex: Int,
    insertionCount: Int,
    randomSeed: Long = System.currentTimeMillis(),
): DefaultShuffleOrder? = buildPlayNextShuffleOrder(
    timeline = timelineDelegate.getCurrentTimeline(),
    mediaItemCount = timelineDelegate.getMediaItemCount(),
    currentIndex = timelineDelegate.getCurrentMediaItemIndex(),
    insertionIndex = insertionIndex,
    insertionCount = insertionCount,
    randomSeed = randomSeed,
)

internal fun calculatePlayNextInsertionIndex(itemCount: Int, currentIndex: Int): Int =
    if (itemCount == 0) 0 else currentIndex + 1

@OptIn(UnstableApi::class)
internal fun planPlayNextInsertion(
    timeline: Timeline,
    mediaItemCount: Int,
    currentIndex: Int,
    items: List<MediaItem>,
    isShuffleModeEnabled: Boolean,
    randomSeed: Long = System.currentTimeMillis(),
): PlayNextInsertionPlan {
    val insertionIndex = calculatePlayNextInsertionIndex(mediaItemCount, currentIndex)
    val shuffleOrder =
        if (isShuffleModeEnabled && mediaItemCount > 0) {
            buildPlayNextShuffleOrder(
                timeline = timeline,
                mediaItemCount = mediaItemCount,
                currentIndex = currentIndex,
                insertionIndex = insertionIndex,
                insertionCount = items.size,
                randomSeed = randomSeed,
            )
        } else null
    return PlayNextInsertionPlan(insertionIndex, items, shuffleOrder)
}

@OptIn(UnstableApi::class)
internal fun performPlayNextHostInsert(
    timeline: Timeline,
    mediaItemCount: Int,
    currentIndex: Int,
    items: List<MediaItem>,
    isShuffleModeEnabled: Boolean,
    onSetSuppressAutoPlayback: (Boolean) -> Unit,
    onAddMediaItems: (index: Int, items: List<MediaItem>) -> Unit,
    onSetShuffleOrder: (ShuffleOrder) -> Unit,
    onPrepare: () -> Unit,
) {
    val plan = planPlayNextInsertion(
        timeline, mediaItemCount, currentIndex, items, isShuffleModeEnabled,
    )
    onSetSuppressAutoPlayback(false)
    onAddMediaItems(plan.insertionIndex, plan.items)
    plan.shuffleOrder?.let(onSetShuffleOrder)
    onPrepare()
}

@OptIn(UnstableApi::class)
internal fun performAddToQueueHostInsert(
    items: List<MediaItem>,
    onSetSuppressAutoPlayback: (Boolean) -> Unit,
    onAddMediaItems: (items: List<MediaItem>) -> Unit,
    onPrepare: () -> Unit,
) {
    onSetSuppressAutoPlayback(false)
    onAddMediaItems(items)
    onPrepare()
}
