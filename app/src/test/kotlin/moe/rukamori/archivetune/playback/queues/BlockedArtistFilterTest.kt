package moe.rukamori.archivetune.playback.queues

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata as CommonMediaMetadata
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queue.BlockedArtistEvictor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BlockedArtistFilterTest {

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

    private fun createMediaItem(
        id: String,
        artistId: String?,
        artistName: String,
        fallbackArtist: String? = null,
    ): MediaItem {
        val meta = if (artistId != null || artistName.isNotBlank()) {
            MediaMetadata(
                id = id,
                title = "Title $id",
                artists = listOf(MediaMetadata.Artist(id = artistId, name = artistName)),
                duration = 200,
            )
        } else {
            null
        }

        val fakeUri = mockk<Uri>(relaxed = true)
        val builder = MediaItem.Builder()
            .setMediaId(id)
            .setUri(fakeUri)

        if (meta != null) {
            builder.setTag(meta)
        }
        if (fallbackArtist != null) {
            builder.setMediaMetadata(
                CommonMediaMetadata.Builder()
                    .setArtist(fallbackArtist)
                    .build(),
            )
        }
        return builder.build()
    }

    @Test
    fun hasBlockedArtistMatchesYouTubeArtistId() {
        val blockedIds = setOf("UC_BLOCKED_ID")
        val blockedNames = emptySet<String>()

        val blockedItem = createMediaItem("yt1", "UC_BLOCKED_ID", "Some Name")
        val unblockedItem = createMediaItem("yt2", "UC_OTHER_ID", "Some Name")

        assertTrue(blockedItem.hasBlockedArtist(blockedIds, blockedNames))
        assertFalse(unblockedItem.hasBlockedArtist(blockedIds, blockedNames))
    }

    @Test
    fun hasBlockedArtistMatchesNormalizedSpotifyArtistName() {
        val blockedIds = emptySet<String>()
        val blockedNames = setOf("bad artist")

        val spotifyItem = createMediaItem(
            id = "spotify:track:123",
            artistId = "spotify_artist_xyz",
            artistName = "  Bad Artist  ",
        )
        val otherSpotifyItem = createMediaItem(
            id = "spotify:track:456",
            artistId = "spotify_artist_abc",
            artistName = "Good Artist",
        )

        assertTrue(spotifyItem.hasBlockedArtist(blockedIds, blockedNames))
        assertFalse(otherSpotifyItem.hasBlockedArtist(blockedIds, blockedNames))
    }

    @Test
    fun hasBlockedArtistMatchesFallbackMediaMetadataArtist() {
        val blockedNames = setOf("blocked fallback")

        val fallbackItem = createMediaItem(
            id = "fb1",
            artistId = null,
            artistName = "",
            fallbackArtist = "  Blocked Fallback  ",
        )
        val otherFallbackItem = createMediaItem(
            id = "fb2",
            artistId = null,
            artistName = "",
            fallbackArtist = "Clean Artist",
        )

        assertTrue(fallbackItem.hasBlockedArtist(emptySet(), blockedNames))
        assertFalse(otherFallbackItem.hasBlockedArtist(emptySet(), blockedNames))
    }

    @Test
    fun filterBlockedArtistsRemovesMatchingItemsFromList() {
        val blockedIds = setOf("BLOCKED_ID")
        val blockedNames = setOf("blocked name")

        val item1 = createMediaItem("1", "BLOCKED_ID", "Artist 1")
        val item2 = createMediaItem("2", "UNBLOCKED_ID", "Blocked Name")
        val item3 = createMediaItem("3", "CLEAN_ID", "Clean Name")

        val result = listOf(item1, item2, item3).filterBlockedArtists(blockedIds, blockedNames)

        assertEquals(1, result.size)
        assertEquals("3", result[0].mediaId)
    }

    @Test
    fun filterBlockedArtistsMaintainsCorrectStatusIndex() {
        val blockedIds = setOf("B1")
        val blockedNames = setOf("blocked")

        val item0 = createMediaItem("0", "B1", "Artist 0")
        val item1 = createMediaItem("1", "OK1", "Artist 1")
        val item2 = createMediaItem("2", "OK2", "Artist 2")
        val item3 = createMediaItem("3", "OK3", "blocked")

        val status = Queue.Status(
            title = "Test Queue",
            items = listOf(item0, item1, item2, item3),
            mediaItemIndex = 2,
        )

        val filteredStatus = status.filterBlockedArtists(blockedIds, blockedNames)

        assertEquals(2, filteredStatus.items.size)
        assertEquals("1", filteredStatus.items[0].mediaId)
        assertEquals("2", filteredStatus.items[1].mediaId)
        // Original index 2 (item2) shifted down by 1 because item0 was removed before it
        assertEquals(1, filteredStatus.mediaItemIndex)
    }

    @Test
    fun evictBlockedArtistsSeeksAndRemovesWhenCurrentIsBlocked() {
        val item0 = createMediaItem("0", "OK0", "Artist 0")
        val item1 = createMediaItem("1", "BLOCKED", "Blocked Artist")
        val item2 = createMediaItem("2", "BLOCKED", "Blocked Artist")
        val item3 = createMediaItem("3", "OK3", "Artist 3")

        val queue = mutableListOf(item0, item1, item2, item3)
        var currentIndex = 1
        var seekedIndex = -1
        var stopped = false

        val delegate = object : BlockedArtistEvictor.Delegate {
            override fun getMediaItemCount(): Int = queue.size
            override fun getCurrentMediaItemIndex(): Int = currentIndex
            override fun getMediaItemAt(index: Int): MediaItem = queue[index]
            override fun seekToDefaultPosition(index: Int) {
                seekedIndex = index
                currentIndex = index
            }
            override fun removeMediaItem(index: Int) {
                queue.removeAt(index)
            }
            override fun stopAndClearPlayback() {
                stopped = true
                queue.clear()
            }
        }

        val evictor = BlockedArtistEvictor(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            database = io.mockk.mockk(relaxed = true),
            delegate = delegate,
        )

        evictor.evictBlockedArtists(
            blockedIds = setOf("BLOCKED"),
            blockedNames = emptySet(),
        )

        assertEquals(3, seekedIndex)
        assertEquals(2, queue.size)
        assertEquals("0", queue[0].mediaId)
        assertEquals("3", queue[1].mediaId)
        assertFalse(stopped)
    }

    @Test
    fun evictBlockedArtistsStopsWhenAllItemsBlocked() {
        val item0 = createMediaItem("0", "B1", "Artist 0")
        val item1 = createMediaItem("1", "B2", "Artist 1")

        val queue = mutableListOf(item0, item1)
        var stopped = false

        val delegate = object : BlockedArtistEvictor.Delegate {
            override fun getMediaItemCount(): Int = queue.size
            override fun getCurrentMediaItemIndex(): Int = 0
            override fun getMediaItemAt(index: Int): MediaItem = queue[index]
            override fun seekToDefaultPosition(index: Int) {}
            override fun removeMediaItem(index: Int) { queue.removeAt(index) }
            override fun stopAndClearPlayback() {
                stopped = true
                queue.clear()
            }
        }

        val evictor = BlockedArtistEvictor(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            database = io.mockk.mockk(relaxed = true),
            delegate = delegate,
        )

        evictor.evictBlockedArtists(
            blockedIds = setOf("B1", "B2"),
            blockedNames = emptySet(),
        )

        assertTrue(stopped)
        assertEquals(0, queue.size)
    }
}
