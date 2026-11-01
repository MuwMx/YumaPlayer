/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.together

import android.os.SystemClock
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.mediaItems
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.host.ServiceConfigCollector
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.together.AddTrackMode
import moe.rukamori.archivetune.together.ControlAction
import moe.rukamori.archivetune.together.TogetherPlaybackSync
import moe.rukamori.archivetune.together.TogetherRole
import moe.rukamori.archivetune.together.TogetherRoomSettings
import moe.rukamori.archivetune.together.TogetherRoomState
import moe.rukamori.archivetune.together.TogetherSessionState
import moe.rukamori.archivetune.together.TogetherTrack
import moe.rukamori.archivetune.utils.md5

internal class ServiceTogetherCoordinator(
    private val configCollector: ServiceConfigCollector,
    private val playerProvider: () -> Player,
    private val delegate: Delegate,
) {
    interface Delegate {
        fun getString(resId: Int): String
        fun showTogetherNotice(message: String, key: String? = null)
        fun playNext(items: List<androidx.media3.common.MediaItem>)
        fun addToQueue(items: List<androidx.media3.common.MediaItem>)
        var suppressAutoPlayback: Boolean
        var currentQueue: Queue
        var queueTitle: String?
        fun getPendingGuestControl(): TogetherPendingGuestControl?
        fun clearPendingGuestControl()
    }

    suspend fun applyHostControl(action: ControlAction) {
        withContext(Dispatchers.Main) {
            val player = playerProvider()
            when (action) {
                ControlAction.Play -> {
                    if (!player.playWhenReady) {
                        player.prepare()
                        player.playWhenReady = true
                    }
                }

                ControlAction.Pause -> {
                    if (player.playWhenReady) {
                        player.playWhenReady = false
                    }
                }

                is ControlAction.SeekTo -> {
                    player.seekTo(action.positionMs.coerceAtLeast(0L))
                    player.prepare()
                }

                ControlAction.SkipNext -> {
                    if (player.hasNextMediaItem()) {
                        player.seekToNext()
                        player.prepare()
                        player.playWhenReady = true
                    }
                }

                ControlAction.SkipPrevious -> {
                    if (player.hasPreviousMediaItem()) {
                        player.seekToPrevious()
                        player.prepare()
                        player.playWhenReady = true
                    }
                }

                is ControlAction.SeekToTrack -> {
                    val trackId = action.trackId.trim()
                    if (trackId.isNotBlank()) {
                        val idx =
                            player.mediaItems.indexOfFirst {
                                val metaId = it.metadata?.id
                                it.mediaId == trackId || metaId == trackId
                            }
                        if (idx >= 0 && idx < player.mediaItemCount) {
                            player.seekTo(idx, action.positionMs.coerceAtLeast(0L))
                            player.prepare()
                        }
                    }
                }

                is ControlAction.SeekToIndex -> {
                    val idx = action.index.coerceAtLeast(0)
                    if (idx < player.mediaItemCount) {
                        player.seekTo(idx, action.positionMs.coerceAtLeast(0L))
                        player.prepare()
                    }
                }

                is ControlAction.SetRepeatMode -> {
                    if (player.repeatMode != action.repeatMode) {
                        player.repeatMode = action.repeatMode
                    }
                }

                is ControlAction.SetShuffleEnabled -> {
                    if (player.shuffleModeEnabled != action.shuffleEnabled) {
                        player.shuffleModeEnabled = action.shuffleEnabled
                    }
                }
            }
        }
    }

    suspend fun applyHostAddTrack(
        track: TogetherTrack,
        mode: AddTrackMode,
    ) {
        val mediaItem = track.toMediaMetadata().toMediaItem()
        withContext(Dispatchers.Main) {
            when (mode) {
                AddTrackMode.PLAY_NEXT -> delegate.playNext(listOf(mediaItem))
                AddTrackMode.ADD_TO_QUEUE -> delegate.addToQueue(listOf(mediaItem))
            }
        }
    }

    suspend fun buildTogetherRoomState(
        sessionId: String,
        hostId: String,
    ): TogetherRoomState =
        withContext(Dispatchers.Main) {
            val player = playerProvider()
            val tracks =
                player.mediaItems.mapNotNull { it.metadata }.map { meta ->
                    TogetherTrack(
                        id = meta.id,
                        title = meta.title,
                        artists = meta.artists.map { it.name },
                        durationSec = meta.duration,
                        thumbnailUrl = meta.thumbnailUrl,
                    )
                }

            val queueHash = md5(tracks.joinToString(separator = "|") { it.id })

            TogetherRoomState(
                sessionId = sessionId,
                hostId = hostId,
                settings = TogetherRoomSettings(),
                participants = emptyList(),
                queue = tracks,
                queueHash = queueHash,
                currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
                isPlaying = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
                positionMs = player.currentPosition.coerceAtLeast(0L),
                repeatMode = player.repeatMode,
                shuffleEnabled = player.shuffleModeEnabled,
                sentAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            )
        }

    suspend fun applyRemoteRoomState(
        state: TogetherRoomState,
        force: Boolean = false,
    ) {
        val pid = configCollector.togetherSelfParticipantId ?: return
        val now = SystemClock.elapsedRealtime()

        val pending = delegate.getPendingGuestControl()
        if (force) {
            delegate.clearPendingGuestControl()
        } else if (pending != null) {
            val currentTrackId = state.queue.getOrNull(state.currentIndex.coerceAtLeast(0))?.id
            val mismatch =
                (pending.desiredIsPlaying != null && state.isPlaying != pending.desiredIsPlaying) ||
                    (pending.desiredIndex != null && state.currentIndex != pending.desiredIndex) ||
                    (pending.desiredTrackId != null && currentTrackId != pending.desiredTrackId)
            if (now >= pending.expiresAtElapsedMs) {
                if ((pending.desiredIndex != null || pending.desiredTrackId != null) &&
                    now - pending.requestedAtElapsedMs >= 1200L &&
                    mismatch
                ) {
                    delegate.showTogetherNotice(delegate.getString(R.string.together_song_change_failed), key = "GUEST_SEEK_TIMEOUT")
                }
                delegate.clearPendingGuestControl()
            } else {
                if (mismatch) return
                delegate.clearPendingGuestControl()
            }
        }

        val sentAt = state.sentAtElapsedRealtimeMs
        if (TogetherPlaybackSync.isStaleRoomState(
                sentAtElapsedRealtimeMs = sentAt,
                lastAppliedSentAtElapsedRealtimeMs = configCollector.togetherLastAppliedRoomStateSentAtElapsedMs,
                force = force,
            )
        ) {
            return
        }

        val targetPos =
            TogetherPlaybackSync.targetPositionMs(
                state = state,
                isOnlineSession = configCollector.togetherIsOnlineSession,
                clockSnapshot = if (configCollector.togetherIsOnlineSession) null else configCollector.togetherClock?.snapshot(),
                nowElapsedRealtimeMs = now,
            )

        withContext(Dispatchers.Main) {
            val player = playerProvider()
            configCollector.togetherApplyingRemote = true
            configCollector.togetherSuppressEchoUntilElapsedMs =
                TogetherPlaybackSync.echoSuppressionUntil(
                    SystemClock.elapsedRealtime(),
                )
            try {
                val desiredItems = state.queue.map { it.toMediaMetadata().toMediaItem() }
                val desiredIds = state.queue.map { it.id }
                val desiredHash = state.queueHash
                val localIds = player.mediaItems.mapNotNull { it.metadata?.id ?: it.mediaId }.filter { it.isNotBlank() }
                val localHash =
                    if (localIds.isEmpty()) {
                        ""
                    } else {
                        md5(localIds.joinToString(separator = "|"))
                    }
                val needsRebuild =
                    TogetherPlaybackSync.needsQueueRebuild(
                        desiredHash = desiredHash,
                        desiredIds = desiredIds,
                        localHash = localHash,
                        localIds = localIds,
                    )

                if (desiredItems.isNotEmpty() && needsRebuild) {
                    configCollector.togetherLastAppliedQueueHash = desiredHash.ifBlank { localHash }
                    val startIndex = state.currentIndex.coerceIn(0, desiredItems.lastIndex)
                    delegate.suppressAutoPlayback = false
                    delegate.currentQueue =
                        ListQueue(
                            title = delegate.getString(R.string.music_player),
                            items = desiredItems,
                            startIndex = startIndex,
                            position = targetPos,
                        )
                    delegate.queueTitle = null
                    player.setMediaItems(desiredItems, startIndex, targetPos)
                    player.prepare()
                    player.repeatMode = state.repeatMode
                    player.shuffleModeEnabled = state.shuffleEnabled
                    player.playWhenReady = state.isPlaying
                    configCollector.togetherLastRemoteAppliedIndex = startIndex
                } else {
                    val index =
                        if (player.mediaItemCount > 0) {
                            state.currentIndex.coerceIn(0, player.mediaItemCount - 1)
                        } else {
                            0
                        }
                    val indexChanged = player.mediaItemCount > 0 && index != player.currentMediaItemIndex

                    if (indexChanged) {
                        if (player.repeatMode != state.repeatMode) player.repeatMode = state.repeatMode
                        if (player.shuffleModeEnabled != state.shuffleEnabled) player.shuffleModeEnabled = state.shuffleEnabled
                        player.seekTo(index, targetPos)
                        player.prepare()
                        player.playWhenReady = state.isPlaying
                    } else {
                        val playbackStateChanged = player.playWhenReady != state.isPlaying
                        if (player.repeatMode != state.repeatMode) player.repeatMode = state.repeatMode
                        if (player.shuffleModeEnabled != state.shuffleEnabled) player.shuffleModeEnabled = state.shuffleEnabled
                        if (playbackStateChanged) player.playWhenReady = state.isPlaying
                        val shouldSeekForDrift =
                            TogetherPlaybackSync.shouldSeekForDrift(
                                currentPositionMs = player.currentPosition,
                                targetPositionMs = targetPos,
                                isPlaying = state.isPlaying,
                                isOnlineSession = configCollector.togetherIsOnlineSession,
                            )
                        if (shouldSeekForDrift || (playbackStateChanged && !state.isPlaying)) {
                            player.seekTo(targetPos)
                            player.prepare()
                        }
                    }
                    configCollector.togetherLastRemoteAppliedIndex = index
                }
                configCollector.togetherLastRemoteAppliedPlayWhenReady = state.isPlaying
                configCollector.togetherLastAppliedRoomStateSentAtElapsedMs = sentAt

                configCollector.togetherSessionState.value =
                    TogetherSessionState.Joined(
                        role = TogetherRole.Guest,
                        sessionId = state.sessionId,
                        selfParticipantId = pid,
                        roomState = state,
                    )
            } finally {
                configCollector.togetherApplyingRemote = false
            }
        }
    }
}
