package moe.rukamori.archivetune.playback.queue

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueShuffleOrdersTest {
    private fun timeline(
        playOrder: List<Int>,
        windowCount: Int = playOrder.size,
    ): Timeline {
        val timeline = mockk<Timeline>()
        every { timeline.isEmpty } returns playOrder.isEmpty()
        every { timeline.windowCount } returns windowCount
        every { timeline.getFirstWindowIndex(true) } returns (playOrder.firstOrNull() ?: C.INDEX_UNSET)
        every { timeline.getNextWindowIndex(any(), Player.REPEAT_MODE_OFF, true) } answers {
            playOrder.getOrNull(playOrder.indexOf(firstArg<Int>()) + 1) ?: C.INDEX_UNSET
        }
        every { timeline.getNextWindowIndex(any(), Player.REPEAT_MODE_OFF, false) } answers {
            (firstArg<Int>() + 1).takeIf { it < windowCount } ?: C.INDEX_UNSET
        }
        return timeline
    }

    private fun playOrderOf(order: ShuffleOrder): List<Int> {
        val result = mutableListOf<Int>()
        var index = order.firstIndex
        while (index != C.INDEX_UNSET) {
            result += index
            index = order.getNextIndex(index)
        }
        return result
    }

    @Test
    fun prefixUpToCurrentPlayingTrackIsPreserved() {
        val initialOrder = listOf(4, 2, 0, 5, 1, 3)
        val timeline = timeline(initialOrder)
        val currentWindowIndex = 0
        val addedCount = 4
        val randomSeed = 12345L

        val order = buildAppendShuffleOrder(
            timeline = timeline,
            addedCount = addedCount,
            currentWindowIndex = currentWindowIndex,
            randomSeed = randomSeed,
        )!!

        val result = playOrderOf(order)
        assertEquals(10, result.size)
        assertEquals(listOf(4, 2, 0), result.subList(0, 3))
    }

    @Test
    fun resultingShuffleOrderIsValidPermutationWithNoDuplicates() {
        val initialOrder = listOf(2, 0, 3, 1)
        val timeline = timeline(initialOrder)
        val addedCount = 3
        val totalCount = initialOrder.size + addedCount

        for (currentWindowIndex in listOf(2, 0, 3, 1, C.INDEX_UNSET, 999)) {
            val order = buildAppendShuffleOrder(
                timeline = timeline,
                addedCount = addedCount,
                currentWindowIndex = currentWindowIndex,
                randomSeed = 42L,
            )!!
            val result = playOrderOf(order)
            assertEquals(totalCount, result.size)
            assertEquals(totalCount, result.toSet().size)
            assertEquals((0 until totalCount).toList(), result.sorted())
        }
    }

    @Test
    fun newlyAppendedItemsAreDistributedAcrossRemainingQueueRatherThanLinearTail() {
        val initialOrder = (0 until 10).toList()
        val timeline = timeline(initialOrder)
        val currentWindowIndex = 1
        val addedCount = 10
        val randomSeed = 100L

        val order = buildAppendShuffleOrder(
            timeline = timeline,
            addedCount = addedCount,
            currentWindowIndex = currentWindowIndex,
            randomSeed = randomSeed,
        )!!

        val result = playOrderOf(order)
        val remainingPart = result.subList(2, result.size)
        val appendedIndices = (10 until 20).toList()

        assertTrue(remainingPart.containsAll(appendedIndices))
        val tail = result.takeLast(addedCount)
        assertNotEquals(appendedIndices, tail)
    }

    @Test
    fun edgeCasesHandledSafely() {
        val timeline = timeline(listOf(0, 1, 2))
        assertNull(buildAppendShuffleOrder(timeline, addedCount = 0))
        assertNull(buildAppendShuffleOrder(timeline, addedCount = -1))
        assertNull(buildAppendShuffleOrder(timeline(emptyList(), windowCount = 0), addedCount = 3))

        val orderUnset = buildAppendShuffleOrder(timeline, addedCount = 2, currentWindowIndex = C.INDEX_UNSET)!!
        val resultUnset = playOrderOf(orderUnset)
        assertEquals(5, resultUnset.size)
        assertEquals((0 until 5).toList(), resultUnset.sorted())

        val orderNotFound = buildAppendShuffleOrder(timeline, addedCount = 2, currentWindowIndex = 99)!!
        val resultNotFound = playOrderOf(orderNotFound)
        assertEquals(5, resultNotFound.size)
        assertEquals((0 until 5).toList(), resultNotFound.sorted())
    }
}
