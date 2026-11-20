/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playlist.spotify

import kotlin.math.abs
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.spotify.SpotifyMapper
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

/**
 * Whether the currently playing [mediaMetadata] is this Spotify track.
 *
 * Relocated VERBATIM from the legacy SpotifyPlaylistScreen.kt:420-452 before
 * that screen was deleted in the spotify route flip. A resolved Spotify
 * placeholder plays as a YouTube id, so a raw id comparison misses — the
 * check keys on mediaMetadata.spotifyTrackId when present and falls back to
 * title/duration/album/artist/thumbnail heuristics otherwise. SpotifyAlbumScreen
 * and the unified playlist detail's Spotify rows both resolve through this.
 */
internal fun SpotifyTrack.isResolvedAs(mediaMetadata: MediaMetadata?): Boolean {
    if (mediaMetadata == null) return false

    mediaMetadata.spotifyTrackId?.let { spotifyTrackId ->
        return id.isNotBlank() && spotifyTrackId == id
    }

    val titleMatches = name.equals(mediaMetadata.title, ignoreCase = true)
    val durationMatches =
        durationMs <= 0 ||
            mediaMetadata.duration <= 0 ||
            abs(durationMs.toLong() - mediaMetadata.duration * 1000L) <= 1_000L
    val albumMatches =
        album?.let { spotifyAlbum ->
            val currentAlbum = mediaMetadata.album ?: return false
            spotifyAlbum.id.isNotBlank() && spotifyAlbum.id == currentAlbum.id ||
                spotifyAlbum.name.equals(currentAlbum.title, ignoreCase = true)
        } ?: true
    val artistMatches =
        artists.isEmpty() ||
            mediaMetadata.artists.isEmpty() ||
            artists.any { spotifyArtist ->
                mediaMetadata.artists.any { artist ->
                    spotifyArtist.name.equals(artist.name, ignoreCase = true)
                }
            }
    val thumbnailMatches =
        SpotifyMapper.getTrackThumbnail(this)?.let { thumbnail ->
            thumbnail == mediaMetadata.thumbnailUrl
        } ?: true

    return titleMatches && durationMatches && albumMatches && artistMatches && thumbnailMatches
}
