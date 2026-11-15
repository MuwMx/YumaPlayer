/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.mapping

import moe.rukamori.archivetune.spotify.models.SpotifyAlbum
import moe.rukamori.archivetune.spotify.models.SpotifyArtist
import moe.rukamori.archivetune.spotify.models.SpotifyImage
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

object SpotifyEntityMapper {
    private fun inferCdnDimension(url: String): Int =
        when {
            url.contains("0000b273") || url.contains("00000003") -> 640
            url.contains("00001e02") -> 300
            url.contains("00004851") -> 64
            else -> 0
        }

    fun getBestImage(images: List<SpotifyImage>): SpotifyImage? {
        val valid = images.filter { it.url.isNotBlank() }
        if (valid.isEmpty()) return null

        return valid.maxByOrNull { image ->
            val explicit = maxOf(image.width ?: 0, image.height ?: 0)
            if (explicit > 0) explicit else inferCdnDimension(image.url)
        }
    }

    fun getPlaylistThumbnail(playlist: SpotifyPlaylist): String? =
        getBestImage(playlist.images)?.url

    fun getTrackThumbnail(track: SpotifyTrack): String? =
        track.album?.images?.let { getBestImage(it)?.url }

    fun getAlbumThumbnail(album: SpotifyAlbum): String? =
        getBestImage(album.images)?.url

    fun getArtistThumbnail(artist: SpotifyArtist): String? =
        getBestImage(artist.images)?.url

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
