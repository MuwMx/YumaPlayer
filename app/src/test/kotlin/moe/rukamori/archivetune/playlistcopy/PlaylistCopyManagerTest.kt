package moe.rukamori.archivetune.playlistcopy

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistCopyManagerTest {
    private val copier = mockk<PlaylistCopier>()
    private val manager = PlaylistCopyManager(copier)

    private val request =
        PlaylistCopyRequest(
            PlaylistCopySource.Spotify("sp", "Road trip", thumbnailUrl = null),
            PlaylistCopyDestination.NewPersonal("Road trip", sourceSpotifyId = "sp"),
        )

    private suspend fun awaitState(predicate: (PlaylistCopyState) -> Boolean) =
        withTimeout(5_000) { manager.state.first(predicate) }

    @Test
    fun publishesTheResultOfACopy() =
        runBlocking {
            val result = PlaylistCopyResult("copy", added = 3, failed = emptyList())
            coEvery { copier.copy(request, any()) } returns PlaylistCopyState.Success(result)

            manager.start(request)

            assertEquals(PlaylistCopyState.Success(result), awaitState { it is PlaylistCopyState.Success })
        }

    @Test
    fun retryAddsTheMissingSongsToThePlaylistThatWasJustWritten() =
        runBlocking {
            val first = PlaylistCopyResult("copy", added = 2, failed = emptyList())
            val requests = java.util.Collections.synchronizedList(mutableListOf<PlaylistCopyRequest>())
            coEvery { copier.copy(capture(requests), any()) } returns PlaylistCopyState.Success(first)
            manager.start(request)
            awaitState { it is PlaylistCopyState.Success }

            manager.retry()

            withTimeout(5_000) { while (requests.size < 2) delay(10) }
            assertEquals(PlaylistCopyDestination.Existing("copy"), requests[1].destination)
        }

    @Test
    fun aReplacedCopyThatIsStillFinishingCannotOverwriteTheNewOne() =
        runBlocking {
            val firstStarted = CompletableDeferred<Unit>()
            val letFirstFinish = CompletableDeferred<Unit>()
            val late = PlaylistCopyResult("late", added = 1, failed = emptyList())
            val second = PlaylistCopyResult("second", added = 1, failed = emptyList())
            val otherRequest = request.copy(destination = PlaylistCopyDestination.NewPersonal("Other"))
            coEvery { copier.copy(request, any()) } coAnswers {
                firstStarted.complete(Unit)
                // Work that is already past its last cancellation point when the copy is replaced.
                withContext(NonCancellable) { letFirstFinish.await() }
                PlaylistCopyState.Success(late)
            }
            coEvery { copier.copy(otherRequest, any()) } returns PlaylistCopyState.Success(second)

            manager.start(request)
            withTimeout(5_000) { firstStarted.await() }
            manager.start(otherRequest)
            awaitState { it == PlaylistCopyState.Success(second) }
            letFirstFinish.complete(Unit)
            delay(100)

            assertEquals(PlaylistCopyState.Success(second), manager.state.value)
        }

    @Test
    fun dismissStopsTheCopyAndClearsTheState() =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            coEvery { copier.copy(request, any()) } coAnswers {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } catch (e: CancellationException) {
                    cancelled.complete(Unit)
                    throw e
                }
            }

            manager.start(request)
            withTimeout(5_000) { started.await() }
            manager.dismiss()

            withTimeout(5_000) { cancelled.await() }
            assertEquals(PlaylistCopyState.Idle, manager.state.value)
        }

    @Test
    fun editableCopyOfASpotifyPlaylistGoesToItsLinkedPlaylist() =
        runBlocking {
            val spotify = PlaylistCopySource.Spotify("sp", "Road trip", thumbnailUrl = null)
            val result = PlaylistCopyResult("linked", added = 1, failed = emptyList())
            coEvery { copier.editableCopyDestination("sp", "Road trip") } returns PlaylistCopyDestination.Existing("linked")
            coEvery { copier.copy(PlaylistCopyRequest(spotify, PlaylistCopyDestination.Existing("linked")), any()) } returns
                PlaylistCopyState.Success(result)

            manager.startEditableCopy(spotify)

            assertEquals(PlaylistCopyState.Success(result), awaitState { it is PlaylistCopyState.Success })
        }

    @Test
    fun aCopyWhoseDestinationCannotBeResolvedFails() =
        runBlocking {
            val spotify = PlaylistCopySource.Spotify("sp", "Road trip", thumbnailUrl = null)
            coEvery { copier.editableCopyDestination("sp", "Road trip") } throws IllegalStateException("database closed")

            manager.startEditableCopy(spotify)

            assertEquals(PlaylistCopyState.Failure(), awaitState { it is PlaylistCopyState.Failure })
        }
}
