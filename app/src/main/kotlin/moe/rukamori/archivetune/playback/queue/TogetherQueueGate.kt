/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */
package moe.rukamori.archivetune.playback.queue

import androidx.media3.common.MediaItem
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.together.TogetherGuestOp
import moe.rukamori.archivetune.together.TogetherGuestPlaybackPlanner
import moe.rukamori.archivetune.together.TogetherRole
import moe.rukamori.archivetune.together.TogetherRoomState
import moe.rukamori.archivetune.together.TogetherTrack

internal sealed interface PlayQueueGateDecision {
    data object PassThrough : PlayQueueGateDecision
    data class Denied(val noticeKey: String) : PlayQueueGateDecision
    data object HandleAsGuest : PlayQueueGateDecision
}

internal sealed interface GuestPlayQueuePlan {
    data class Blocked(val noticeKey: String) : GuestPlayQueuePlan
    data class Execute(val ops: List<TogetherGuestOp>, val noticeKey: String) : GuestPlayQueuePlan
}

internal sealed interface GuestRadioGateDecision {
    data object PassThrough : GuestRadioGateDecision
    data class Denied(val noticeKey: String) : GuestRadioGateDecision
}

internal sealed interface GuestAddTracksGateDecision {
    data object PassThrough : GuestAddTracksGateDecision
    data object Denied : GuestAddTracksGateDecision
    data class Allowed(val tracks: List<TogetherTrack>) : GuestAddTracksGateDecision
}

internal fun isGuestPlayback(
    role: TogetherRole?,
    isApplyingRemote: Boolean = false,
): Boolean = !isApplyingRemote && role is TogetherRole.Guest

internal fun buildTogetherTrack(item: MediaItem?): TogetherTrack? {
    val meta = item?.metadata
    val trackId = meta?.id?.trim().orEmpty().ifBlank {
        item?.mediaId?.trim().orEmpty()
    }
    if (trackId.isBlank()) return null
    return TogetherTrack(
        id = trackId,
        title = meta?.title ?: trackId,
        artists = meta?.artists?.map { it.name }.orEmpty(),
        durationSec = meta?.duration ?: -1,
        thumbnailUrl = meta?.thumbnailUrl,
    )
}

internal fun buildTogetherTracks(items: List<MediaItem>): List<TogetherTrack> =
    items.mapNotNull { it.metadata }.map { meta ->
        TogetherTrack(
            id = meta.id,
            title = meta.title,
            artists = meta.artists.map { it.name },
            durationSec = meta.duration,
            thumbnailUrl = meta.thumbnailUrl,
        )
    }

internal fun planPlayTrackNow(
    roomState: TogetherRoomState,
    track: TogetherTrack,
    positionMs: Long,
    playWhenReady: Boolean,
): List<TogetherGuestOp> =
    TogetherGuestPlaybackPlanner.planPlayTrackNow(
        roomState = roomState,
        track = track,
        positionMs = positionMs,
        playWhenReady = playWhenReady,
    )

internal fun gatePlayQueue(
    role: TogetherRole?,
    allowGuestsToControlPlayback: Boolean,
    isApplyingRemote: Boolean = false,
): PlayQueueGateDecision {
    if (!isGuestPlayback(role, isApplyingRemote)) return PlayQueueGateDecision.PassThrough
    if (!allowGuestsToControlPlayback) return PlayQueueGateDecision.Denied("GUEST_PLAYQUEUE_DISABLED")
    return PlayQueueGateDecision.HandleAsGuest
}

internal fun planGuestPlayQueue(
    roomState: TogetherRoomState,
    targetItem: MediaItem?,
    positionMs: Long,
    playWhenReady: Boolean,
): GuestPlayQueuePlan {
    val track = buildTogetherTrack(targetItem)
        ?: return GuestPlayQueuePlan.Blocked("GUEST_PLAYQUEUE_NO_TRACK")
    val ops = planPlayTrackNow(
        roomState = roomState,
        track = track,
        positionMs = positionMs,
        playWhenReady = playWhenReady,
    )
    if (ops.isEmpty()) {
        return GuestPlayQueuePlan.Blocked("GUEST_PLAYQUEUE_BLOCKED")
    }
    return GuestPlayQueuePlan.Execute(ops, noticeKey = "GUEST_PLAYQUEUE_REQUEST")
}

internal fun gateStartRadio(
    role: TogetherRole?,
    allowGuestsToControlPlayback: Boolean,
    isApplyingRemote: Boolean = false,
): GuestRadioGateDecision {
    if (!isGuestPlayback(role, isApplyingRemote)) return GuestRadioGateDecision.PassThrough
    val noticeKey = if (!allowGuestsToControlPlayback) "GUEST_RADIO_DISABLED" else "GUEST_RADIO_UNSUPPORTED"
    return GuestRadioGateDecision.Denied(noticeKey)
}

internal fun gateAddTracks(
    role: TogetherRole?,
    allowGuestAdd: Boolean,
    items: List<MediaItem>,
): GuestAddTracksGateDecision {
    if (role !is TogetherRole.Guest) return GuestAddTracksGateDecision.PassThrough
    if (!allowGuestAdd) return GuestAddTracksGateDecision.Denied
    return GuestAddTracksGateDecision.Allowed(buildTogetherTracks(items))
}
