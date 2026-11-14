/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.constants.LikeSource
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.spotify.SpotifyLikedSongsQueue
import moe.rukamori.archivetune.spotify.SpotifyPlaylistQueue
import moe.rukamori.archivetune.spotify.SpotifyTracksQueue
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

object LikeSourceResolver {

    fun extractCleanSpotifyId(rawId: String?): String? {
        if (rawId.isNullOrBlank()) return null
        return rawId
            .removePrefix("spotify:track:")
            .removePrefix("spotify:")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    fun isSpotifyId(id: String?, isLocal: Boolean = false): Boolean {
        if (id.isNullOrBlank()) return false
        if (isLocal || id.isLocalMediaId()) return false
        if (id.startsWith("spotify:")) return true
        return id.length == 22 && id.all { it.isLetterOrDigit() }
    }

    fun resolve(
        mediaId: String?,
        spotifyTrackId: String? = null,
        queue: Queue? = null,
        isLocal: Boolean = false,
    ): LikeSource {
        if (!spotifyTrackId.isNullOrBlank()) {
            return LikeSource.SPOTIFY
        }
        if (isSpotifyId(mediaId, isLocal)) {
            return LikeSource.SPOTIFY
        }
        if (queue is SpotifyLikedSongsQueue || queue is SpotifyPlaylistQueue || queue is SpotifyTracksQueue) {
            return LikeSource.SPOTIFY
        }
        return LikeSource.YTM
    }
}

fun Set<String>.isTrackLiked(track: SpotifyTrack): Boolean {
    val cleanId = LikeSourceResolver.extractCleanSpotifyId(track.id)
    if (cleanId != null && cleanId in this) return true
    if (track.id.isNotBlank() && track.id in this) return true
    val isrc = track.externalIds?.isrc?.trim()
    return !isrc.isNullOrBlank() && isrc in this
}
