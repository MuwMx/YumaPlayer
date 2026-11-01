/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.listeners

import android.net.Uri
import android.os.Bundle
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.listeners.MusicServicePlayerListeners
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.playback.queues.SpotifyQueue
import moe.rukamori.archivetune.utils.PreferenceStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueBackwardPagingTest {

    private lateinit var player: Player
    private lateinit var playerDelegate: MusicServicePlayerListeners.PlayerDelegate
    private lateinit var queueDelegate: MusicServicePlayerListeners.QueueDelegate
    private lateinit var currentQueue: Queue

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
        every { anyConstructed<Bundle>().getBoolean(any(), any()) } returns false
        player = mockk(relaxed = true)
        every { player.playbackState } returns Player.STATE_READY
        playerDelegate =
            mockk<MusicServicePlayerListeners.PlayerDelegate>(relaxed = true).also { delegate ->
                every { delegate.player } returns player
            }
        queueDelegate =
            mockk {
                every { currentQueue } answers { this@QueueBackwardPagingTest.currentQueue }
                every { isInitializingQueue } returns false
                every { dataStore } returns mockk<DataStore<Preferences>>(relaxed = true)
                every { setPreviousQueueLoading(any()) } just Runs
            }
        currentQueue = FakeSpotifyQueue(mutableListOf())
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun request_prependsBatchAtZeroWithoutPlaybackMutations() =
        runTest(UnconfinedTestDispatcher()) {
            val expected = listOf(item("a"), item("b"))
            val queue = FakeSpotifyQueue(mutableListOf(expected))
            currentQueue = queue
            val paging = paging(this)

            assertTrue(paging.request())
            advanceUntilIdle()

            assertEquals(1, queue.previousPageCalls)
            verify(exactly = 1) { player.addMediaItems(0, expected) }
            verify(exactly = 0) { player.prepare() }
            verify(exactly = 0) { player.play() }
            verify(exactly = 0) { player.pause() }
            verify(exactly = 0) { player.seekTo(any<Long>()) }
            verify(exactly = 0) { player.seekTo(any<Int>(), any<Long>()) }
            verify(exactly = 0) { player.seekToNext() }
            verify(exactly = 0) { player.setMediaItem(any<MediaItem>()) }
            verify(exactly = 0) { player.setMediaItems(any<List<MediaItem>>()) }
            verify { queueDelegate.setPreviousQueueLoading(false) }
        }

    @Test
    fun request_rejectsConcurrentBackwardRequestForSameQueue() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = CompletableDeferred<Unit>()
            val queue = FakeSpotifyQueue(mutableListOf(listOf(item("a"))), gate = gate)
            currentQueue = queue
            val paging = paging(this)

            assertTrue(paging.request())
            assertFalse(paging.request())

            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, queue.previousPageCalls)
            verify(exactly = 1) { player.addMediaItems(0, any<List<MediaItem>>()) }
        }

    @Test
    fun request_dropsBatchWhenQueueWasReplacedWhileLoading() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = CompletableDeferred<Unit>()
            val queue = FakeSpotifyQueue(mutableListOf(listOf(item("a"))), gate = gate)
            currentQueue = queue
            val paging = paging(this)

            assertTrue(paging.request())
            currentQueue = FakeSpotifyQueue(mutableListOf())
            gate.complete(Unit)
            advanceUntilIdle()

            verify(exactly = 0) { player.addMediaItems(0, any<List<MediaItem>>()) }
            verify { queueDelegate.setPreviousQueueLoading(false) }
        }

    @Test
    fun request_appliesExplicitFilterAndSkipsFullyFilteredBatch() =
        runTest(UnconfinedTestDispatcher()) {
            mockkObject(PreferenceStore)
            every { PreferenceStore.get<Boolean>(HideExplicitKey) } returns true
            every { PreferenceStore.get<Boolean>(HideVideoKey) } returns false

            val explicitItem = item("explicit", explicit = true)
            val cleanItem = item("clean")
            val queue = FakeSpotifyQueue(mutableListOf(listOf(explicitItem), listOf(cleanItem)))
            currentQueue = queue
            val paging = paging(this)

            assertTrue(paging.request())
            advanceUntilIdle()

            assertEquals(2, queue.previousPageCalls)
            verify(exactly = 1) { player.addMediaItems(0, listOf(cleanItem)) }
        }

    @Test
    fun request_rejectsQueueWithoutPreviousPageIdlePlayerAndNonSpotifyQueue() =
        runTest(UnconfinedTestDispatcher()) {
            val paging = paging(this)

            currentQueue = FakeSpotifyQueue(mutableListOf())
            assertFalse(paging.request())

            currentQueue = FakeSpotifyQueue(mutableListOf(listOf(item("a"))))
            every { player.playbackState } returns Player.STATE_IDLE
            assertFalse(paging.request())

            every { player.playbackState } returns Player.STATE_READY
            currentQueue = mockk<Queue>(relaxed = true)
            assertFalse(paging.request())

            verify(exactly = 0) { player.addMediaItems(0, any<List<MediaItem>>()) }
        }

    @Test
    fun request_retainsResolvedBatchWhenPlayerTurnsIdleAndRetriesWithoutResolvingAgain() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = CompletableDeferred<Unit>()
            val expected = listOf(item("a"))
            val queue = FakeSpotifyQueue(mutableListOf(expected), gate = gate)
            currentQueue = queue
            val paging = paging(this)

            assertTrue(paging.request())
            every { player.playbackState } returns Player.STATE_IDLE
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, queue.previousPageCalls)
            verify(exactly = 0) { player.addMediaItems(0, any<List<MediaItem>>()) }
            assertTrue(paging.hasPendingBatch())

            every { player.playbackState } returns Player.STATE_READY
            assertTrue(paging.request())

            assertEquals(1, queue.previousPageCalls)
            assertFalse(paging.hasPendingBatch())
            verify(exactly = 1) { player.addMediaItems(0, expected) }
        }

    @Test
    fun request_dropsRetainedBatchWhenSameQueueGenerationAdvancesDuringInitialization() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = CompletableDeferred<Unit>()
            val stale = listOf(item("stale"))
            val fresh = listOf(item("fresh"))
            val queue = FakeSpotifyQueue(mutableListOf(stale), gate = gate)
            currentQueue = queue
            val paging = paging(this)

            assertTrue(paging.request())
            every { queueDelegate.isInitializingQueue } returns true
            gate.complete(Unit)
            advanceUntilIdle()

            verify(exactly = 0) { player.addMediaItems(0, any<List<MediaItem>>()) }
            assertTrue(paging.hasPendingBatch())

            queue.generation = 1L
            assertFalse(paging.hasPendingBatch())

            every { queueDelegate.isInitializingQueue } returns false
            queue.batches.add(fresh)
            assertTrue(paging.request())
            advanceUntilIdle()

            assertEquals(2, queue.previousPageCalls)
            verify(exactly = 1) { player.addMediaItems(0, fresh) }
        }

    @Test
    fun request_discardsBatchResolvedBeforeSameQueueGenerationAdvance() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = CompletableDeferred<Unit>()
            val stale = listOf(item("stale"))
            val fresh = listOf(item("fresh"))
            val queue = FakeSpotifyQueue(mutableListOf(stale), gate = gate)
            currentQueue = queue
            val paging = paging(this)

            assertTrue(paging.request())
            queue.generation = 1L
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, queue.previousPageCalls)
            verify(exactly = 0) { player.addMediaItems(0, any<List<MediaItem>>()) }
            assertFalse(paging.hasPendingBatch())

            queue.generation = 2L
            queue.batches.add(fresh)
            assertTrue(paging.request())
            advanceUntilIdle()

            assertEquals(2, queue.previousPageCalls)
            verify(exactly = 1) { player.addMediaItems(0, fresh) }
        }

    @Test
    fun request_scopeCancellationStopsJobAndClearsLoading() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = CompletableDeferred<Unit>()
            val queue = FakeSpotifyQueue(mutableListOf(listOf(item("a"))), gate = gate)
            currentQueue = queue
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val paging = QueueBackwardPaging(scope, playerDelegate, queueDelegate, UnconfinedTestDispatcher(testScheduler))

            assertTrue(paging.request())
            scope.cancel()
            gate.complete(Unit)
            advanceUntilIdle()

            verify(exactly = 0) { player.addMediaItems(0, any<List<MediaItem>>()) }
            verify { queueDelegate.setPreviousQueueLoading(false) }
        }

    private fun paging(scope: CoroutineScope): QueueBackwardPaging =
        QueueBackwardPaging(
            scope = scope,
            playerDelegate = playerDelegate,
            queueDelegate = queueDelegate,
            ioDispatcher = UnconfinedTestDispatcher(),
        )

    private class FakeSpotifyQueue(
        val batches: MutableList<List<MediaItem>>,
        private val gate: CompletableDeferred<Unit>? = null,
    ) : SpotifyQueue {
        var previousPageCalls = 0
            private set

        var generation = 0L

        override val contextGeneration: Long
            get() = generation

        override val preloadItem: MediaMetadata? = null

        override suspend fun getInitialStatus(): Queue.Status =
            Queue.Status(title = null, items = emptyList(), mediaItemIndex = 0)

        override fun hasNextPage(): Boolean = false

        override suspend fun nextPage(): List<MediaItem> = emptyList()

        override fun hasPreviousPage(): Boolean = batches.isNotEmpty()

        override suspend fun previousPage(): SpotifyQueue.PreviousPage {
            previousPageCalls += 1
            val pageGeneration = generation
            gate?.await()
            val items = if (batches.isEmpty()) emptyList() else batches.removeAt(0)
            return SpotifyQueue.PreviousPage(items, pageGeneration)
        }
    }

    private companion object {
        fun item(id: String, explicit: Boolean = false): MediaItem =
            MediaMetadata(
                id = id,
                title = id,
                artists = emptyList(),
                duration = 180,
                explicit = explicit,
            ).toMediaItem()
    }
}
