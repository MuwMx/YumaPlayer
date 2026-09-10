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

object LikeSourceResolver {

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
