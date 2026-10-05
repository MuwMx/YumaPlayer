/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback

import androidx.annotation.OptIn
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.extensions.SilentHandler
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.queue.GuestAddTracksGateDecision
import moe.rukamori.archivetune.playback.queue.GuestPlayQueuePlan
import moe.rukamori.archivetune.playback.queue.PlayQueueGateDecision
import moe.rukamori.archivetune.playback.queue.gateAddTracks
import moe.rukamori.archivetune.playback.queue.gatePlayQueue
import moe.rukamori.archivetune.playback.queue.planGuestPlayQueue
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.together.AddTrackMode
import moe.rukamori.archivetune.together.ControlAction
import moe.rukamori.archivetune.together.TogetherGuestOp
import moe.rukamori.archivetune.together.TogetherSessionState
import moe.rukamori.archivetune.together.TogetherTrack
import moe.rukamori.archivetune.utils.get

@OptIn(UnstableApi::class)
internal class TogetherGuestCoordinator(
    private val scope: CoroutineScope,
    private val dataStore: DataStore<Preferences>,
    private val delegate: Delegate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    interface Delegate {
        fun getTogetherSessionState(): Any?
        fun isTogetherApplyingRemote(): Boolean
        fun showTogetherNotice(message: String, key: String)
        fun requestTogetherControl(action: ControlAction)
        fun requestTogetherAddTrack(track: TogetherTrack, mode: AddTrackMode)
        fun getString(resId: Int): String
        fun ensureScopesActive()
    }

    fun gatePlayQueue(queue: Queue, playWhenReady: Boolean): Boolean {
        val joined = delegate.getTogetherSessionState() as? TogetherSessionState.Joined
        val decision = gatePlayQueue(
            role = joined?.role,
            allowGuestsToControlPlayback = joined?.roomState?.settings?.allowGuestsToControlPlayback ?: false,
            isApplyingRemote = delegate.isTogetherApplyingRemote(),
        )
        when (decision) {
            is PlayQueueGateDecision.Denied -> {
                delegate.showTogetherNotice(delegate.getString(R.string.not_allowed), key = decision.noticeKey)
                return true
            }
            PlayQueueGateDecision.HandleAsGuest -> {
                if (joined == null) return true
                delegate.ensureScopesActive()
                scope.launch(SilentHandler) {
                    val initialStatus = withContext(ioDispatcher) {
                        queue
                            .getInitialStatus()
                            .filterExplicit(dataStore.get(HideExplicitKey, false))
                            .filterVideo(dataStore.get(HideVideoKey, false))
                    }

                    val targetItem = initialStatus.items.getOrNull(initialStatus.mediaItemIndex)
                        ?: queue.preloadItem?.toMediaItem()

                    val plan = planGuestPlayQueue(
                        roomState = joined.roomState,
                        targetItem = targetItem,
                        positionMs = initialStatus.position,
                        playWhenReady = playWhenReady,
                    )
                    when (plan) {
                        is GuestPlayQueuePlan.Blocked -> {
                            delegate.showTogetherNotice(delegate.getString(R.string.not_allowed), key = plan.noticeKey)
                        }
                        is GuestPlayQueuePlan.Execute -> {
                            delegate.showTogetherNotice(
                                delegate.getString(R.string.together_requesting_song_change),
                                key = plan.noticeKey,
                            )
                            plan.ops.forEach { op ->
                                when (op) {
                                    is TogetherGuestOp.Control -> delegate.requestTogetherControl(op.action)
                                    is TogetherGuestOp.AddTrack -> delegate.requestTogetherAddTrack(op.track, op.mode)
                                }
                            }
                        }
                    }
                }
                return true
            }
            PlayQueueGateDecision.PassThrough -> return false
        }
    }

    fun gatePlayNext(items: List<MediaItem>): Boolean =
        gateAddTracksInternal(items, AddTrackMode.PLAY_NEXT)

    fun gateAddToQueue(items: List<MediaItem>): Boolean =
        gateAddTracksInternal(items, AddTrackMode.ADD_TO_QUEUE)

    private fun gateAddTracksInternal(items: List<MediaItem>, mode: AddTrackMode): Boolean {
        val joined = delegate.getTogetherSessionState() as? TogetherSessionState.Joined
        val decision = gateAddTracks(
            role = joined?.role,
            allowGuestAdd = joined?.roomState?.settings?.allowGuestsToAddTracks ?: false,
            items = items,
        )
        when (decision) {
            is GuestAddTracksGateDecision.Allowed -> {
                val ordered = if (mode == AddTrackMode.PLAY_NEXT) decision.tracks.asReversed() else decision.tracks
                ordered.forEach { track ->
                    delegate.requestTogetherAddTrack(track, mode)
                }
                return true
            }
            GuestAddTracksGateDecision.Denied -> return true
            GuestAddTracksGateDecision.PassThrough -> return false
        }
    }
}
