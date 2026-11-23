/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.ui.screens.playlist.detail

import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.playlist.source.PlaylistTrack
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

/**
 * Per-origin key generation. One shape per origin, matching the legacy
 * screens byte-for-byte so a route flip never changes LazyColumn keys:
 *   PlaylistSong (local) -> "${map.id}_$index"     [localPlaylistSongKey]
 *   SongItem (online)    -> setVideoId ?: "id-index" [onlinePlaylistSongKey]
 *   SpotifyTrack         -> "spotify_track_${track.id}_$index"
 *   Song / CachedSong (auto, top, cache) -> "${track.id}_$index"
 */
internal fun playlistDetailTrackKey(
    track: PlaylistTrack,
    index: Int,
): Any =
    when (val origin = track.origin) {
        is PlaylistSong -> "${origin.map.id}_$index"
        is SongItem -> origin.setVideoId ?: "${origin.id}-$index"
        is SpotifyTrack -> "spotify_track_${origin.id}_$index"
        else -> "${track.id}_$index"
    }
