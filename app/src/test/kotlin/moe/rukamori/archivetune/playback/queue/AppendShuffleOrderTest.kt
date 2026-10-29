package moe.rukamori.archivetune.playback.queue

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppendShuffleOrderTest {
    /** A timeline of [windowCount] songs whose shuffle play order is [playOrder]. */
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

    private fun playOrderOf(order: androidx.media3.exoplayer.source.ShuffleOrder): List<Int> {
        val result = mutableListOf<Int>()
        var index = order.firstIndex
        while (index != C.INDEX_UNSET) {
            result += index
            index = order.getNextIndex(index)
        }
        return result
    }

    @Test
    fun theExistingPlayOrderIsKeptAndTheNewSongsPlayLast() {
        val order = buildAppendShuffleOrder(timeline(listOf(2, 0, 3, 1)), addedCount = 2)!!

        assertEquals(listOf(2, 0, 3, 1, 4, 5), playOrderOf(order))
    }

    @Test
    fun nothingToDoWhenNothingIsAddedOrTheQueueIsEmpty() {
        assertNull(buildAppendShuffleOrder(timeline(listOf(0, 1)), addedCount = 0))
        assertNull(buildAppendShuffleOrder(timeline(emptyList(), windowCount = 0), addedCount = 3))
    }

    @Test
    fun songsLeftAreCountedInPlayOrderWhenShuffled() {
        // The last song of the list plays second, so most of the queue is still ahead of it.
        val shuffled = timeline(listOf(4, 9, 0, 1, 2, 3, 5, 6, 7, 8))

        assertEquals(6, songsLeftInPlayOrder(shuffled, currentIndex = 9, shuffle = true, limit = 6))
        assertEquals(1, songsLeftInPlayOrder(shuffled, currentIndex = 9, shuffle = false, limit = 6))
        assertEquals(2, songsLeftInPlayOrder(shuffled, currentIndex = 7, shuffle = true, limit = 6))
    }
}
