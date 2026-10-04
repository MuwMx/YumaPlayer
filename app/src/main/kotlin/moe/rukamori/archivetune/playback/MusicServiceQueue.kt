/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.extensions.currentMetadata
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.together.AddTrackMode
import moe.rukamori.archivetune.together.ControlAction
import moe.rukamori.archivetune.together.TogetherTrack
import moe.rukamori.archivetune.utils.dataStore
import java.util.WeakHashMap

private val queueManagers = WeakHashMap<MusicService, PlaybackQueueManager>()

internal val MusicService.queueManager: PlaybackQueueManager
    get() = synchronized(queueManagers) {
        queueManagers.getOrPut(this) {
            createPlaybackQueueManager()
        }
    }

@OptIn(UnstableApi::class)
private fun MusicService.createPlaybackQueueManager(): PlaybackQueueManager {
    val service = this
    val queueHolder = object : PlaybackQueueManager.QueueHolder {
        override var currentQueue: Queue
            get() = service.currentQueue
            set(value) {
                service.currentQueue = value
            }
        override var queueTitle: String?
            get() = service.queueTitle
            set(value) {
                service.queueTitle = value
            }
    }
    return PlaybackQueueManager(
        queueHolder = queueHolder,
        persistenceStore = service.queuePersistenceStore,
        scope = service.scope,
        ioScope = service.ioScope,
        dataStore = service.dataStore,
        database = service.database,
        syncUtils = service.syncUtils,
        delegate = object : PlaybackQueueManager.Delegate {
            override fun getMediaItemCount(): Int = service.player.mediaItemCount
            override fun getCurrentMediaItemIndex(): Int = service.player.currentMediaItemIndex
            override fun getMediaItemAt(index: Int): MediaItem = service.player.getMediaItemAt(index)
            override fun getCurrentTimeline(): Timeline = service.player.currentTimeline
            override fun getPlaybackState(): Int = service.player.playbackState
            override fun getCurrentMetadata(): MediaMetadata? = service.player.currentMetadata
            override fun isShuffleModeEnabled(): Boolean = service.player.shuffleModeEnabled
            override fun setShuffleModeEnabled(enabled: Boolean) {
                service.player.shuffleModeEnabled = enabled
            }
            override fun setShuffleOrder(shuffleOrder: ShuffleOrder) {
                service.localPlayer.setShuffleOrder(shuffleOrder)
            }
            override fun setMediaItem(item: MediaItem) {
                service.player.setMediaItem(item)
            }
            override fun setMediaItems(items: List<MediaItem>, index: Int, positionMs: Long) {
                service.player.setMediaItems(items, index, positionMs)
            }
            override fun addMediaItems(items: List<MediaItem>) {
                service.player.addMediaItems(items)
            }
            override fun addMediaItems(index: Int, items: List<MediaItem>) {
                service.player.addMediaItems(index, items)
            }
            override fun removeMediaItem(index: Int) {
                service.player.removeMediaItem(index)
            }
            override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
                service.player.removeMediaItems(fromIndex, toIndex)
            }
            override fun clearMediaItems() {
                service.player.clearMediaItems()
            }
            override fun prepare() {
                service.player.prepare()
            }
            override fun play() {
                service.player.play()
            }
            override fun stop() {
                service.player.stop()
            }
            override fun seekToNext() {
                service.player.seekToNext()
            }
            override fun setPlayWhenReady(playWhenReady: Boolean) {
                service.player.playWhenReady = playWhenReady
            }
            override fun isPlayWhenReady(): Boolean = service.player.playWhenReady

            override fun ensureScopesActive() = service.ensureScopesActive()
            override fun cancelIdleStop() = service.cancelIdleStop()
            override fun promoteToStartedService() = service.promoteToStartedService()
            override fun ensureStartedAsForeground() = service.ensureStartedAsForeground()
            override fun cancelRestoredQueueHydration() = service.cancelRestoredQueueHydration()
            override fun cancelCrossfade(resetVolume: Boolean, resetPauseAtEnd: Boolean) =
                service.cancelCrossfade(resetVolume = resetVolume, resetPauseAtEnd = resetPauseAtEnd)
            override fun setSuppressAutoPlayback(suppress: Boolean) {
                service.suppressAutoPlayback = suppress
            }
            override fun setInitializingQueue(isInitializing: Boolean) {
                service.isInitializingQueue = isInitializing
            }
            override fun setInfiniteQueueLoading(isLoading: Boolean) {
                service.infiniteQueueLoading.value = isLoading
            }
            override fun cancelNetworkStallRecovery() {
                service.networkStallRecoveryJob?.cancel()
                service.networkStallRecoveryJob = null
            }
            override fun setWaitingForNetworkConnection(waiting: Boolean) {
                service.waitingForNetworkConnection.value = waiting
            }
            override fun clearCurrentMediaMetadata() {
                service.currentMediaMetadata.value = null
            }
            override fun abandonAudioFocus() = service.abandonAudioFocus()
            override fun closeAudioEffectSession() = service.closeAudioEffectSession()
            override fun resetConsecutivePlaybackErr() {
                service.consecutivePlaybackErr = 0
            }

            override fun recordAutoAddedMediaId(mediaId: String) {
                service.autoAddedMediaIds.add(mediaId)
            }
            override fun clearAutoAddedMediaIds() {
                service.autoAddedMediaIds.clear()
            }

            override fun getTogetherSessionState(): Any? = service.togetherSessionState.value
            override fun isTogetherApplyingRemote(): Boolean = service.isTogetherApplyingRemote()
            override fun showTogetherNotice(message: String, key: String) =
                service.showTogetherNotice(message, key = key)
            override fun requestTogetherControl(action: ControlAction) = service.requestTogetherControl(action)
            override fun requestTogetherAddTrack(track: TogetherTrack, mode: AddTrackMode) =
                service.requestTogetherAddTrack(track, mode)

            override fun getString(resId: Int): String = service.getString(resId)
            override fun showToast(resId: Int) {
                Toast.makeText(service, service.getString(resId), Toast.LENGTH_SHORT).show()
            }

            override fun isCurrentPlaybackItemLocal(metadata: MediaMetadata): Boolean =
                service.isCurrentPlaybackItemLocal(metadata)
            override fun isCurrentSongLocal(): Boolean = service.currentSong.value?.song?.isLocal == true
            override fun getCurrentSong(): Song? = service.currentSong.value
            override fun getCurrentMediaMetadata(): MediaMetadata? = service.currentMediaMetadata.value
            override fun downloadSong(song: SongEntity) {
                val downloadRequest =
                    DownloadRequest
                        .Builder(song.id, song.id.toUri())
                        .setCustomCacheKey(song.id)
                        .setData(song.title.toByteArray())
                        .build()
                DownloadService.sendAddDownload(
                    service,
                    ExoDownloadService::class.java,
                    downloadRequest,
                    false,
                )
            }
            override suspend fun resolveVoiceMediaItems(query: String): List<MediaItem> =
                service.mediaLibrarySessionCallback.resolveVoiceMediaItems(query)
        },
    )
}

internal fun MusicService.playQueueInternal(
    queue: Queue,
    playWhenReady: Boolean = true,
) = queueManager.playQueue(queue, playWhenReady)

@OptIn(UnstableApi::class)
internal fun MusicService.applyCurrentFirstShuffleOrder() =
    queueManager.applyCurrentFirstShuffleOrder()

@OptIn(UnstableApi::class)
internal fun MusicService.buildPlayNextShuffleOrder(
    currentIndex: Int,
    insertionIndex: Int,
    insertionCount: Int,
): DefaultShuffleOrder? =
    queueManager.buildPlayNextShuffleOrder(currentIndex, insertionIndex, insertionCount)

internal fun MusicService.startRadioSeamlesslyInternal() =
    queueManager.startRadioSeamlessly()

internal fun MusicService.clearAutomixInternal() =
    queueManager.clearAutomix()

internal fun MusicService.clearQueueInternal() =
    queueManager.clearQueue()

internal fun MusicService.onInfiniteQueueDisabledInternal() =
    queueManager.onInfiniteQueueDisabled()

internal fun MusicService.onInfiniteQueueEnabledInternal() =
    queueManager.onInfiniteQueueEnabled()

internal fun MusicService.cancelInfiniteQueueBootstrap() =
    queueManager.cancelInfiniteQueueBootstrap()

internal fun MusicService.stopAndClearPlaybackInternal(clearPersistentState: Boolean = false) =
    queueManager.stopAndClearPlayback(clearPersistentState)

internal fun MusicService.playNextInternal(items: List<MediaItem>) =
    queueManager.playNext(items)

internal fun MusicService.addToQueueInternal(items: List<MediaItem>) =
    queueManager.addToQueue(items)

internal fun MusicService.playFromVoiceSearchInternal(query: String) =
    queueManager.playFromVoiceSearch(query)

internal fun MusicService.toggleLibraryInternal() =
    queueManager.toggleLibrary()

internal fun MusicService.toggleLikeInternal() =
    queueManager.toggleLike()

internal fun MusicService.toggleStartRadioInternal() =
    queueManager.toggleStartRadio()
