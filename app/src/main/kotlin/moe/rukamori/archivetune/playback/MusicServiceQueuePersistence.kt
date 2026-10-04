/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

import androidx.media3.common.MediaItem
import moe.rukamori.archivetune.extensions.currentMetadata
import moe.rukamori.archivetune.extensions.mediaItems
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.PersistPlayerState
import moe.rukamori.archivetune.models.PersistQueue
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.utils.dataStore
import java.io.Serializable

internal fun MusicService.createQueuePersistenceStore(): QueuePersistenceStore {
    val service = this
    return QueuePersistenceStore(
        filesDir = filesDir,
        dataStore = dataStore,
        scope = scope,
        delegate = object : QueuePersistenceStore.Delegate {
            override fun isRestoringPersistentState(): Boolean =
                service.isRestoringPersistentState

            override fun getSaveSnapshot(): QueueSaveSnapshot? {
                if (service.isRestoringPersistentState || service.player.mediaItemCount == 0) return null
                return QueueSaveSnapshot(
                    currentQueue = service.currentQueue,
                    queueTitle = service.queueTitle,
                    mediaItems = service.player.mediaItems,
                    currentMediaItemIndex = service.player.currentMediaItemIndex,
                    currentPosition = service.player.currentPosition,
                    playWhenReady = service.player.playWhenReady,
                    repeatMode = service.player.repeatMode,
                    shuffleModeEnabled = service.player.shuffleModeEnabled,
                    volume = service.playerVolume.value,
                    playbackState = service.player.playbackState,
                )
            }

            override fun applyInitialQueue(
                continuationQueue: Queue,
                title: String?,
                items: List<MediaItem>,
                startIndex: Int,
                positionMs: Long,
            ) {
                service.currentQueue = continuationQueue
                service.queueTitle = title
                service.player.setMediaItems(items, startIndex, positionMs)
                service.player.prepare()
                service.player.playWhenReady = false
                service.currentMediaMetadata.value = service.player.currentMetadata
                service.updateNotification()
            }

            override fun applyBackfillItems(
                leadingItems: List<MediaItem>,
                trailingItems: List<MediaItem>,
            ) {
                if (leadingItems.isNotEmpty()) {
                    service.player.addMediaItems(0, leadingItems)
                }
                if (trailingItems.isNotEmpty()) {
                    service.player.addMediaItems(trailingItems)
                }
            }

            override fun getMediaItemCount(): Int =
                service.player.mediaItemCount

            override fun applyPlayerState(
                playerState: PersistPlayerState,
                restoredQueue: Boolean,
            ) {
                service.player.repeatMode = playerState.repeatMode
                service.player.shuffleModeEnabled = playerState.shuffleModeEnabled
                service.playerVolume.value = playerState.volume.coerceIn(0f, 1f)

                if (service.player.mediaItemCount > 0) {
                    val index =
                        when {
                            restoredQueue -> {
                                service.player.currentMediaItemIndex.coerceIn(0, service.player.mediaItemCount - 1)
                            }
                            playerState.currentMediaItemIndex in 0 until service.player.mediaItemCount -> {
                                playerState.currentMediaItemIndex
                            }
                            else -> {
                                service.player.currentMediaItemIndex.coerceIn(0, service.player.mediaItemCount - 1)
                            }
                        }
                    service.player.seekTo(index, playerState.currentPosition.coerceAtLeast(0L))
                }

                service.player.playWhenReady = false
                service.abandonAudioFocus()
                service.currentMediaMetadata.value =
                    service.player.currentMetadata.takeIf { service.player.mediaItemCount > 0 }
                service.updateNotification()
            }
        },
    )
}

internal inline fun <reified T> MusicService.readPersistentObject(fileName: String): T? =
    queuePersistenceStore.readPersistentObject(fileName)

internal fun MusicService.clearPersistedQueueFiles() {
    queuePersistenceStore.clearPersistedQueueFiles()
}

internal fun MusicService.writePersistentObject(
    fileName: String,
    payload: Serializable,
) {
    queuePersistenceStore.writePersistentObject(fileName, payload)
}

internal fun MediaItem.toPersistableMetadata(): MediaMetadata? =
    QueuePersistenceStore.toPersistableMetadata(this)

internal suspend fun MusicService.saveQueueToDisk() {
    queuePersistenceStore.saveQueueToDisk()
}

internal suspend fun MusicService.restorePersistentQueue(persistedQueue: PersistQueue) {
    queuePersistenceStore.restorePersistentQueue(persistedQueue)
}

internal suspend fun MusicService.restorePersistentPlayerState(
    playerState: PersistPlayerState,
    restoredQueue: Boolean,
) {
    queuePersistenceStore.restorePersistentPlayerState(playerState, restoredQueue)
}
