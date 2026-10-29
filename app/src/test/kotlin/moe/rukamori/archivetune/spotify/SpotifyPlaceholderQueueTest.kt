package moe.rukamori.archivetune.spotify

import android.net.Uri
import android.os.Bundle
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class SpotifyPlaceholderQueueTest {
    @Before
    fun setup() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val value = firstArg<String>()
            mockk<Uri>(relaxed = true).also { uri ->
                every { uri.toString() } returns value
            }
        }
        mockkConstructor(Bundle::class)
        every { anyConstructed<Bundle>().putBoolean(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun placeholder(index: Int) =
        MediaMetadata(id = "spotify:track$index", title = "Track $index", artists = emptyList(), duration = 180)

    private fun youtube(index: Int) =
        MediaMetadata(id = "yt$index", title = "Track $index", artists = emptyList(), duration = 180)

    @Test
    fun wholePlaylistIsQueuedInOrderWithSelectedSongCurrent() =
        runTest {
            val items = (0 until 692).map(::placeholder)
            val queue =
                SpotifyPlaceholderQueue(
                    title = "Masterworks",
                    items = items,
                    startIndex = 690,
                    storedMatches = { emptyMap() },
                )

            val status = queue.getInitialStatus()

            // Every song stays in the queue: the two before the end are above
            // the current one only because they come earlier in the playlist,
            // and nothing is ever paged in later.
            assertEquals(692, status.items.size)
            assertEquals(690, status.mediaItemIndex)
            assertEquals(items.map { it.id }, status.items.map { it.mediaId })
            assertFalse(queue.hasNextPage())
        }

    @Test
    fun storedMatchesBecomeYouTubeItemsButTheSelectedSongKeepsItsId() =
        runTest {
            val items = (0 until 5).map(::placeholder)
            val queue =
                SpotifyPlaceholderQueue(
                    title = "Copy",
                    items = items,
                    startIndex = 2,
                    storedMatches = { list ->
                        list.associate { it.id to youtube(it.id.removePrefix("spotify:track").toInt()) }
                            .filterKeys { it != "spotify:track1" && it != "spotify:track3" }
                    },
                )

            val status = queue.getInitialStatus()

            // The selected song is already loading under its placeholder id;
            // changing it here would make the player restart it.
            assertEquals(
                listOf("yt0", "spotify:track1", "spotify:track2", "spotify:track3", "yt4"),
                status.items.map { it.mediaId },
            )
            assertEquals(queue.preloadItem?.id, status.items[status.mediaItemIndex].mediaId)
            assertEquals(2, status.mediaItemIndex)
        }

    @Test
    fun unresolvedSelectedSongStaysQueuedForThePlayerToMatch() =
        runTest {
            val queue =
                SpotifyPlaceholderQueue(
                    title = "Copy",
                    items = listOf(placeholder(0), placeholder(1)),
                    startIndex = 1,
                    storedMatches = { emptyMap() },
                )

            val status = queue.getInitialStatus()

            assertEquals(listOf("spotify:track0", "spotify:track1"), status.items.map { it.mediaId })
            assertEquals(1, status.mediaItemIndex)
        }

    @Test
    fun listsWithoutPlaceholdersStayPlainListQueues() {
        val queue = spotifyAwareQueue("Mix", listOf(youtube(0), youtube(1)), startIndex = 1)
        assertTrue(queue is ListQueue)
    }

    @Test
    fun placeholderIdsAreRecognised() {
        assertTrue(SpotifyPlaceholders.isPlaceholder("spotify:4uLU6hMCjMI75M1A2tKUQC"))
        assertFalse(SpotifyPlaceholders.isPlaceholder("spotify:"))
        assertFalse(SpotifyPlaceholders.isPlaceholder("spotify:track:4uLU6hMCjMI75M1A2tKUQC"))
        assertFalse(SpotifyPlaceholders.isPlaceholder("dQw4w9WgXcQ"))
        assertFalse(SpotifyPlaceholders.isPlaceholder(null))
    }

    @Test
    fun spotifyTrackIdsBecomePlaceholderIds() {
        assertEquals("spotify:4uLU6hMCjMI75M1A2tKUQC", SpotifyTrack(id = "spotify:track:4uLU6hMCjMI75M1A2tKUQC").toPlaceholderId())
        assertEquals("spotify:4uLU6hMCjMI75M1A2tKUQC", SpotifyTrack(id = "4uLU6hMCjMI75M1A2tKUQC").toPlaceholderId())
        assertEquals(null, SpotifyTrack(id = " ").toPlaceholderId())
    }
}
