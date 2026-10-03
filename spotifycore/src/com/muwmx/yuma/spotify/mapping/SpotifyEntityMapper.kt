/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.mapping

import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

object SpotifyEntityMapper {
    fun getPlaylistThumbnail(playlist: SpotifyPlaylist): String? =
        playlist.images.let { images ->
            images.maxByOrNull { it.width ?: 0 }?.url
                ?: images.firstOrNull()?.url
        }

    fun getTrackThumbnail(track: SpotifyTrack): String? =
        track.album?.images?.let { images ->
            images.maxByOrNull { it.width ?: 0 }?.url
                ?: images.firstOrNull()?.url
        }

    fun getTrackThumbnailMedium(track: SpotifyTrack): String? {
        val images = track.album?.images?.takeIf { it.isNotEmpty() } ?: return getTrackThumbnail(track)
        val sorted =
            if (images.any { (it.width ?: 0) > 0 }) {
                images.sortedByDescending { it.width ?: 0 }
            } else {
                images
            }

        return when {
            sorted.size >= 3 -> {
                val middle = sorted.subList(1, sorted.size - 1)
                val chosen =
                    middle.minByOrNull {
                        val w = it.width ?: 300
                        kotlin.math.abs(w - 300)
                    } ?: middle.first()
                chosen.url
            }
            sorted.size == 2 -> {
                val second = sorted[1]
                val secondWidth = second.width ?: 0
                if (secondWidth in 1..120) {
                    sorted[0].url
                } else {
                    second.url
                }
            }
            else -> sorted.firstOrNull()?.url ?: getTrackThumbnail(track)
        }
    }
}
