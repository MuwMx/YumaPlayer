/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.spotify.models

import kotlinx.serialization.Serializable

@Serializable
data class SpotifySearchResult(
    val tracks: SpotifyPaging<SpotifyTrack>? = null,
    val playlists: SpotifyPaging<SpotifyPlaylist>? = null,
    val albums: SpotifyPaging<SpotifyAlbum>? = null,
    val artists: SpotifyPaging<SpotifyArtist>? = null,
)
