/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.constants.PersistentQueueKey
import moe.rukamori.archivetune.extensions.SilentHandler
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.toContinuationQueue
import moe.rukamori.archivetune.extensions.toPersistQueue
import moe.rukamori.archivetune.extensions.toQueue
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.PersistPlayerState
import moe.rukamori.archivetune.models.PersistQueue
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.concurrent.atomic.AtomicLong

internal data class QueueSaveSnapshot(
    val currentQueue: Queue,
    val queueTitle: String?,
    val mediaItems: List<MediaItem>,
    val currentMediaItemIndex: Int,
    val currentPosition: Long,
    val playWhenReady: Boolean,
    val repeatMode: Int,
    val shuffleModeEnabled: Boolean,
    val volume: Float,
    val playbackState: Int,
)

internal class QueuePersistenceStore(
    private val filesDir: File,
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val delegate: Delegate,
) {
    interface Delegate {
        fun isRestoringPersistentState(): Boolean
        fun getSaveSnapshot(): QueueSaveSnapshot?
        fun applyInitialQueue(
            continuationQueue: Queue,
            title: String?,
            items: List<MediaItem>,
            startIndex: Int,
            positionMs: Long,
        )
        fun applyBackfillItems(leadingItems: List<MediaItem>, trailingItems: List<MediaItem>)
        fun getMediaItemCount(): Int
        fun applyPlayerState(playerState: PersistPlayerState, restoredQueue: Boolean)
    }

    private val persistentStateLock = Any()
    private val persistentSaveGeneration = AtomicLong(0L)
    private val restoredQueueHydrationGeneration = AtomicLong(0L)
    @Volatile
    private var isHydratingRestoredQueue = false
    private var restoredQueueBackfillJob: Job? = null
    @Volatile
    internal var isRestoringPersistentState = false

    inline fun <reified T> readPersistentObject(fileName: String): T? {
        val payload = readPersistentObjectInternal(fileName) ?: return null
        check(payload is T) { "Unexpected persistent payload type for $fileName" }
        return payload
    }

    fun readPersistentObjectInternal(fileName: String): Any? {
        val persistentFile = filesDir.resolve(fileName)
        if (!persistentFile.exists() || !persistentFile.isFile) return null

        return synchronized(persistentStateLock) {
            runCatching {
                persistentFile.inputStream().use { fis ->
                    ObjectInputStream(fis).use { input -> input.readObject() }
                }
            }.onFailure {
                Timber.tag(TAG).w(it, "Failed to read persistent file: $fileName")
            }.getOrNull()
        }
    }

    fun writePersistentObject(fileName: String, payload: Serializable) {
        val persistentFile = filesDir.resolve(fileName)
        val tempFile = filesDir.resolve("$fileName.tmp")

        synchronized(persistentStateLock) {
            runCatching {
                FileOutputStream(tempFile).use { fos ->
                    ObjectOutputStream(fos).use { output ->
                        output.writeObject(payload)
                        output.flush()
                    }
                }
                if (!tempFile.renameTo(persistentFile)) {
                    if (persistentFile.exists() && !persistentFile.delete()) error("Could not replace $fileName")
                    if (!tempFile.renameTo(persistentFile)) error("Could not atomically move $fileName")
                }
            }.onFailure {
                runCatching { tempFile.delete() }
                reportException(it)
            }
        }
    }

    fun clearPersistedQueueFiles() {
        persistentSaveGeneration.incrementAndGet()
        synchronized(persistentStateLock) {
            listOf(PERSISTENT_QUEUE_FILE, PERSISTENT_PLAYER_STATE_FILE, PERSISTENT_AUTOMIX_FILE).forEach { fileName ->
                val persistentFile = filesDir.resolve(fileName)
                val tempFile = filesDir.resolve("$fileName.tmp")
                runCatching {
                    if (persistentFile.exists() && !persistentFile.delete()) {
                        Timber.tag(TAG).w("Failed to delete persistent file: $fileName")
                    }
                    if (tempFile.exists() && !tempFile.delete()) {
                        Timber.tag(TAG).w("Failed to delete temporary persistent file: $fileName")
                    }
                }.onFailure {
                    Timber.tag(TAG).w(it, "Failed to clear persistent file: $fileName")
                }
            }
        }
    }

    fun cancelRestoredQueueHydration() {
        restoredQueueHydrationGeneration.incrementAndGet()
        restoredQueueBackfillJob?.cancel()
        restoredQueueBackfillJob = null
        isHydratingRestoredQueue = false
    }

    suspend fun saveQueueToDisk() {
        val saveGeneration = persistentSaveGeneration.get()
        val snapshot = withContext(Dispatchers.Main.immediate) {
            if (
                saveGeneration != persistentSaveGeneration.get() ||
                isRestoringPersistentState ||
                delegate.isRestoringPersistentState() ||
                isHydratingRestoredQueue
            ) {
                return@withContext null
            }

            val queueSnapshot = delegate.getSaveSnapshot() ?: return@withContext null
            val mediaItemsSnapshot = queueSnapshot.mediaItems.mapNotNull { toPersistableMetadata(it) }
            if (mediaItemsSnapshot.isEmpty()) return@withContext null

            val persistQueue = queueSnapshot.currentQueue.toPersistQueue(
                title = queueSnapshot.queueTitle,
                items = mediaItemsSnapshot,
                mediaItemIndex = queueSnapshot.currentMediaItemIndex,
                position = queueSnapshot.currentPosition,
            )
            val persistPlayerState = PersistPlayerState(
                playWhenReady = queueSnapshot.playWhenReady,
                repeatMode = queueSnapshot.repeatMode,
                shuffleModeEnabled = queueSnapshot.shuffleModeEnabled,
                volume = queueSnapshot.volume,
                currentPosition = queueSnapshot.currentPosition,
                currentMediaItemIndex = queueSnapshot.currentMediaItemIndex,
                playbackState = queueSnapshot.playbackState,
            )

            persistQueue to persistPlayerState
        } ?: return

        withContext(ioDispatcher) {
            if (saveGeneration != persistentSaveGeneration.get()) return@withContext
            writePersistentObject(PERSISTENT_QUEUE_FILE, snapshot.first)
            if (saveGeneration != persistentSaveGeneration.get()) return@withContext
            writePersistentObject(PERSISTENT_PLAYER_STATE_FILE, snapshot.second)
        }
    }

    suspend fun restorePersistentQueue(persistedQueue: PersistQueue) {
        cancelRestoredQueueHydration()
        val hydrationGeneration = restoredQueueHydrationGeneration.incrementAndGet()
        isHydratingRestoredQueue = true

        val itemQueue = persistedQueue.toQueue()
        val continuationQueue = persistedQueue.toContinuationQueue()
        val hideExplicit = dataStore.get(HideExplicitKey, false)
        val hideVideo = dataStore.get(HideVideoKey, false)
        val initialStatus = itemQueue.getInitialStatus().filterExplicit(hideExplicit).filterVideo(hideVideo)

        withContext(Dispatchers.Main) {
            val items = initialStatus.items
            if (items.isEmpty()) {
                if (hydrationGeneration == restoredQueueHydrationGeneration.get()) {
                    isHydratingRestoredQueue = false
                }
                return@withContext
            }

            val fullIndex = initialStatus.mediaItemIndex.coerceIn(0, items.lastIndex)
            val windowStart = (fullIndex - 20).coerceAtLeast(0)
            val windowEnd = (fullIndex + 50).coerceAtMost(items.size)

            val initialChunk = items.subList(windowStart, windowEnd)
            val relativeIndex = (fullIndex - windowStart).coerceIn(0, initialChunk.lastIndex)

            delegate.applyInitialQueue(
                continuationQueue = continuationQueue,
                title = initialStatus.title,
                items = initialChunk,
                startIndex = relativeIndex,
                positionMs = initialStatus.position,
            )

            if (items.size > initialChunk.size) {
                restoredQueueBackfillJob = scope.launch(SilentHandler) {
                    try {
                        delay(2000)
                        if (!isActive || delegate.getMediaItemCount() == 0) return@launch
                        val leading = if (windowStart > 0) items.subList(0, windowStart) else emptyList()
                        val trailing = if (windowEnd < items.size) items.subList(windowEnd, items.size) else emptyList()
                        delegate.applyBackfillItems(leading, trailing)
                    } finally {
                        if (hydrationGeneration == restoredQueueHydrationGeneration.get()) {
                            isHydratingRestoredQueue = false
                            restoredQueueBackfillJob = null
                            if (isActive && dataStore.get(PersistentQueueKey, true) && delegate.getMediaItemCount() > 0) {
                                saveQueueToDisk()
                            }
                        }
                    }
                }
            } else {
                if (hydrationGeneration == restoredQueueHydrationGeneration.get()) {
                    isHydratingRestoredQueue = false
                }
            }
        }
    }

    suspend fun restorePersistentPlayerState(playerState: PersistPlayerState, restoredQueue: Boolean) {
        withContext(Dispatchers.Main) {
            delegate.applyPlayerState(playerState, restoredQueue)
        }
    }

    fun toPersistableMetadata(mediaItem: MediaItem): MediaMetadata? =
        Companion.toPersistableMetadata(mediaItem)

    companion object {
        private const val TAG = "QueuePersistenceStore"
        const val PERSISTENT_QUEUE_FILE = "queue.bin"
        const val PERSISTENT_PLAYER_STATE_FILE = "player_state.bin"
        const val PERSISTENT_AUTOMIX_FILE = "automix.bin"

        fun toPersistableMetadata(mediaItem: MediaItem): MediaMetadata? {
            val tagged = mediaItem.metadata
            if (tagged != null) return tagged

            val id = mediaItem.mediaId.trim().ifBlank {
                mediaItem.localConfiguration?.uri?.toString()?.trim().orEmpty()
            }.takeIf { it.isNotBlank() } ?: return null

            val title = mediaItem.mediaMetadata.title?.toString()?.trim()?.takeIf { !it.isNullOrBlank() } ?: id
            val artistText = mediaItem.mediaMetadata.artist?.toString()?.trim()?.takeIf { !it.isNullOrBlank() }
                ?: mediaItem.mediaMetadata.subtitle?.toString()?.trim()?.takeIf { !it.isNullOrBlank() }
            val artists = artistText?.split(",")?.mapNotNull { it.trim().takeIf(String::isNotBlank) }
                ?.map { MediaMetadata.Artist(id = null, name = it) }.orEmpty()

            val albumTitle = mediaItem.mediaMetadata.albumTitle?.toString()?.trim()?.takeIf { !it.isNullOrBlank() }
            val album = albumTitle?.let { MediaMetadata.Album(id = it, title = it) }

            return MediaMetadata(
                id = id,
                title = title,
                artists = artists,
                duration = -1,
                thumbnailUrl = mediaItem.mediaMetadata.artworkUri?.toString(),
                album = album,
                explicit = false,
                liked = false,
                likedDate = null,
                inLibrary = null,
            )
        }
    }
}
