package moe.rukamori.archivetune.playback.recovery

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import moe.rukamori.archivetune.playback.PlaybackRecoveryEngine
import moe.rukamori.archivetune.playback.RecoveryDecision
import moe.rukamori.archivetune.spotify.SpotifyPlaceholders
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class StreamErrorRouterUnmatchedTest {
    private val playerActions = mockk<PlaybackRecoveryEngine.PlayerActions>(relaxed = true)
    private var current = MediaItem.Builder().setMediaId("spotify:unmatched-a").build()

    private val router =
        StreamErrorRouter(
            scope = CoroutineScope(Dispatchers.Unconfined),
            playerActions = playerActions,
            cacheOps = mockk(relaxed = true),
            networkState = mockk(relaxed = true),
            loginPrompt = mockk(relaxed = true),
            networkStallReviver = mockk(relaxed = true),
        )

    private val notFound = TestPlaybackException(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)

    @Before
    fun setUp() {
        SpotifyPlaceholders.isOnline = { true }
        every { playerActions.currentMediaItem } answers { current }
        listOf("spotify:unmatched-a", "spotify:unmatched-b").forEach(SpotifyPlaceholders::markUnmatched)
    }

    private fun failOn(mediaId: String): RecoveryDecision {
        current = MediaItem.Builder().setMediaId(mediaId).build()
        return router.onPlayerError(notFound)
    }

    @Test
    fun aSongWithNoYouTubeVersionIsSkipped() {
        every { playerActions.nextMediaItemIndex } returns 4

        assertEquals(RecoveryDecision.SkipNext, failOn("spotify:unmatched-a"))

        verify { playerActions.seekTo(4, C.TIME_UNSET) }
    }

    @Test
    fun playbackStopsAtTheEndOfTheQueue() {
        every { playerActions.nextMediaItemIndex } returns C.INDEX_UNSET

        assertEquals(RecoveryDecision.Stop, failOn("spotify:unmatched-a"))

        verify { playerActions.pause() }
    }

    @Test
    fun aRepeatingQueueOfUnavailableSongsIsSkippedOnlyOnce() {
        every { playerActions.nextMediaItemIndex } returns 1

        assertEquals(RecoveryDecision.SkipNext, failOn("spotify:unmatched-a"))
        assertEquals(RecoveryDecision.SkipNext, failOn("spotify:unmatched-b"))
        // The run is back at the first song, so nothing in the queue can play.
        assertEquals(RecoveryDecision.Stop, failOn("spotify:unmatched-a"))
    }

    private class TestPlaybackException(
        errorCode: Int,
    ) : PlaybackException("test", null, errorCode, mockk(relaxed = true), 0L)
}
