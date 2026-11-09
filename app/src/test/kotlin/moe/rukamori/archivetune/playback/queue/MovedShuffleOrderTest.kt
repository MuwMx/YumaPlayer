package moe.rukamori.archivetune.playback.queue

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MovedShuffleOrderTest {
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
    fun draggingASongOneSpotDownMovesOnlyThatSong() {
        // Shuffled queue 5, 1, 7, 2, ...: the song at playlist position 7 is dragged below the one at 2.
        val order = buildMovedShuffleOrder(timeline(listOf(5, 1, 7, 2, 6, 0, 3, 4)), fromIndex = 7, toIndex = 2)!!

        assertEquals(listOf(5, 1, 2, 7, 6, 0, 3, 4), playOrderOf(order))
    }

    @Test
    fun draggingItBackRestoresTheQueueExactly() {
        val queue = listOf(5, 1, 7, 2, 6, 0, 3, 4)
        val down = playOrderOf(buildMovedShuffleOrder(timeline(queue), fromIndex = 7, toIndex = 2)!!)

        val back = buildMovedShuffleOrder(timeline(down), fromIndex = 7, toIndex = 2)!!

        assertEquals(queue, playOrderOf(back))
    }

    @Test
    fun draggingUpPlacesTheSongWhereTheTargetWas() {
        val order = buildMovedShuffleOrder(timeline(listOf(5, 1, 3, 2, 4, 0)), fromIndex = 0, toIndex = 1)!!

        assertEquals(listOf(5, 0, 1, 3, 2, 4), playOrderOf(order))
    }

    @Test
    fun nothingToDoForADropOnItselfOrAnUnknownSong() {
        assertNull(buildMovedShuffleOrder(timeline(listOf(2, 0, 1)), fromIndex = 1, toIndex = 1))
        assertNull(buildMovedShuffleOrder(timeline(listOf(2, 0, 1)), fromIndex = 1, toIndex = 9))
        assertNull(buildMovedShuffleOrder(timeline(emptyList(), windowCount = 0), fromIndex = 0, toIndex = 1))
    }
}
